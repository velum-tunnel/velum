package com.rollinkxx.velum

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.VpnService
import android.os.SystemClock
import com.wireguard.android.backend.Tunnel
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Menjaga tunnel tetap tersambung saat konektivitas berubah (pindah Wi-Fi/data,
 * putus sesaat) dengan memantul tunnel sekali pakai backoff.
 *
 * Lingkup aplikasi, bukan Activity: tetap bekerja walau UI ditutup. Yang menahan proses
 * tetap hidup selama tunnel UP adalah **VPN yang aktif** (VpnService yang sudah
 * `establish()`), BUKAN layanan latar depan — `GoBackend` tidak pernah memanggil
 * `startForeground` (diverifikasi pada sumber upstream tag `1.0.20260102`), jadi jangan
 * menyandarkan penalaran tentang umur proses pada anggapan ada FGS.
 * Aktif hanya bila diniatkan tersambung ([Prefs.wasUp]); putus manual menghentikannya.
 */
object ReconnectMonitor {
    private const val TAG = "Velum"
    private const val DEBOUNCE_MS = 3000L

    /**
     * Jeda pantulan bertahap. Tiga percobaan cepat saja terlalu mudah menyerah:
     * jaringan yang baru berganti (habis pindah Wi-Fi, baru keluar dari mode pesawat,
     * baru menyala setelah boot) sering butuh belasan detik sebelum benar-benar siap.
     * Pantulan tetap dibatalkan begitu pengguna menekan Putuskan.
     */
    private val BACKOFF_MS = longArrayOf(2000, 5000, 10000, 30000, 60000)

    private val worker = Executors.newSingleThreadExecutor()

    @Volatile
    private var callback: ConnectivityManager.NetworkCallback? = null

    @Volatile
    private var lastBounceMs = 0L

    @Volatile
    private var bouncing = false

    /**
     * Apakah pemantau sedang terdaftar pada jaringan default.
     *
     * Diekspos untuk diagnostik, bukan untuk logika: maintainer menguji tanpa adb, jadi
     * "pemantau hidup/mati" harus bisa dilihat di layar. Yang dibaca di sini adalah keadaan
     * callback yang `@Volatile` — cukup untuk ditampilkan, tetapi TIDAK boleh dipakai
     * sebagai guard keputusan (bisa berubah segera setelah dibaca).
     */
    val isActive: Boolean get() = callback != null

    /** Mulai memantau; aman dipanggil berulang. Panggil setelah tersambung. */
    @Synchronized
    fun ensure(context: Context) {
        if (callback != null) return
        val app = context.applicationContext
        val cm = app.getSystemService(ConnectivityManager::class.java) ?: return
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                if (fromOwnTunnel(app, network)) return
                scheduleBounce(app, "tersedia")
            }

