package com.rollinkxx.velum

import android.app.NotificationManager
import android.content.Context

/**
 * Pembersih notifikasi status lama milik aplikasi.
 *
 * Fork WireGuard sudah menyediakan satu-satunya notifikasi foreground yang wajib untuk
 * lifecycle VpnService. Velum tidak boleh membuat notifikasi status kedua karena Android
 * menampilkannya sebagai dua notifikasi VPN yang sama-sama aktif. ID ini dipertahankan hanya
 * untuk membersihkan notifikasi yang mungkin tertinggal dari versi aplikasi sebelumnya.
 */
object StatusNotifier {
    private const val NOTIF_ID = 42

    fun hide(context: Context) {
        context.getSystemService(NotificationManager::class.java)?.cancel(NOTIF_ID)
    }
}
