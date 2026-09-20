package com.rollinkxx.velum

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * Notifikasi persisten status koneksi pada kanal aplikasi sendiri.
 *
 * Notifikasi ini merupakan ringkasan status aplikasi yang dapat diketuk untuk membuka UI.
 * Ia sengaja terpisah dari notifikasi foreground-service lifecycle milik fork WireGuard:
 * service wajib memiliki notifikasi non-dismissible sendiri, sedangkan notifikasi ini
 * memberi detail durasi/endpoint dan aman dihilangkan bila izin notifikasi ditolak.
 *
 * Tanpa dependensi: memakai Notification framework bawaan. Izin POST_NOTIFICATIONS
 * (Android 13+) diminta dari MainActivity; bila pengguna menolak, notify() di-skip aman.
 */
object StatusNotifier {
    private const val CHANNEL_ID = "status"
    private const val NOTIF_ID = 42

    fun show(context: Context) {
        val mgr = context.getSystemService(NotificationManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= 26) {
            mgr.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.notif_channel),
                    NotificationManager.IMPORTANCE_LOW
                )
            )
        }
        val tap = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = if (Build.VERSION.SDK_INT >= 26) {
            Notification.Builder(context, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(context)
        }
        val notif = builder
            .setSmallIcon(R.drawable.ic_launcher_tile)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(ConnectedSubtitle.forSession(context, VelumTunnel.upSinceElapsedMs))
            .setContentIntent(tap)
            .setOngoing(true)
            .setOnlyAlertOnce(true) // pembaruan teks tidak perlu mengganggu lagi
            .build()
        try {
            mgr.notify(NOTIF_ID, notif)
        } catch (_: SecurityException) {
            // Izin notifikasi ditolak (Android 13+) — status bar saja yang hilang.
        }
    }

    fun hide(context: Context) {
        context.getSystemService(NotificationManager::class.java)?.cancel(NOTIF_ID)
    }
}
