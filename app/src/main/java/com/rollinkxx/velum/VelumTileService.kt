package com.rollinkxx.velum

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.net.VpnService
import android.os.Handler
import android.os.Looper
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.wireguard.android.backend.Tunnel
import java.util.concurrent.Executors

/**
 * Ubin pengaturan cepat: menyambung/memutus tanpa membuka aplikasi.
 *
 * Berjalan di proses aplikasi yang sama. Bila proses sedang mati, status ubin
 * hanya mengikuti nilai yang tersimpan di backend WireGuard — cukup untuk
 * menyalakan, dan disegarkan begitu ubin terlihat.
 */
class VelumTileService : TileService() {

    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()

    override fun onStartListening() {
        super.onStartListening()
        refreshTileState()
    }

    /**
     * Menghentikan executor. Tanpa ini setiap instance layanan meninggalkan satu thread
     * **non-daemon** yang tidak pernah mati — dan sistem membuat-dan-membuang TileService
     * berulang kali selama pemakaian normal, jadi threadnya menumpuk dan menahan proses
     * tetap hidup. `shutdown()` (bukan `shutdownNow()`) agar aksi yang sudah berjalan
     * tidak dipotong di tengah `VelumTunnel.up()`.
     */
    override fun onDestroy() {
        worker.shutdown()
        super.onDestroy()
    }

    override fun onClick() {
        super.onClick()
        val app = applicationContext
        worker.execute {
            try {
                // KeystoreUnavailableException juga harus masuk failure path ini; bila Prefs
                // dibuka sebelum try, executor dapat menghentikan aksi tanpa update tile.
                val prefs = Prefs.of(app)
                // State lokal di-reset saat proses lahir ulang; backend adalah sumber
                // kebenaran untuk menentukan aksi tile, bukan nilai default DOWN.
                VelumTunnel.refreshState(app)
                val wasUp = VelumTunnel.state == Tunnel.State.UP
                if (!wasUp && VpnService.prepare(app) != null) {
                    // Belum ada persetujuan VPN: hanya aplikasi yang bisa memintanya.
                    openApp()
                    updateTile()
                    return@execute
                }
                if (!wasUp && !prefs.isRegistered) {
                    // Belum terdaftar: registrasi butuh layar untuk menampilkan hasilnya.
                    // Sengaja TIDAK menaikkan generasi niat di jalur ini — membuka aplikasi
                    // bukan perubahan niat koneksi, dan menaikkannya di sini akan
                    // membatalkan registrasi yang mungkin sedang berjalan di layar utama.
                    openApp()
                } else {
                    // Niat baru dari pelaku ini, dicatat tepat sebelum tunnel disentuh.
                    // Tanpa ini, ubin dan layar utama saling menimpa `wasUp` dan
                    // hidup/matinya pemantau: pengguna memutus lewat ubin, lalu ekor
                    // `connect()` milik layar menulis `wasUp = true` + menyalakan pemantau
                    // lagi, dan tunnel yang baru dimatikan membangkitkan dirinya sendiri
                    // pada peristiwa jaringan berikutnya.
                    if (wasUp) {
                        VelumTunnel.cancelIntent(prefs)
                        ReconnectMonitor.stop(app)
                        VelumTunnel.down(app)
                    } else {
                        val gen = VelumTunnel.bumpIntent()
                        // Proba endpoint bisa makan ~6 detik: niat pengguna bisa berubah di
                        // dalamnya, jadi diperiksa ulang tepat sebelum tunnel disentuh.
                        EndpointProbe.refresh(prefs)
                        if (VelumTunnel.intentStale(gen)) {
                            VelumLog.i(TAG, "aksi ubin (sambung) dibatalkan: ada niat yang lebih baru")
                        } else {
                            if (VelumConnectionContract.connect(app, prefs) { VelumTunnel.intentStale(gen) } &&
                                VelumTunnel.markUpIfCurrent(prefs, gen)
                            ) {
                                ReconnectMonitor.ensure(app)
                            } else {
                                if (VelumTunnel.clearUpIfCurrent(prefs, gen)) {
                                    runCatching { VelumTunnel.down(app) }
                                    ReconnectMonitor.stop(app)
                                }
                                VelumLog.w(TAG, "aksi ubin sambung gagal: handshake tidak terbukti")
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                VelumLog.w(TAG, "aksi ubin gagal", e)
            }
            updateTile()
        }
    }

    private fun updateTile() {
        main.post {
            val tile = qsTile ?: return@post
            tile.state =
                if (VelumTunnel.state == Tunnel.State.UP) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            tile.updateTile()
        }
    }

    private fun refreshTileState() {
        worker.execute {
            runCatching { VelumTunnel.refreshState(applicationContext) }
            updateTile()
        }
    }

    /**
     * Membuka aplikasi (izin VPN belum ada / belum terdaftar).
     * `startActivityAndCollapse(Intent)` usang sejak API 34 dan diganti varian
     * PendingIntent — keduanya dipakai sesuai versi karena minSdk masih 24.
     */
    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= 34) {
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, flags))
        } else {
            // startActivityAndCollapse(Intent) sudah usang sejak API 34 dan penggantinya
            // baru ada di versi itu. Pakai startActivity biasa: fungsinya sama, hanya
            // panel cepat yang tidak ikut menutup — urusan kosmetik.
            startActivity(intent)
        }
    }

    private companion object {
        const val TAG = "Velum"
    }
}