            override fun onLost(network: Network) {
                if (fromOwnTunnel(app, network)) return
                scheduleBounce(app, "hilang")
            }
        }
        // Abaikan callback lengket awal untuk jaringan yang sedang aktif.
        lastBounceMs = SystemClock.elapsedRealtime()
        try {
            cm.registerDefaultNetworkCallback(cb)
        } catch (e: Exception) {
            VelumLog.w(TAG, "gagal mendaftar network callback", e)
            return
        }
        callback = cb
        // Callback awal untuk default network yang sudah aktif sengaja didebounce. Boot atau
        // update tetap perlu satu pemicu recovery eksplisit agar tunnel tidak macet DOWN bila
        // percobaan pertama gagal sementara network tidak berubah lagi.
        recoverIfNeeded(app, VelumRecoveryDecision.Trigger.BOOT_OR_UPDATE)
    }

    /** Berhenti memantau; aman dipanggil berulang. Panggil saat putus manual. */
    @Synchronized
    fun stop(context: Context) {
        val cb = callback ?: return
        callback = null
        try {
            context.applicationContext.getSystemService(ConnectivityManager::class.java)
                ?.unregisterNetworkCallback(cb)
        } catch (e: Exception) {
            VelumLog.w(TAG, "gagal melepas network callback", e)
        }
    }

    /**
     * Apakah peristiwa jaringan ini berasal dari tunnel Velum sendiri.
     *
     * `registerDefaultNetworkCallback` melaporkan jaringan **default**, dan begitu tunnel
     * naik, jaringan VPN itulah yang menjadi default — jadi kenaikan tunnel memicu
     * `onAvailable` untuk dirinya sendiri. Bila peristiwa itu ikut memicu pantulan, tunnel
     * yang baru saja sehat justru dimatikan lagi.
     *
     * Celah ini nyata pada satu kondisi spesifik: debounce 3 detik hanya menahan peristiwa
     * susulan bila pantulan berhasil pada percobaan PERTAMA (jeda 2 detik < 3 detik). Bila
     * berhasil pada percobaan ke-2 atau ke-3 (jeda 5/10 detik > 3 detik), peristiwa akibat
     * tunnel sendiri lolos debounce dan memicu pantulan berikutnya.
     *
     * Menyaring lewat `TRANSPORT_VPN` benar **terlepas dari apakah skenario itu sudah
     * pernah terjadi di lapangan**: peristiwa yang disebabkan tunnel ini memang bukan
     * alasan yang sah untuk memantulkannya. `getNetworkCapabilities(Network)` ada sejak
     * API 23 dan `TRANSPORT_VPN` sejak API 21 — keduanya di bawah minSdk 24.
     */
    /** Jadwalkan recovery tanpa menunggu perubahan network; aman dipanggil berulang. */
    fun recoverIfNeeded(context: Context, trigger: VelumRecoveryDecision.Trigger) {
        val app = context.applicationContext
        if (bouncing) return
        lastBounceMs = SystemClock.elapsedRealtime()
        bouncing = true
        val gen = VelumTunnel.currentIntent
        worker.execute {
            try {
                val prefs = try {
                    Prefs.of(app)
                } catch (e: KeystoreUnavailableException) {
                    VelumLog.w(TAG, "recovery dibatalkan: penyimpanan aman tidak tersedia", e)
                    return@execute
                }
                VelumTunnel.refreshState(app)
                val should = VelumRecoveryDecision.shouldSchedule(
                    trigger = trigger,
                    wasUp = prefs.wasUp,
                    registered = prefs.isRegistered,
                    tunnelUp = VelumTunnel.state == Tunnel.State.UP,
                    bouncing = false,
                    intentStale = VelumTunnel.intentStale(gen)
                )
                if (!should) return@execute
                if (VelumTunnel.state != Tunnel.State.UP) {
                    tryUpOnce(app, prefs, gen)
                } else if (trigger == VelumRecoveryDecision.Trigger.NETWORK) {
                    bounceWithBackoff(app, prefs, gen)
                }
            } catch (e: Exception) {
                VelumLog.w(TAG, "recovery otomatis gagal", e)
            } finally {
                bouncing = false
            }
        }
    }

    private fun fromOwnTunnel(app: Context, network: Network): Boolean = try {
        app.getSystemService(ConnectivityManager::class.java)
            ?.getNetworkCapabilities(network)
            ?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
    } catch (_: Exception) {
        false // ragu: perlakukan sebagai peristiwa jaringan biasa
    }

    private fun scheduleBounce(app: Context, reason: String) {
        if (VelumTunnel.state != Tunnel.State.UP) {
            recoverIfNeeded(app, VelumRecoveryDecision.Trigger.NETWORK)
            return
        }
        val now = SystemClock.elapsedRealtime()
        if (now - lastBounceMs < DEBOUNCE_MS || bouncing) return
        lastBounceMs = now
        bouncing = true
        // Pemantau BUKAN pelaku niat: ia menegakkan niat yang sudah ada. Karena itu ia
        // mengingat generasi saat dijadwalkan (tanpa menaikkannya) dan berhenti begitu
        // pelaku lain — layar utama, ubin, receiver — menyatakan niat yang lebih baru.
        // Tanpa ini, pantulan yang sedang berjalan bisa menyalakan tunnel tepat setelah
        // pengguna memutusnya lewat ubin.
        val gen = VelumTunnel.currentIntent
        worker.execute {
            try {
                // Tanpa keystore tidak ada niat sah yang bisa dibaca; jangan bertindak
                // otomatis dalam keadaan itu.
                val prefs = try {
                    Prefs.of(app)
                } catch (e: KeystoreUnavailableException) {
                    VelumLog.w(TAG, "pantulan dibatalkan: penyimpanan aman tidak tersedia", e)
                    return@execute
                }
                if (!prefs.wasUp || !prefs.isRegistered) return@execute
                if (VelumTunnel.intentStale(gen)) {
                    VelumLog.i(TAG, "pantulan jaringan dibatalkan: ada niat pengguna yang lebih baru")
                    return@execute
                }
                if (VelumTunnel.state != Tunnel.State.UP) {
                    tryUpOnce(app, prefs, gen)
                    return@execute
                }
                VelumLog.i(TAG, "jaringan $reason: memantul tunnel")
                bounceWithBackoff(app, prefs, gen)
            } finally {
                bouncing = false
            }
        }
    }

    /**
     * Niat tersimpan, atau `false` bila penyimpanannya sendiri tidak bisa dibuka:
     * dalam keadaan itu tidak ada tindakan otomatis yang dibenarkan.
     */
    private fun wasUpSafe(app: Context): Boolean = try {
        Prefs.of(app).wasUp
    } catch (e: KeystoreUnavailableException) {
        false
    }

    /** Menyalakan tunnel yang mati padahal diniatkan UP (mis. proses lahir ulang). */
    private fun tryUpOnce(app: Context, prefs: Prefs, gen: Int) {
        if (!wasUpSafe(app)) return // pengguna memutus di tengah jalan
        try {
            VelumTunnel.refreshState(app)
            if (VelumTunnel.state == Tunnel.State.UP) return
            // Proba endpoint di bawah ini bisa makan ~6 detik; niat pengguna diperiksa
            // ulang tepat sebelum tunnel disentuh, bukan hanya sebelum proba.
            if (VelumTunnel.intentStale(gen)) return
            if (VpnService.prepare(app) == null) {
                // Jaringan baru: endpoint terbaik bisa berubah (refresh() mengabaikan
                // hasil yang masih segar <1 jam, jadi murah di jalur cepat ini).
                EndpointProbe.refresh(prefs)
                if (VelumTunnel.intentStale(gen)) {
                    VelumLog.i(TAG, "sambung ulang latar dibatalkan: ada niat pengguna yang lebih baru")
                    return
                }
                VelumTunnel.up(app, prefs)
                // Segarkan penanda waktu SETELAH berhasil, bukan hanya saat menjadwalkan:
                // peristiwa jaringan susulan yang dipicu oleh kenaikan tunnel ini sendiri
                // harus tetap tertahan debounce.
                lastBounceMs = SystemClock.elapsedRealtime()
                VelumLog.i(TAG, "sambung ulang latar berhasil")
            }
        } catch (e: Exception) {
            VelumLog.w(TAG, "sambung ulang latar gagal", e)
        }
    }

    private fun bounceWithBackoff(app: Context, prefs: Prefs, gen: Int) {
        runCatching { VelumTunnel.down(app) }
        for (delay in BACKOFF_MS) {
            try {
                TimeUnit.MILLISECONDS.sleep(delay)
            } catch (_: InterruptedException) {
                return
            }
            if (!wasUpSafe(app)) return // pengguna memutus di tengah pantulan
            if (VelumTunnel.intentStale(gen)) {
                VelumLog.i(TAG, "pantulan tunnel dihentikan: ada niat pengguna yang lebih baru")
                return
            }
            try {
                VelumTunnel.refreshState(app)
                if (VelumTunnel.state == Tunnel.State.UP) return
                EndpointProbe.refresh(prefs)
                // Jeda di atas bisa 60 detik: niat yang dibaca sebelum tidur sudah
                // tidak berarti apa-apa bila pengguna bertindak selama tidur.
                if (VelumTunnel.intentStale(gen)) return
                VelumTunnel.up(app, prefs)
                VelumTunnel.refreshState(app)
                if (VelumTunnel.state == Tunnel.State.UP) {
                    // Sama seperti di `tryUpOnce`: keberhasilan pada percobaan ke-2/ke-3
                    // terjadi LEBIH dari 3 detik setelah jadwal, jadi tanpa penyegaran ini
                    // peristiwa jaringan susulan lolos debounce dan memicu pantulan baru
                    // pada tunnel yang justru baru saja sehat.
                    lastBounceMs = SystemClock.elapsedRealtime()
                    VelumLog.i(TAG, "pantulan tunnel berhasil")
                    return
                }
            } catch (e: Exception) {
                VelumLog.w(TAG, "pantulan tunnel gagal, coba lagi", e)
            }
        }
        VelumLog.w(TAG, "pantulan tunnel menyerah setelah ${BACKOFF_MS.size} percobaan")
    }
}
