package com.rmediatech.janus.bridge

import android.app.NotificationManager
import android.app.Service
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.nsd.NsdManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import com.rmediatech.janus.clipboard.ClipboardItem
import com.rmediatech.janus.clipboard.JanusClipboardBridge
import com.rmediatech.janus.logging.SovereignLogger
import com.rmediatech.janus.security.JanusAuthToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.Inet4Address
import java.net.NetworkInterface

data class BridgeState(
    val isRunning: Boolean = false,
    val localIp: String = "127.0.0.1",
    val port: Int = 8080,
    val pairingPin: String = "",
    val sessionSecret: String = "",
    val connectedClients: Int = 0,
    val lastActivityTime: Long = 0L,
    val lastActivityEvent: String = ""
)

/**
 * Sovereign Janus Bridge Foreground Service:
 * One Job: Manages bridge lifecycle, Wi-Fi locks, system clipboard hooks,
 * and 3D temporal wake validation (`SOVEREIGN-LIFECYCLE-VIABILITY-01`).
 */
class JanusBridgeService : Service() {

    private var server: JanusHttpServer? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var multicastLock: WifiManager.MulticastLock? = null
    private var nsdManager: NsdManager? = null
    private var nsdRegistrationListener: NsdManager.RegistrationListener? = null
    private var clipManager: ClipboardManager? = null
    private var clipListener: ClipboardManager.OnPrimaryClipChangedListener? = null
    private var bridgeClipListener: ((ClipboardItem) -> Unit)? = null

    companion object {
        const val CHANNEL_ID = "janus_bridge_channel"
        const val NOTIFICATION_ID = 2026
        const val ACTION_START = "com.rmediatech.janus.ACTION_START"
        const val ACTION_STOP = "com.rmediatech.janus.ACTION_STOP"
        const val ACTION_TEMPORAL_RESUME = "com.rmediatech.janus.ACTION_TEMPORAL_RESUME"

        private val _bridgeState = MutableStateFlow(BridgeState())
        val bridgeState = _bridgeState.asStateFlow()
        val globalAuthToken = JanusAuthToken()

        fun startService(context: Context) {
            val intent = Intent(context, JanusBridgeService::class.java).apply { action = ACTION_START }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent)
            else context.startService(intent)
        }

        fun stopService(context: Context) {
            context.startService(Intent(context, JanusBridgeService::class.java).apply { action = ACTION_STOP })
        }

        fun onTemporalResume(context: Context) {
            SovereignLogger.i(SovereignLogger.Category.LIFECYCLE, "onTemporalResume() — verifying bridge & socket liveness")
            if (_bridgeState.value.isRunning) {
                context.startService(Intent(context, JanusBridgeService::class.java).apply { action = ACTION_TEMPORAL_RESUME })
            }
        }

