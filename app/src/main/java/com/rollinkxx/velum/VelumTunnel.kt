package com.rollinkxx.velum

import android.content.Context
import android.os.Build
import android.os.SystemClock
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Tunnel
import com.wireguard.config.Config
import com.wireguard.config.Interface
import com.wireguard.config.Peer
import java.util.concurrent.atomic.AtomicInteger

/**
 * Pengelola tunnel WARP berbasis WireGuard (GoBackend).
 * Singleton ringan: satu backend, satu tunnel, tanpa service tambahan —
 * VpnService milik library yang menjaga proses tetap hidup selama tersambung.
 *
 * **Kelas ini pemilik tunggal keadaan koneksi.** Dalam satu proses ada beberapa pelaku
 * yang bisa menyalakan atau mematikan tunnel — layar utama, ubin pengaturan cepat,
 * receiver boot, dan pemantau jaringan — dan masing-masing punya thread sendiri.
 * Karena itu:
 *
 * - [up], [down], dan [restart] memakai **satu kunci yang sama** (`@Synchronized` pada
 *   object ini). Tanpa itu, `down` dari satu pelaku bisa disela `up` dari pelaku lain
 *   dan tunnel hidup lagi setelah pengguna menekan Putuskan — untuk aplikasi VPN itu
 *   kebocoran niat pengguna, bukan sekadar kosmetik.
 * - [upSinceElapsedMs] dan notifikasi status diperbarui di sini, bukan di Activity:
 *   keduanya wajib mengikuti umur **tunnel** (proses), bukan umur **layar** (Activity).
 *
 * **Batas jaminan kunci itu (jangan dilebihkan):** `@Synchronized` hanya membuat
 * *transisi tunnel* tidak saling menyela. Ia TIDAK melindungi memo niat ([Prefs.wasUp])
 * maupun hidup/matinya [ReconnectMonitor], karena keduanya ditulis di luar kunci oleh
 * pelaku yang berbeda. Untuk itu ada [bumpIntent]/[intentStale] — lihat dokumentasinya.
 */
object VelumTunnel : Tunnel {
    private const val NAME = "velum"
    private const val MTU = 1280
    private const val DNS = "1.1.1.1, 1.0.0.1"
    private const val ALLOWED_IPS = "0.0.0.0/0, ::/0"

    @Volatile
    private var backend: GoBackend? = null

    /**
     * Context aplikasi, disimpan saat backend dibuat. Dipakai [updateNotification] yang
     * dipanggil dari thread backend — di sana tidak ada Context lain yang tersedia.
     */
    @Volatile
    private var appContext: Context? = null

    @Volatile
    var state: Tunnel.State = Tunnel.State.DOWN
        private set

    /**
     * Kapan tunnel terakhir naik, dalam basis [SystemClock.elapsedRealtime]; `0` bila turun.
     *
     * Sengaja disimpan di sini, bukan di Activity: layar dibuat ulang setiap rotasi, dan
     * durasi yang kembali ke `00:00` padahal koneksi tidak pernah putus adalah informasi
     * yang salah. `elapsedRealtime` dipakai (bukan jam dinding) karena tidak terpengaruh
     * perubahan waktu oleh pengguna atau operator.
     */
    @Volatile
    var upSinceElapsedMs: Long = 0L
        private set

    /** Callback UI; dipanggil dari thread backend, penerima harus pindah ke main thread sendiri. */
    private val listenerSlot = VelumListenerSlot<(Tunnel.State) -> Unit>()
    var listener: ((Tunnel.State) -> Unit)?
        get() = listenerSlot.current
        set(value) {
            listenerSlot.current = value
        }

    /** Clear only the callback still owned by this controller. */
    fun clearListenerIfCurrent(owner: (Tunnel.State) -> Unit): Boolean =
        listenerSlot.clearIfCurrent(owner)

    /**
     * Generasi niat pengguna, milik **proses** — bukan milik satu layar atau satu pelaku.
     *
     * Kunci `@Synchronized` di kelas ini hanya menjamin *transisi tunnel* tidak saling
     * menyela. Ia tidak melindungi dua hal lain yang juga menentukan hasil akhir:
     * memo niat ([Prefs.wasUp]) dan hidup/matinya [ReconnectMonitor]. Keduanya ditulis
     * oleh beberapa pelaku yang tidak saling kenal — layar utama, ubin pengaturan cepat,
     * receiver boot/pembaruan — dan tanpa penanda urutan ada interleaving yang nyata:
     *
     * - pengguna menekan ubin untuk memutus (`wasUp = false`, monitor dimatikan) tepat
     *   saat `connect()` layar utama berada di ekor pekerjaannya → layar menulis
     *   `wasUp = true` dan menyalakan monitor lagi → tunnel yang baru dimatikan
     *   membangkitkan dirinya sendiri pada peristiwa jaringan berikutnya;
     * - pengguna menekan ubin untuk menyambung (proba endpoint ≤ 6 detik), berubah
     *   pikiran, lalu menekan Putuskan di aplikasi → `down()` layar selesai lebih dulu,
     *   kemudian `up()` ubin menuntaskan pekerjaannya → tunnel hidup lagi setelah
     *   diminta mati.
     *
     * Karena itu setiap pelaku menaikkan generasi ini SEBELUM mulai bekerja, menyimpan
     * angkanya, dan memeriksa [intentStale] sebelum menulis keadaan apa pun. Pelaku yang
     * lebih baru selalu menang; yang lebih tua berhenti tanpa menyentuh apa pun.
     */
    private val intentGen = AtomicInteger(0)

