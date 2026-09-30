package com.rmediatech.janus.bridge

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.rmediatech.janus.MainActivity
import com.rmediatech.janus.clipboard.ClipboardItem
import com.rmediatech.janus.clipboard.JanusClipboardBridge
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
    val sessionSecret: String = ""
)

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

        private val _bridgeState = MutableStateFlow(BridgeState())
        val bridgeState = _bridgeState.asStateFlow()

        val globalAuthToken = JanusAuthToken()

        fun startService(context: Context) {
            val intent = Intent(context, JanusBridgeService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, JanusBridgeService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
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
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startBridge()
            ACTION_STOP -> stopBridge()
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startBridge() {
        if (server != null) return

        val port = 8080
        val ip = getDeviceIp()

        // 1. Acquire Wi-Fi locks to prevent packet drops and sleep disconnects
        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        wifiLock = wifiManager?.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "Janus:WifiLock")?.apply {
            setReferenceCounted(false)
            acquire()
        }
        multicastLock = wifiManager?.createMulticastLock("Janus:MulticastLock")?.apply {
            setReferenceCounted(false)
            acquire()
        }

        // 2. Start HTTP & WebSocket Server
        val httpServer = JanusHttpServer(
            context = applicationContext,
            port = port,
            authToken = globalAuthToken
        )
        try {
            httpServer.start()
            server = httpServer
        } catch (e: Exception) {
            stopBridge()
            return
        }

        // 3. Register mDNS service for zero-conf auto discovery
        registerNsd(port)

        // 4. Hook bi-directional system clipboard sync
        val mainHandler = Handler(Looper.getMainLooper())
        clipManager = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager

        // Listen for phone copies anywhere on device -> push to Janus bridge
        clipListener = ClipboardManager.OnPrimaryClipChangedListener {
            try {
                val clip = clipManager?.primaryClip
                if (clip != null && clip.itemCount > 0) {
                    val text = clip.getItemAt(0).coerceToText(applicationContext).toString()
                    if (text.isNotBlank()) {
                        JanusClipboardBridge.push(text, "phone")
                    }
                }
            } catch (_: Exception) {}
        }
        mainHandler.post {
            clipListener?.let { clipManager?.addPrimaryClipChangedListener(it) }
        }

        // Listen for desktop copies -> set in Android system clipboard
        bridgeClipListener = { item ->
            if (item.source == "desktop") {
                mainHandler.post {
                    try {
                        clipManager?.setPrimaryClip(ClipData.newPlainText("Janus Desktop", item.text))
                    } catch (_: Exception) {}
                }
            }
        }
        bridgeClipListener?.let { JanusClipboardBridge.addListener(it) }

        // 5. Update observable state
        _bridgeState.value = BridgeState(
            isRunning = true,
            localIp = ip,
            port = port,
            pairingPin = globalAuthToken.pairingPin,
            sessionSecret = globalAuthToken.sessionSecret
        )

        // 6. Build Foreground Notification
        val notification = buildNotification(ip, port, globalAuthToken.pairingPin)
        startForeground(NOTIFICATION_ID, notification)
    }

    private fun stopBridge() {
        try {
            server?.stop()
        } catch (_: Exception) {}
        server = null

        try {
            if (clipListener != null) {
                clipManager?.removePrimaryClipChangedListener(clipListener)
                clipListener = null
            }
            if (bridgeClipListener != null) {
                JanusClipboardBridge.removeListener(bridgeClipListener!!)
                bridgeClipListener = null
            }
        } catch (_: Exception) {}

        try {
            if (nsdRegistrationListener != null) {
                nsdManager?.unregisterService(nsdRegistrationListener)
                nsdRegistrationListener = null
            }
        } catch (_: Exception) {}

        try {
            if (wifiLock?.isHeld == true) wifiLock?.release()
            if (multicastLock?.isHeld == true) multicastLock?.release()
        } catch (_: Exception) {}

        _bridgeState.value = BridgeState(isRunning = false)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun registerNsd(port: Int) {
        nsdManager = getSystemService(Context.NSD_SERVICE) as? NsdManager ?: return
        val serviceInfo = NsdServiceInfo().apply {
            serviceName = "Janus Bridge"
            serviceType = "_janus._tcp."
            setPort(port)
        }

        nsdRegistrationListener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(serviceInfo: NsdServiceInfo?) {}
            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {}
            override fun onServiceUnregistered(serviceInfo: NsdServiceInfo?) {}
            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {}
        }

        try {
            nsdManager?.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, nsdRegistrationListener)
        } catch (_: Exception) {}
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Janus Sovereign Bridge",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Active wireless desktop connection & file bridge"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(ip: String, port: Int, pin: String): Notification {
        val tapIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, JanusBridgeService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_upload_done)
            .setContentTitle("Janus Bridge Active: http://$ip:$port")
            .setContentText("Pairing PIN: $pin • Drag & Drop Ready")
            .setContentIntent(tapIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop Bridge", stopIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onDestroy() {
        stopBridge()
        super.onDestroy()
    }
}