        fun getDeviceIp(): String {
            try {
                val interfaces = NetworkInterface.getNetworkInterfaces()
                while (interfaces.hasMoreElements()) {
                    val iface = interfaces.nextElement()
                    if (iface.isLoopback || !iface.isUp) continue
                    val addresses = iface.inetAddresses
                    while (addresses.hasMoreElements()) {
                        val addr = addresses.nextElement()
                        if (addr is Inet4Address && !addr.isLoopbackAddress) {
                            return addr.hostAddress ?: "127.0.0.1"
                        }
                    }
                }
            } catch (_: Exception) {}
            return "127.0.0.1"
        }
    }

    override fun onCreate() {
        super.onCreate()
        SovereignLogger.init(applicationContext)
        globalAuthToken.initPersistence(applicationContext)
        JanusServiceHelper.createNotificationChannel(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startBridge()
            ACTION_STOP -> stopBridge()
            ACTION_TEMPORAL_RESUME -> verifyTemporalLiveness()
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun verifyTemporalLiveness() {
        val currentServer = server
        if (!_bridgeState.value.isRunning || currentServer == null) return

        val freshIp = getDeviceIp()
        val oldIp = _bridgeState.value.localIp
        if (freshIp != oldIp && freshIp != "127.0.0.1") {
            SovereignLogger.i(SovereignLogger.Category.NETWORK, "Local LAN IP drifted across sleep boundary: $oldIp -> $freshIp")
            _bridgeState.value = _bridgeState.value.copy(localIp = freshIp)
        }

        Thread {
            val liveClients = currentServer.pruneAndProbeConnections()
            if (liveClients != _bridgeState.value.connectedClients) {
                _bridgeState.value = _bridgeState.value.copy(connectedClients = liveClients)
            }
            updateNotification(_bridgeState.value.localIp, _bridgeState.value.port, globalAuthToken.pairingPin, liveClients)
        }.start()

        if (!currentServer.isAlive) {
            SovereignLogger.w(SovereignLogger.Category.LIFECYCLE, "Server socket dead after wake; self-healing bridge")
            try { currentServer.stop() } catch (_: Exception) {}
            server = null
            startBridge()
        }
    }

    private fun startBridge() {
        if (server != null) return
        val port = 8080
        val ip = getDeviceIp()
        globalAuthToken.initPersistence(applicationContext)

        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        wifiLock = wifiManager?.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "Janus:WifiLock")?.apply {
            setReferenceCounted(false); acquire()
        }
        multicastLock = wifiManager?.createMulticastLock("Janus:MulticastLock")?.apply {
            setReferenceCounted(false); acquire()
        }

        val httpServer = JanusHttpServer(applicationContext, port, globalAuthToken).apply {
            onClientCountChanged = { count ->
                _bridgeState.value = _bridgeState.value.copy(connectedClients = count, lastActivityTime = System.currentTimeMillis())
                updateNotification(_bridgeState.value.localIp, port, globalAuthToken.pairingPin, count)
                SovereignLogger.i(SovereignLogger.Category.BRIDGE, "Active client count changed: $count")
            }
            onActivityEvent = { event ->
                _bridgeState.value = _bridgeState.value.copy(lastActivityTime = System.currentTimeMillis(), lastActivityEvent = event)
                SovereignLogger.d(SovereignLogger.Category.BRIDGE, "Activity event: $event")
            }
        }
        try {
            httpServer.start(0, false)
            server = httpServer
            SovereignLogger.i(SovereignLogger.Category.BRIDGE, "JanusHttpServer listening on http://$ip:$port (PIN=${globalAuthToken.pairingPin})")
        } catch (e: Exception) {
            SovereignLogger.e(SovereignLogger.Category.BRIDGE, "Failed starting JanusHttpServer on :$port", e)
            stopBridge()
            return
        }

        val (nsd, listener) = JanusServiceHelper.registerNsd(this, port)
        nsdManager = nsd
        nsdRegistrationListener = listener
        setupClipboardHooks()

        _bridgeState.value = BridgeState(
            isRunning = true,
            localIp = ip,
            port = port,
            pairingPin = globalAuthToken.pairingPin,
            sessionSecret = globalAuthToken.sessionSecret,
            connectedClients = 0,
            lastActivityTime = System.currentTimeMillis(),
            lastActivityEvent = "STARTED"
        )
        startForeground(NOTIFICATION_ID, JanusServiceHelper.buildNotification(this, ip, port, globalAuthToken.pairingPin, 0))
    }

    private fun setupClipboardHooks() {
        val mainHandler = Handler(Looper.getMainLooper())
        clipManager = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager

        clipListener = ClipboardManager.OnPrimaryClipChangedListener {
            try {
                val clip = clipManager?.primaryClip
                if (clip != null && clip.itemCount > 0) {
                    val text = clip.getItemAt(0).coerceToText(applicationContext).toString()
                    if (text.isNotBlank()) {
                        JanusClipboardBridge.push(text, "phone")
                        _bridgeState.value = _bridgeState.value.copy(lastActivityTime = System.currentTimeMillis(), lastActivityEvent = "PHONE_COPY")
                    }
                }
            } catch (_: Exception) {}
        }
        mainHandler.post { clipListener?.let { clipManager?.addPrimaryClipChangedListener(it) } }

        bridgeClipListener = { item ->
            if (item.source == "desktop") {
                _bridgeState.value = _bridgeState.value.copy(lastActivityTime = System.currentTimeMillis(), lastActivityEvent = "DESKTOP_SYNC")
                mainHandler.post {
                    try { clipManager?.setPrimaryClip(ClipData.newPlainText("Janus Desktop", item.text)) } catch (_: Exception) {}
                }
            }
        }
        bridgeClipListener?.let { JanusClipboardBridge.addListener(it) }
    }

    private fun stopBridge() {
        SovereignLogger.i(SovereignLogger.Category.BRIDGE, "Stopping JanusBridgeService")
        try { server?.stop() } catch (_: Exception) {}
        server = null
        try {
            clipListener?.let { clipManager?.removePrimaryClipChangedListener(it); clipListener = null }
            bridgeClipListener?.let { JanusClipboardBridge.removeListener(it); bridgeClipListener = null }
        } catch (_: Exception) {}
        try { nsdRegistrationListener?.let { nsdManager?.unregisterService(it); nsdRegistrationListener = null } } catch (_: Exception) {}
        try {
            if (wifiLock?.isHeld == true) wifiLock?.release()
            if (multicastLock?.isHeld == true) multicastLock?.release()
        } catch (_: Exception) {}

        _bridgeState.value = BridgeState(isRunning = false)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun updateNotification(ip: String, port: Int, pin: String, clients: Int) {
        if (!_bridgeState.value.isRunning) return
        val manager = getSystemService(NotificationManager::class.java)
        manager?.notify(NOTIFICATION_ID, JanusServiceHelper.buildNotification(this, ip, port, pin, clients))
    }

    override fun onDestroy() {
        stopBridge()
        super.onDestroy()
    }
}
