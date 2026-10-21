package com.rmediatech.janus.bridge

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import com.rmediatech.janus.MainActivity
import com.rmediatech.janus.logging.SovereignLogger

/**
 * Sovereign Notification & mDNS/NSD Discovery Helper for JanusBridgeService:
 * One Job: Manages foreground service notification rendering and zero-conf _janus._tcp mDNS registration.
 */
object JanusServiceHelper {

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                JanusBridgeService.CHANNEL_ID,
                "Janus Sovereign Bridge",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Active wireless desktop connection & file bridge"
                setShowBadge(false)
            }
            val manager = context.getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    fun buildNotification(
        context: Context,
        ip: String,
        port: Int,
        pin: String,
        clients: Int = 0
    ): Notification {
        val tapIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = PendingIntent.getService(
            context,
            1,
            Intent(context, JanusBridgeService::class.java).apply {
                action = JanusBridgeService.ACTION_STOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (clients > 0) {
            "Janus: $clients Desktop${if (clients > 1) "s" else ""} Linked"
        } else {
            "Janus Bridge Active: http://$ip:$port"
        }

        val text = if (clients > 0) {
            "http://$ip:$port • Live Bi-Directional Sync Active"
        } else {
            "Pairing PIN: $pin • Drag & Drop Ready"
        }

        return NotificationCompat.Builder(context, JanusBridgeService.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_upload_done)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(tapIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop Bridge", stopIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    fun registerNsd(context: Context, port: Int): Pair<NsdManager?, NsdManager.RegistrationListener?> {
        val nsdManager = context.getSystemService(Context.NSD_SERVICE) as? NsdManager ?: return null to null
        val serviceInfo = NsdServiceInfo().apply {
            serviceName = "Janus Bridge"
            serviceType = "_janus._tcp."
            setPort(port)
        }

        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo?) {
                SovereignLogger.i(SovereignLogger.Category.DISCOVERY, "mDNS registered: ${info?.serviceName} on port $port")
            }
            override fun onRegistrationFailed(info: NsdServiceInfo?, errorCode: Int) {
                SovereignLogger.w(SovereignLogger.Category.DISCOVERY, "mDNS registration failed (code=$errorCode)")
            }
            override fun onServiceUnregistered(info: NsdServiceInfo?) {
                SovereignLogger.i(SovereignLogger.Category.DISCOVERY, "mDNS service unregistered")
            }
            override fun onUnregistrationFailed(info: NsdServiceInfo?, errorCode: Int) {}
        }

        try {
            nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, listener)
        } catch (e: Exception) {
            SovereignLogger.w(SovereignLogger.Category.DISCOVERY, "mDNS registerService exception", e)
        }
        return nsdManager to listener
    }
}
