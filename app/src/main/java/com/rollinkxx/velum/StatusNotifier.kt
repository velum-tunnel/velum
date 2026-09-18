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
 * Library WireGuard tetap tidak memanggil `startForeground`; VelumForegroundService
 * app-owned memakai notification ID/channel yang sama agar tidak membuat notification
 * ganda. Notification diperbarui dengan kesehatan link, bukan hanya state TUN.
 *
 * Tanpa dependensi: memakai Notification framework bawaan. Izin POST_NOTIFICATIONS
 * (Android 13+) diminta dari MainActivity; bila pengguna menolak, notify() di-skip aman.
 */
object StatusNotifier {
    internal const val CHANNEL_ID = "status"
    internal const val NOTIF_ID = 42

    fun show(context: Context) {
        val mgr = context.getSystemService(NotificationManager::class.java) ?: return
        try {
            mgr.notify(NOTIF_ID, notification(context))
        } catch (_: SecurityException) {
            // Izin notifikasi ditolak (Android 13+) — status bar saja yang hilang.
        }
    }

    fun notification(context: Context): Notification {
        if (Build.VERSION.SDK_INT >= 26) {
            context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(
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
            .setContentText(
                when (VelumLinkHealthStore.current) {
                    VelumLinkHealth.CONNECTED ->
                        ConnectedSubtitle.forSession(context, VelumTunnel.upSinceElapsedMs)
                    VelumLinkHealth.DEGRADED -> context.getString(R.string.notif_degraded)
                    VelumLinkHealth.OFFLINE -> context.getString(R.string.notif_offline)
                }
            )
            .setContentIntent(tap)
            .setOngoing(true)
            .setOnlyAlertOnce(true) // pembaruan teks tidak perlu mengganggu lagi
            .build()
        return notif
    }

    fun hide(context: Context) {
        try {
            context.getSystemService(NotificationManager::class.java)?.cancel(NOTIF_ID)
        } catch (_: SecurityException) {
            // Izin notifikasi ditolak (Android 13+) — tidak ada notification untuk dihapus.
        }
    }
}