    /** Generasi niat saat ini, tanpa menaikkannya (dipakai pelaku yang tidak membawa niat baru). */
    val currentIntent: Int get() = intentGen.get()

    /** Menandai niat pengguna yang baru; kembalikan generasi yang harus dipegang pelaku. */
    fun bumpIntent(): Int = intentGen.incrementAndGet()

    /** Apakah generasi [gen] sudah digantikan pelaku lain yang lebih baru. */
    fun intentStale(gen: Int): Boolean = intentGen.get() != gen

    /** Menetapkan memo UP hanya bila generasi pemanggil masih memegang intent terbaru. */
    @Synchronized
    fun markUpIfCurrent(prefs: Prefs, gen: Int): Boolean {
        if (intentGen.get() != gen) return false
        return prefs.setWasUpDurable(true)
    }

    /** Menghapus memo UP hanya bila pekerjaan ini masih pemilik intent. */
    @Synchronized
    fun clearUpIfCurrent(prefs: Prefs, gen: Int): Boolean {
        if (intentGen.get() != gen) return false
        return prefs.setWasUpDurable(false)
    }

    /** Membatalkan intent dan memo UP sebagai satu operasi terhadap koneksi lama. */
    @Synchronized
    fun cancelIntent(prefs: Prefs): Int {
        val gen = intentGen.incrementAndGet()
        prefs.setWasUpDurable(false)
        return gen
    }

    override fun getName(): String = NAME

    override fun onStateChange(newState: Tunnel.State) {
        state = newState
        if (newState == Tunnel.State.UP) {
            // Hanya diisi bila belum terisi: pantulan down->up yang cepat tidak boleh
            // mereset durasi yang sudah berjalan.
            if (upSinceElapsedMs == 0L) upSinceElapsedMs = SystemClock.elapsedRealtime()
        } else {
            upSinceElapsedMs = 0L
        }
        updateNotification(newState)
        listener?.invoke(newState)
        if (newState == Tunnel.State.DOWN) {
            // Putus manual sudah lebih dulu menulis wasUp=false dan menaikkan generasi niat;
            // recovery otomatis karena state DOWN akan langsung batal pada guard yang sama.
            appContext?.let { ReconnectMonitor.recoverIfNeeded(it, VelumRecoveryDecision.Trigger.TUNNEL_DOWN) }
        }
    }

    /**
     * Notifikasi status mengikuti **tunnel**, bukan Activity.
     *
     * Sebelumnya `show`/`hide` hanya dipanggil dari callback visual layar utama, akibatnya:
     * tunnel yang mati di latar (pantulan menyerah setelah 5 percobaan, atau diputus lewat
     * ubin) meninggalkan notifikasi "Tersambung" yang basi selamanya, dan menyambung lewat
     * ubin saat aplikasi tertutup tidak memunculkan notifikasi sama sekali.
     *
     * Aman dipanggil dari thread backend: `NotificationManager.notify` thread-safe, dan
     * `StatusNotifier.show` sudah menelan `SecurityException` bila izin notifikasi ditolak.
     */
    private fun updateNotification(newState: Tunnel.State) {
        val ctx = appContext ?: return
        if (newState == Tunnel.State.UP) {
            StatusNotifier.show(ctx)
        } else {
            StatusNotifier.hide(ctx)
        }
    }

    private fun backend(context: Context): GoBackend {
        appContext = context.applicationContext
        return backend ?: synchronized(this) {
            backend ?: GoBackend(context.applicationContext).also { backend = it }
        }
    }

    /** Sinkronkan status dengan backend (mis. setelah proses dibuat ulang). Blocking. */
    @Synchronized
    fun refreshState(context: Context): Tunnel.State {
        val s = backend(context).getState(this)
        // Jangan hanya menulis field `state`: proses baru harus menjalankan bookkeeping
        // lifecycle yang sama seperti callback backend (durasi, notifikasi, listener, dan
        // guard recovery). Tanpa ini backend bisa UP sementara sesi lokal tetap berdurasi 0
        // dan UI tidak pernah menerima transisi DOWN->UP.
        onStateChange(s)
        return s
    }

