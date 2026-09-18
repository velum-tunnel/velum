package com.rollinkxx.velum

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat

/**
 * Foreground companion untuk menaikkan process importance selama koneksi diminta pengguna.
 * TUN tetap dimiliki GoBackend.VpnService; service ini tidak membuat interface VPN kedua.
 */
class VelumForegroundService : Service() {
    override fun onCreate() {
        super.onCreate()
        val notification = StatusNotifier.notification(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                StatusNotifier.NOTIF_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED
            )
        } else {
            @Suppress("DEPRECATION")
            startForeground(StatusNotifier.NOTIF_ID, notification)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int =
        START_STICKY

    override fun onDestroy() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        fun start(context: Context) {
            VelumLinkHealthStore.update(VelumLinkHealth.DEGRADED)
            val intent = Intent(context, VelumForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(context, intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, VelumForegroundService::class.java))
        }
    }
}
