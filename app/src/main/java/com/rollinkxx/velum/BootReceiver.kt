package com.rollinkxx.velum

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.SystemClock

/**
 * Menyambung ulang tunnel tanpa campur tangan pengguna pada dua peristiwa:
 *
 * 1. **`BOOT_COMPLETED`** — perangkat baru menyala.
 * 2. **`MY_PACKAGE_REPLACED`** — aplikasi ini baru diperbarui. Pembaruan mematikan proses,
 *    dan proses yang mati berarti tunnel ikut mati; tanpa peristiwa kedua ini tunnel tetap
 *    mati padahal [Prefs.wasUp] masih `true`, sampai jaringan kebetulan berganti atau
 *    pengguna membuka aplikasi. Untuk aplikasi yang dimaksudkan selalu aktif, diam-diam
 *    mati setiap kali diperbarui adalah kegagalan yang terlihat jelas oleh pengguna.
 *
 * Keduanya hanya dijalankan bila: terakhir tunnel memang UP ([Prefs.wasUp]), perangkat
 * sudah terdaftar, dan persetujuan VPN dari pengguna masih berlaku
 * (`VpnService.prepare == null`). Bila persetujuan hilang, tidak dilakukan apa pun —
 * pengguna menyambung manual dari aplikasi.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            return
        }
        // Tanpa keystore, tidak ada registrasi yang sah untuk dipulihkan dan tidak ada
        // tempat untuk merekam diagnostik: berhenti diam, jangan bertindak apa pun.
        val prefs = try {
            Prefs.of(context)
        } catch (e: KeystoreUnavailableException) {
            VelumLog.w(TAG, "$action: sambung ulang dibatalkan: penyimpanan aman tidak tersedia", e)
            return
        }
        if (!prefs.isRegistered || !prefs.wasUp) return
        if (VpnService.prepare(context) != null) {
            VelumLog.w(TAG, "$action: persetujuan VPN tidak ada, sambung ulang dibatalkan")
            // Dicatat juga ke diagnostik: "kenapa tunnel tidak menyambung sendiri setelah
            // boot" harus bisa dijawab dari layar, bukan hanya dari logcat yang tidak
            // terbaca tanpa adb. Tidak ada durasi yang diukur karena tidak ada percobaan.
            // `writeBootRecord` (apply), BUKAN yang durabel (commit): jalur ini berjalan di
            // main thread dan tidak punya pekerjaan latar, jadi menulis sinkron ke disk di
            // sini tidak sepadan untuk satu baris diagnostik.
            prefs.writeBootRecord(
                VelumDiagnostics.encodeBoot(
                    VelumDiagnostics.Boot(
                        VelumDiagnostics.BOOT_NO_VPN, 0L, System.currentTimeMillis()
                    )
                )
            )
            return
        }
        val pending = goAsync()
        // Diukur untuk diagnostik: inilah angka yang menjawab apakah `up()` melewati
        // anggaran receiver (~10 detik), dan maintainer membacanya dari layar diagnostik
        // karena tidak punya adb. `elapsedRealtime` dipakai (bukan `uptimeMillis`) supaya
        // jeda deep sleep selama percobaan ikut terhitung — itulah waktu yang dialami sistem.
        val mulaiElapsed = SystemClock.elapsedRealtime()
        val mulaiEpoch = System.currentTimeMillis()
        // RISIKO YANG DIKETAHUI DAN SENGAJA DIPERTAHANKAN (butuh perangkat untuk diputuskan):
        // `up()` di bawah bisa memakan 2 detik (GoBackend menunggu VpnService) ditambah
        // hingga 10 x 1 detik retry resolusi DNS (`DNS_RESOLUTION_RETRIES = 10` pada
        // GoBackend.java:43) bila endpoint berupa nama domain dan DNS belum siap — kondisi
        // khas saat boot. Totalnya bisa melewati anggaran receiver.
        //
        // Alternatif yang tampak lebih bersih — serahkan ke ReconnectMonitor lalu selesai
        // tanpa menunggu — TIDAK diambil, karena `goAsync()` juga menahan proses tetap
        // hidup selama pekerjaan berlangsung. Tanpa itu, proses yang baru lahir untuk
        // broadcast ini bisa dibunuh sebelum tunnel naik, dan kegagalannya sama senyapnya.
        // Jadi pilihannya bukan "aman vs berisiko", melainkan dua risiko berbeda:
        // melebihi anggaran receiver, atau kehilangan proses di tengah penyambungan.
        // Memutuskannya butuh pengukuran di perangkat, bukan penalaran dari sandbox — dan
        // sejak 2026-09-13 pengukurannya TIDAK lagi butuh adb: durasi percobaan dan
        // hasilnya direkam ke `Prefs.bootRecord` dan ditampilkan di baris "Boot" layar
        // diagnostik (uji F2 di docs/uji-perangkat.md).
        // Niat pengguna dicatat SEBELUM pekerjaan dimulai, sama seperti pelaku lain
        // (layar utama lewat `VelumController.nextIntent()`, ubin lewat `VelumTileService`).
        // Tanpa ini percobaan boot bisa menghidupkan tunnel yang baru saja diminta mati:
        // `up()` di sini dan `down()` dari layar sama-sama `@Synchronized`, sehingga tanpa
        // penanda urutan keduanya sekadar berlomba memperoleh kunci — dan `down()` bisa
        // kalah dari `up()` yang sudah telanjur berjalan.
        val gen = VelumTunnel.bumpIntent()
        // Yang sudah dijaga di sini: kegagalan `up()` tidak menghalangi
        // `ReconnectMonitor.ensure()` (bila niatnya masih yang terbaru), jadi peristiwa
        // jaringan berikutnya tetap punya peluang memulihkan tunnel.
        Thread {
            var hasil = VelumDiagnostics.BOOT_FAIL
            try {
                if (VelumTunnel.intentStale(gen)) {
                    VelumLog.i(TAG, "$action: sambung ulang dibatalkan: ada niat pengguna yang lebih baru")
                    hasil = VelumDiagnostics.BOOT_SKIPPED
                } else {
                    if (VelumConnectionContract.connect(context, prefs) { VelumTunnel.intentStale(gen) }) {
                        hasil = VelumDiagnostics.BOOT_OK
                    } else {
                        runCatching { VelumTunnel.down(context) }
                        VelumLog.w(TAG, "$action: handshake tidak terbukti")
                    }
                }
            } catch (e: Exception) {
                VelumLog.w(TAG, "$action: sambung ulang gagal", e)
            } finally {
                // Direkam SEBELUM `pending.finish()`: sesudah itu proses boleh dibunuh
                // kapan saja. `Prefs.bootRecord` memakai `commit()` karena alasan yang sama.
                try {
                    // Durabel (commit) karena kita berada di thread latar dan `finish()`
                    // dipanggil segera setelah ini.
                    prefs.writeBootRecordDurable(
                        VelumDiagnostics.encodeBoot(
                            VelumDiagnostics.Boot(
                                hasil,
                                SystemClock.elapsedRealtime() - mulaiElapsed,
                                mulaiEpoch
                            )
                        )
                    )
                } catch (e: Exception) {
                    // Diagnostik tidak boleh menjadi alasan gagalnya pemulihan tunnel.
                    VelumLog.w(TAG, "$action: gagal merekam hasil boot", e)
                }
                // Jaga sesi: bila peristiwa ini datang sebelum jaringan siap (khas saat
                // boot), callback Available milik pemantau yang akan memulihkan.
                //
                // Pemantau hanya dihidupkan bila niat boot MASIH yang terbaru: mendaftarkan
                // monitor sesudah pengguna memutus akan membuat baris `Pemantau` terbaca
                // "aktif" padahal ia baru saja meminta putus — persis baris yang dipakai
                // membaca kebocoran niat.
                if (!VelumTunnel.intentStale(gen)) ReconnectMonitor.ensure(context)
                pending.finish()
            }
        }.start()
    }

    private companion object {
        const val TAG = "Velum"
    }
}