    /** Menyalakan tunnel. Blocking; panggil dari thread latar. */
    @Synchronized
    @Throws(Exception::class)
    fun up(context: Context, prefs: Prefs) {
        backend(context).setState(this, Tunnel.State.UP, buildConfig(prefs))
    }

    /** Mematikan tunnel. Blocking; panggil dari thread latar. */
    @Synchronized
    @Throws(Exception::class)
    fun down(context: Context) {
        backend(context).setState(this, Tunnel.State.DOWN, null)
    }

    /**
     * Mematikan lalu menyalakan tunnel dengan konfigurasi terbaru, sebagai **satu operasi
     * atomik** terhadap pelaku lain.
     *
     * Dipakai saat memutar endpoint: pasangan `down` + `up` yang dipanggil terpisah bisa
     * disela `down` dari pelaku lain (mis. pengguna menekan Putuskan), sehingga tunnel
     * berakhir hidup padahal pengguna memintanya mati.
     *
     * Niat pengguna ([Prefs.wasUp]) dibaca ulang SETELAH `down` dan di dalam kunci:
     * bila ia memutus di tengah jalan, tunnel tidak dihidupkan lagi.
     */
    @Synchronized
    @Throws(Exception::class)
    fun restart(context: Context, prefs: Prefs, shouldContinue: () -> Boolean = { prefs.wasUp }) {
        val b = backend(context)
        b.setState(this, Tunnel.State.DOWN, null)
        if (!shouldContinue()) return // intent baru membatalkan sebelum tunnel dihidupkan lagi
        b.setState(this, Tunnel.State.UP, buildConfig(prefs))
    }

    /** Hasil baca statistik transfer dari backend; null bila gagal. */
    class TrafficStats(val rxBytes: Long, val txBytes: Long, val latestHandshakeMs: Long)

    /**
     * Membaca statistik transfer (jumlah semua peer). Blocking ringan;
     * null bila backend gagal. Panggil dari thread latar.
     *
     * Sengaja TIDAK `@Synchronized`: dipanggil berulang dari [VelumController.awaitHandshake]
     * dan dari tiker UI, dan tidak boleh ikut mengantre di belakang `up()` yang lambat —
     * kalau mengantre, menunggu handshake justru bisa macet selama tunnel dibangun.
     */
    fun traffic(context: Context): TrafficStats? {
        return try {
            val stats = backend(context).getStatistics(this)
            var rx = 0L
            var tx = 0L
            var hs = 0L
            for (key in stats.peers()) {
                val p = stats.peer(key) ?: continue
                rx += p.rxBytes
                tx += p.txBytes
                if (p.latestHandshakeEpochMillis > hs) hs = p.latestHandshakeEpochMillis
            }
            TrafficStats(rx, tx, hs)
        } catch (_: Exception) {
            null
        }
    }

    /** Keadaan "Selalu aktif" VPN sistem; null = tidak terbaca (API < 29 / layanan mati). */
    class AlwaysOnState(val alwaysOn: Boolean, val lockdown: Boolean)

    /**
     * Membaca keadaan always-on/lockdown dari backend WireGuard.
     *
     * Hanya berhasil bila (a) Android 29+ — `VpnService.isAlwaysOn()`/`isLockdownEnabled()`
     * ditambahkan di API 29 — dan (b) `VpnService` sedang hidup di proses ini (tunnel UP).
     * Bila layanan mati, `GoBackend` melempar `TimeoutException` (future-nya di-reset saat
     * service `onDestroy`) → null. Pemanggil wajib menampilkan fallback, bukan menebak.
     * Blocking ringan; panggil dari thread latar.
     */
    fun alwaysOnState(): AlwaysOnState? {
        if (Build.VERSION.SDK_INT < 29) return null
        val b = backend ?: return null
        return try {
            AlwaysOnState(b.isAlwaysOn(), b.isLockdownEnabled())
        } catch (_: Exception) {
            null
        }
    }

    private fun buildConfig(prefs: Prefs): Config {
        val addresses = buildString {
            append(prefs.addressV4).append("/32")
            prefs.addressV6?.takeIf { it.isNotEmpty() }?.let { append(", ").append(it).append("/128") }
        }
        val ifaceBuilder = Interface.Builder()
            .parsePrivateKey(requireNotNull(prefs.privateKey))
            .parseAddresses(addresses)
            .parseDnsServers(DNS)
            .parseMtu(MTU.toString())
        val excluded = prefs.excludedApps
        if (excluded.isNotEmpty()) ifaceBuilder.excludeApplications(excluded)
        val iface = ifaceBuilder.build()
        val peer = Peer.Builder()
            .parsePublicKey(requireNotNull(prefs.peerPublicKey))
            .parseAllowedIPs(ALLOWED_IPS)
            .parseEndpoint(prefs.effectiveEndpoint ?: VelumApi.DEFAULT_ENDPOINT)
            .parsePersistentKeepalive("25")
            .build()
        return Config.Builder().setInterface(iface).addPeer(peer).build()
    }
}
