package com.rollinkxx.velum

import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import java.io.IOException
import com.wireguard.android.backend.Tunnel
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicInteger

/**
 * Orkestrasi koneksi & uji: memutuskan **apa** yang dilakukan, sementara
 * [MainActivity] hanya merender **bagaimana** hasilnya ditampilkan.
 *
 * **Batas umur kelas ini (jujur, sebelumnya dokumennya mengklaim sebaliknya):**
 * controller dibuat per-Activity dan dimatikan di `onDestroy`, jadi ia TIDAK bertahan
 * saat layar dibuat ulang. Yang membuatnya tidak merusak adalah keadaan koneksi tidak
 * lagi disimpan di sini:
 * - status & durasi tunnel hidup di [VelumTunnel] (umur proses),
 * - hasil uji terakhir hidup di [Prefs],
 * - [prevState] diawali dari status tunnel yang sebenarnya, sehingga layar yang baru
 *   tidak menganggap "sudah UP sejak tadi" sebagai transisi baru (tidak ada lagi
 *   durasi yang direset ke 00:00 dan auto-uji yang berjalan ulang tiap rotasi).
 *
 * **Aturan thread kelas ini:** tidak ada satu pun `ui.*` yang dipanggil langsung.
 * Semua lewat [onUi], yang menjalankan segera bila pemanggil sudah di main thread dan
 * mengantre bila tidak. Alasannya nyata, bukan gaya: jalur ulangan uji memanggil
 * `runTraceTest` dari `testWorker`, dan dulu baris itu menulis `TextView` dari thread
 * latar — kebetulan tidak crash hanya karena kedua view targetnya berukuran tetap,
 * bukan karena benar.
 */
class VelumController(context: Context, private val ui: Ui) {

    /** Semua hal yang bisa diminta controller kepada UI. */
    interface Ui {
        fun setBusy(busy: Boolean)
        fun setStatusText(resId: Int)
        fun setMessageRes(resId: Int)
        fun setMessage(text: String)
        fun setTestTextRes(resId: Int)
        fun showTest(result: VelumTestResult?)
        fun render(state: Tunnel.State)
        fun onConnectedVisual()
        fun onDisconnectedVisual()
        fun refreshStaticInfo()
    }

    private val app = context.applicationContext
    private val prefs = Prefs.of(app)
    private val main = Handler(Looper.getMainLooper())
    private val worker: ExecutorService = Executors.newSingleThreadExecutor()
    /** Terpisah dari [worker] agar uji yang lambat tidak menahan Sambungkan/Putuskan. */
    private val testWorker: ExecutorService = Executors.newSingleThreadExecutor()

    @Volatile
    var busy = false
        private set

    /** Status tunnel terakhir yang diketahui (sumber: backend WireGuard). */
    val state: Tunnel.State get() = VelumTunnel.state

    /**
     * Diawali dari status tunnel yang SEBENARNYA, bukan dari `DOWN` tetap.
     *
     * Bila layar dibuat ulang saat tunnel masih UP, nilai awal `DOWN` membuat
     * `applyCurrentState()` melihat transisi DOWN->UP yang tidak pernah terjadi:
     * durasi direset, auto-uji dijalankan ulang, dan notifikasi diposting lagi.
     */
    private var prevState: Tunnel.State = VelumTunnel.state

    private var testedSinceUp = false

    /** Atomik: dinaikkan dari main thread DAN dari `testWorker`, jadi tidak boleh `++` polos. */
    private val testJobId = AtomicInteger(0)

    @Volatile
    private var pendingTest: Runnable? = null

    /** Ada uji yang hasilnya belum pernah ditampilkan (dipakai membersihkan baris "Menunggu data…"). */
    @Volatile
    private var testInFlight = false

    /** Status utama sementara saat pengguna menekan tombol Uji koneksi. */
    @Volatile
    private var buttonTestStatusShown = false

    /**
     * Menekan auto-uji selama pemutaran endpoint. Diset/lepas lewat [onUi] (lihat
     * [rotateEndpointAndReconnect]) supaya urutannya pasti terhadap applyState.
     */
    @Volatile
    private var testSuppressAuto = false

    /*
     * Generasi niat pengguna TIDAK disimpan di sini lagi: ia milik proses dan dipegang
     * [VelumTunnel.bumpIntent]/[VelumTunnel.intentStale]. Versi per-controller yang lama
     * hanya menutup race antar-pekerjaan di dalam satu layar (Sambungkan vs Putuskan vs
     * Daftar ulang), tetapi tidak terhadap pelaku lain yang ikut menulis `wasUp` dan
     * hidup/matinya [ReconnectMonitor] — ubin pengaturan cepat dan receiver boot.
     * Akibatnya nyata: pengguna memutus lewat ubin, lalu ekor `connect()` milik layar
     * menulis `wasUp = true` dan menyalakan monitor lagi, sehingga tunnel yang baru
     * dimatikan membangkitkan dirinya sendiri pada peristiwa jaringan berikutnya.
     */

    /** Controller sudah dimatikan: jangan sentuh UI, jangan jadwalkan ulangan baru. */
    @Volatile
    private var dead = false

    init {
        VelumTunnel.listener = { newState -> main.post { applyState(newState) } }
    }

    /**
     * Melepas semua kaitan. Dipanggil dari `MainActivity.onDestroy`.
     *
     * `shutdown()` dipakai, BUKAN `shutdownNow()`: menginterupsi thread yang sedang berada
     * di dalam `VelumTunnel.up()` berarti memotong pembangunan tunnel di tengah jalan —
     * lebih berbahaya daripada membiarkan operasi yang sudah dimulai selesai. Hasilnya
     * sekadar tidak dirender, karena [dead] sudah menutup jalur ke UI.
     */
    fun destroy() {
        dead = true
        VelumTunnel.listener = null
        cancelPendingTest()
        worker.shutdown()
        testWorker.shutdown()
    }

    /**
     * Satu-satunya jalur ke UI: jalankan segera bila sudah di main thread, antre bila tidak.
     * Membuat kesalahan "menyentuh view dari thread latar" tidak mungkin terulang,
     * apa pun thread pemanggilnya.
     */
    private fun onUi(block: () -> Unit) {
        if (dead) return
        if (Looper.myLooper() == Looper.getMainLooper()) block() else main.post { if (!dead) block() }
    }

    /**
     * Menaikkan generasi niat **proses** dan mengembalikan angka yang harus dipegang
     * pekerjaan latar. Hanya dipanggil dari main thread (semua pemicu aksi berasal dari
     * klik/kallback UI). Pelaku lain — ubin pengaturan cepat, receiver boot — memakai
     * pencacah yang sama di [VelumTunnel], jadi urutan niat bersifat global.
     */
    private fun nextIntent(): Int = VelumTunnel.bumpIntent()

    /**
     * Menyerahkan pekerjaan latar dengan aman.
     *
     * `ExecutorService.execute` melempar `RejectedExecutionException` setelah `shutdown()`,
     * dan itu terjadi di main thread → crash. Jalurnya nyata: rotasi layar saat
     * [refreshStateAsync] berjalan membuat `onDone` memanggil [connect] pada controller
     * yang baru saja dimatikan. Jadi penyerahan selalu diperiksa, dan sisa race antara
     * pemeriksaan dan penyerahan ditelan di sini — bukan dibiarkan jadi crash.
     */
    private fun submit(executor: ExecutorService, block: () -> Unit) {
        if (dead) return
        try {
            executor.execute(block)
        } catch (_: RejectedExecutionException) {
            VelumLog.i(TAG, "pekerjaan dilewati: controller sudah dimatikan")
        }
    }

    /**
     * Apakah pekerjaan dengan generasi [gen] sudah digantikan niat yang lebih baru —
     * dari layar ini maupun dari pelaku lain (ubin pengaturan cepat, receiver boot).
     */
    private fun stale(gen: Int) = dead || VelumTunnel.intentStale(gen)

    // ---------- Status ----------

    /**
     * Terapkan status: efek samping selalu jalan, teks status mengikuti UI.
     * Selalu dipanggil dari main thread; [onUi] tetap dipakai agar aturan
     * "tidak ada `ui.*` langsung" berlaku seragam dan tidak bergantung pada ingatan.
     */
    private fun applyState(newState: Tunnel.State) {
        if (newState != prevState) {
            prevState = newState
            if (newState == Tunnel.State.UP) {
                onUi { ui.onConnectedVisual() }
                if (!testedSinceUp && !testSuppressAuto && prefs.isRegistered) {
                    testedSinceUp = true
                    runTraceTest(fromButton = false)
                }
            } else {
                testedSinceUp = false
                cancelPendingTest()
                onUi { ui.onDisconnectedVisual() }
            }
        }
        onUi { ui.render(newState) }
    }

    /** Terapkan status yang diketahui saat ini ke UI (mis. setelah Activity hidup lagi). */
    fun applyCurrentState() = applyState(VelumTunnel.state)

    /** Sinkronkan status dengan backend di latar, lalu jalankan [onDone] di main thread. */
    fun refreshStateAsync(onDone: () -> Unit) {
        submit(worker) {
            val s = runCatching { VelumTunnel.refreshState(app) }.getOrDefault(VelumTunnel.state)
            main.post {
                if (dead) return@post // layar sudah ditutup: jangan sentuh UI, jangan lanjut
                applyState(s)
                onDone()
            }
        }
    }

    /**
     * Pulihkan sesi bila proses lahir ulang: diniatkan UP tapi tunnel DOWN dan
     * persetujuan VPN masih berlaku → sambung otomatis; monitor selalu dipastikan
     * aktif selama diniatkan UP.
     */
    fun resumeIfNeeded() {
        if (!prefs.wasUp || !prefs.isRegistered) return
        ReconnectMonitor.ensure(app)
        if (!busy && VelumTunnel.state != Tunnel.State.UP && VpnService.prepare(app) == null) connect()
    }

    // ---------- Aksi ----------

    /** Intent persetujuan VPN bila belum diberikan; null bila sudah boleh menyambung. */
    fun vpnIntent(): Intent? = VpnService.prepare(app)

    fun connect() {
        val gen = nextIntent()
        setBusy(true)
        onUi { ui.setMessage("") }
        submit(worker) {
            try {
                if (stale(gen)) return@submit
                if (prefs.isRegistered && !prefs.warpEnabled) {
                    // Akun era lama tanpa flag WARP: coba sembuhkan otomatis (fail-safe,
                    // kegagalan tidak boleh menghalangi penyambungan).
                    try {
                        VelumApi.ensureWarpEnabled(prefs)
                        onUi { ui.refreshStaticInfo() }
                    } catch (e: Exception) {
                        VelumLog.w(TAG, "auto-heal akun gagal, lanjut tanpa heal", e)
                    }
                }
                if (stale(gen)) return@submit
                if (!prefs.isRegistered) {
                    onUi { ui.setStatusText(R.string.status_registering) }
                    try {
                        registerWithRetry()
                    } catch (e: Exception) {
                        fail(R.string.err_register, e)
                        return@submit
                    }
                }
                if (stale(gen)) return@submit
                onUi { ui.setStatusText(R.string.status_probing) }
                EndpointProbe.refresh(prefs)
                if (stale(gen)) return@submit
                onUi {
                    ui.setStatusText(R.string.status_connecting)
                    ui.refreshStaticInfo()
                }
                val connected = VelumConnectionContract.connect(app, prefs, CONNECT_HANDSHAKE_WAIT_MS) { stale(gen) }
                // State.UP hanya membuktikan antarmuka TUN berhasil dibuat. Endpoint
                // yang dipilih lewat RTT TCP/443 belum membuktikan bahwa UDP/2408
                // (WireGuard) dapat dilewati pada jaringan ini. Jangan menyatakan
                // koneksi berhasil sebelum handshake nyata terlihat.
                if (!connected) {
                    if (stale(gen)) return@submit
                    if (!tryValidatedEndpointFallback(gen)) {
                        throw IOException("endpoint WireGuard tidak menghasilkan handshake")
                    }
                }
                // Niat dibaca ulang SETELAH up(): bila pengguna menekan Putuskan selama
                // penyambungan, jangan menimpa niatnya dan jangan hidupkan pemantau lagi.
                if (stale(gen)) return@submit
                if (!VelumTunnel.markUpIfCurrent(prefs, gen)) {
                    if (stale(gen)) return@submit
                    throw IOException("memo koneksi tidak dapat disimpan")
                }
                rememberWorkingEndpoint()
                ReconnectMonitor.ensure(app)
                onUi { setBusy(false); applyState(VelumTunnel.state) }
            } catch (e: Exception) {
               // Bersihkan HANYA bila niat percobaan ini masih yang terbaru.
               // Tanpa cek ini, kegagalan yang terlambat (mis. registrasi atau proba
               // endpoint yang timeout lama setelah pengguna sudah menekan Putuskan
               // atau menekan Sambungkan lagi) ikut menulis: `wasUp = false`
               // menimpa memo niat milik percobaan yang lebih baru, dan
               // `ReconnectMonitor.stop` mematikan pemantau yang seharusnya tetap
               // hidup — tunnel lalu bisa mati senyap tanpa pemulihan, persis
               // kebocoran niat yang [VelumTunnel.bumpIntent] dirancang cegah.
               // Percobaan yang kalah ini tidak boleh menyentuh keadaan bersama;
               // pembersihan milik niat terbaru (jalur sukses, Putuskan, atau ubin).
               if (stale(gen)) {
                   VelumLog.i(TAG, "kegagalan sambung diabaikan: ada niat pengguna yang lebih baru", e)
                   return@submit
               }
                // Koneksi yang gagal tidak boleh meninggalkan TUN/VPN aktif tanpa
                // niat yang tervalidasi dan tanpa pemantau yang konsisten.
                VelumTunnel.clearUpIfCurrent(prefs, gen)
                ReconnectMonitor.stop(app)
                runCatching { VelumTunnel.down(app) }
                fail(R.string.err_connect, e)
            }
        }
    }

    /**
     * Registrasi dengan satu kali ulangan berjeda, khusus untuk kegagalan jaringan.
     * Jaringan yang baru saja bangun (habis boot, baru ganti Wi-Fi/data) sering gagal
     * pada percobaan pertama; penolakan dari server tidak pernah diulang karena
     * mengulang permintaan ke klien yang ditolak tidak ada gunanya.
     */
    private fun registerWithRetry() {
        try {
            VelumApi.register(prefs)
            return
        } catch (first: Exception) {
            if (VelumError.kindOf(first) != VelumError.Kind.NETWORK) throw first
            VelumLog.i(TAG, "registrasi gagal, mengulang sekali setelah jeda", first)
        }
        try {
            Thread.sleep(REGISTER_RETRY_MS)
        } catch (_: InterruptedException) {
            throw IOException("registrasi dibatalkan")
        }
        VelumApi.register(prefs)
    }

    /**
     * Kandidat tercepat gagal handshake: coba kandidat terukur berikutnya satu per satu,
     * masing-masing DIVERIFIKASI dengan handshake WireGuard singkat (batas
     * [VERIFIED_HANDSHAKE_WAIT_MS] per kandidat, maksimal [MAX_ENDPOINT_FALLBACKS]).
     *
     * Pengukuran RTT dilakukan SEKALI di awal ([EndpointProbe.measureRanked]) lalu
     * kandidat dipasang tanpa mengukur ulang — versi sebelumnya mengukur ulang pada
     * setiap percobaan, sehingga rotasi bisa menyita waktu tiga kali anggaran proba.
     * RTT tetap hanya prioritas percobaan; keputusan akhir selalu handshake UDP/2408,
     * dan [Prefs.workingEndpoint] tidak pernah diisi sebelum handshake itu terlihat.
     *
     * Urutan percobaan dan arti pemenangnya dihitung [VelumVerifiedChoice] (murni,
     * teruji unit dengan handshake tiruan); fungsi ini mengeksekusinya terhadap
     * GoBackend sungguhan dan menjaga niat pengguna ([stale]).
     */
    private fun tryValidatedEndpointFallback(gen: Int): Boolean {
        // Endpoint manual adalah keputusan pengguna: kegagalannya dilaporkan apa adanya
        // (pesan jaringan menyebut endpoint yang dipakai), bukan diatasi diam-diam
        // dengan endpoint lain yang justru tidak pernah diminta.
        if (!prefs.manualEndpoint.isNullOrBlank()) return false
        val failingHost = prefs.effectiveEndpoint?.let(VelumFormat::hostPart)
        val ranked = EndpointProbe.measureRanked(prefs)
        val hasil = VelumVerifiedChoice.pickVerified(
            ranked = ranked,
            skip = failingHost,
            maxCandidates = MAX_ENDPOINT_FALLBACKS
        ) { host ->
            // Niat lebih baru, atau pemasangan yang tidak memindahkan endpoint efektif,
            // berarti kandidat ini memang tidak layak dicoba — BUKAN handshake gagal.
            if (stale(gen) || !EndpointProbe.applyCandidate(prefs, host, failingHost)) {
                return@pickVerified false
            }
            val ok = try {
                VelumConnectionContract.reconnect(app, prefs, VERIFIED_HANDSHAKE_WAIT_MS) { stale(gen) }
            } catch (e: Exception) {
                VelumLog.w(TAG, "gagal membangun ulang dengan kandidat endpoint", e)
                false
            }
            if (ok && !stale(gen)) {
                // Hanya sesudah handshake nyata endpoint ini layak dicatat terbukti.
                rememberWorkingEndpoint()
                true
            } else {
                // Bukti endpoint ini gagal; jangan biarkan ia terus diprioritaskan.
                prefs.workingEndpoint = null
                false
            }
        }
        if (stale(gen)) return false
        if (hasil.winner == null) {
            VelumLog.d(TAG, "rotasi tervalidasi: tidak ada kandidat yang lolos handshake (dicoba: ${hasil.attempted.size})")
            return false
        }
        VelumLog.d(TAG, "endpoint terverifikasi handshake: ${prefs.effectiveEndpoint} (${hasil.attempted.size} dicoba)")
        return true
    }

    fun disconnect() {
        VelumTunnel.cancelIntent(prefs)
        // Putuskan juga harus membatalkan retry uji yang tertunda; jika tidak, retry
        // dapat menulis kembali status uji setelah tunnel sudah dimatikan pengguna.
        cancelPendingTest()
        setBusy(true)
        onUi { ui.setStatusText(R.string.status_disconnecting) }
        ReconnectMonitor.stop(app)
        submit(worker) {
            runCatching { VelumTunnel.down(app) }
            onUi { setBusy(false); applyState(VelumTunnel.state) }
        }
    }

    /** Hapus registrasi dan putuskan; UI bertanggung jawab meminta konfirmasi dulu. */
    fun reset() {
        if (busy) return
        VelumTunnel.cancelIntent(prefs)
        setBusy(true)
        cancelPendingTest()
        ReconnectMonitor.stop(app)
        submit(worker) {
            runCatching { VelumTunnel.down(app) }
            VelumApi.unregister(prefs)
            onUi {
                setBusy(false)
                applyState(Tunnel.State.DOWN)
                ui.refreshStaticInfo()
                ui.showTest(prefs.lastTest) // registrasi dihapus → hasil uji lama ikut hilang
                ui.setMessageRes(R.string.reset_done)
            }
        }
    }

    /** Uji koneksi yang dipicu pengguna (tombol Uji koneksi). */
    fun runTest() {
        if (busy) return
        runTraceTest(fromButton = true)
    }

    /** Membaca statistik trafik di latar, lalu menyerahkannya ke [onResult] di main thread. */
    fun runStats(onResult: (VelumTunnel.TrafficStats?) -> Unit) {
        submit(worker) {
            val stats = VelumTunnel.traffic(app)
            main.post { if (!dead) onResult(stats) }
        }
    }

    /**
     * Membaca keadaan "Selalu aktif" VPN sistem di latar, lalu menyerahkannya ke
     * [onResult] di main thread; null = tidak terbaca (API < 29 / tunnel turun).
     */
    fun runAlwaysOnState(onResult: (VelumTunnel.AlwaysOnState?) -> Unit) {
        submit(worker) {
            val s = VelumTunnel.alwaysOnState()
            main.post { if (!dead) onResult(s) }
        }
    }

    // ---------- Uji trace ----------

    /**
     * Menjalankan uji trace; dipakai tombol Uji koneksi dan auto-uji saat tersambung.
     *
     * Uji SENGAJA tidak langsung menembak jaringan: `State.UP` dari backend hanya berarti
     * antarmuka TUN sudah dibuat, belum tentu handshake WireGuard-nya selesai. Permintaan
     * yang keluar sebelum handshake (atau memakai soket sisa sesi sebelum VPN aktif) tidak
     * lewat WARP — dulu hasilnya "Belum lewat Velum" palsu, sekarang juga diketahui muncul
     * sebagai galat DNS menyesatkan ("Unable to resolve host ...") yang membuat pengguna
     * menyalahkan jaringannya sendiri.
     *
     * **Bisa dipanggil dari main thread ATAU dari `testWorker`** (jalur ulangan). Karena
     * itu setiap sentuhan UI di sini wajib lewat [onUi].
     */
    private fun runTraceTest(fromButton: Boolean, attempt: Int = 0) {
        cancelPendingTest(invalidate = false)
        val job = testJobId.incrementAndGet()
        testInFlight = true
        if (fromButton) {
            buttonTestStatusShown = true
            onUi {
                ui.setStatusText(R.string.test_on)
                ui.setMessage("")
            }
        }
        onUi { ui.setTestTextRes(R.string.test_waiting) }
        submit(testWorker) {
            val waitMs = if (attempt == 0) HANDSHAKE_WAIT_MS else HANDSHAKE_WAIT_RETRY_MS
            val ready = awaitHandshake(waitMs)
            val tunnelUp = VelumTunnel.state == Tunnel.State.UP
            // Handshake = bukti pertama ada data yang benar-benar lewat; hanya sejak itu
            // endpoint ini layak dicatat sebagai "terbukti bekerja".
            if (ready) rememberWorkingEndpoint()
            var trace: VelumFormat.TraceInfo? = null
            var error: String? = null
            if (tunnelUp && ready) {
                onUi { ui.setTestTextRes(R.string.test_running) }
                try {
                    trace = VelumApi.fetchTrace()
                } catch (e: Exception) {
                    error = e.message ?: e.javaClass.simpleName
                }
            }
            main.post { publishTestResult(job, tunnelUp, ready, trace, error, fromButton, attempt) }
        }
    }

    /**
     * Menunggu handshake WireGuard pertama (bukti tunnel benar-benar bisa dilewati).
     * Blocking — latar saja.
     *
     * Sengaja TIDAK mengembalikan "siap" hanya karena antarmuka UP: itu sumber kegagalan
     * senyap yang sudah terbukti di lapangan (lihat dokumen kelas ini).
     */
    private fun awaitHandshake(maxWaitMs: Long): Boolean {
        return VelumConnectionContract.awaitHandshake(app, maxWaitMs) { dead }
    }

    /** Mencatat endpoint yang terbukti menghasilkan handshake (bukti > perkiraan RTT). */
    private fun rememberWorkingEndpoint() {
        val current = prefs.effectiveEndpoint ?: return
        if (prefs.workingEndpoint != current) {
            prefs.workingEndpoint = current
            VelumLog.d(TAG, "endpoint terbukti bekerja: $current")
        }
    }

    /** Menampilkan hasil uji; hasil dari uji yang sudah usang/turun tidak pernah ditulis. */
    private fun publishTestResult(
        job: Int,
        tunnelUp: Boolean,
        handshakeReady: Boolean,
        trace: VelumFormat.TraceInfo?,
        error: String?,
        fromButton: Boolean,
        attempt: Int
    ) {
        if (job != testJobId.get()) return // uji ini sudah dibatalkan/diganti uji baru
        testInFlight = false
        when (
            VelumTestDecision.decide(
                tunnelUp = tunnelUp,
                handshakeReady = handshakeReady,
                trace = trace,
                error = error,
                attempt = attempt,
                maxAttempts = MAX_TEST_ATTEMPTS
            )
        ) {
            TestAction.RETRY -> {
                val rotate = !handshakeReady
                val retry = Runnable {
                    if (dead) return@Runnable
                    submit(testWorker) {
                        if (dead) return@submit
                        // Tanpa handshake, mengulang saja tidak menolong: endpoint lain
                        // dicoba lebih dulu. Ini penambal nyata untuk jaringan yang
                        // memblokir endpoint WARP tertentu.
                        if (rotate) rotateEndpointAndReconnect()
                        runTraceTest(fromButton, attempt + 1)
                    }
                }
                pendingTest = retry
                main.postDelayed(retry, TEST_RETRY_MS)
            }
            // Tunnel turun di tengah uji: hasil dibuang, baris uji dikembalikan ke hasil sah
            // terakhir, dan pesan sementara dibersihkan agar tidak tertinggal.
            TestAction.DROP -> {
                onUi {
                    ui.showTest(prefs.lastTest)
                    if (fromButton) {
                        buttonTestStatusShown = false
                        ui.render(VelumTunnel.state)
                    }
                }
            }
            TestAction.PUBLISH, TestAction.PUBLISH_NO_DATA -> {
                val result = VelumTestResult.of(
                    handshakeReady = handshakeReady,
                    trace = trace,
                    error = error,
                    atEpochMs = System.currentTimeMillis()
                )
                prefs.lastTest = result
                onUi {
                    ui.showTest(result)
                    if (fromButton) {
                        buttonTestStatusShown = false
                        ui.render(VelumTunnel.state)
                    }
                    // "Belum ada data" juga diberitahukan saat uji otomatis: pengguna melihat
                    // status "Tersambung" tetapi tidak ada yang berjalan, dan tanpa penjelasan
                    // keadaan itu tampak seperti kegagalan yang tidak bisa ditindaklanjuti.
                    if (!fromButton && result.kind == VelumTestResult.Kind.NO_DATA) {
                        ui.setMessage(messageFor(result))
                    }
                }
            }
        }
    }

    /** Pesan rincian untuk tombol Uji koneksi; baris "Uji terakhir" memakai data yang sama. */
    private fun messageFor(result: VelumTestResult): String = when (result.kind) {
        VelumTestResult.Kind.ACTIVE -> app.getString(R.string.test_on)
        VelumTestResult.Kind.OFF -> app.getString(R.string.test_off)
        VelumTestResult.Kind.NO_DATA -> app.getString(R.string.test_no_data_hint)
        VelumTestResult.Kind.FAILED ->
            app.getString(R.string.err_network, result.detail ?: "?")
    }

    /**
     * Memutar endpoint lalu menyambung ulang: penambal untuk jaringan yang tidak
     * meneruskan endpoint WARP tertentu (antarmuka UP, handshake tidak pernah terjadi).
     *
     * Berjalan di `testWorker`. Down+up dilakukan lewat [VelumTunnel.restart] supaya
     * atomik terhadap pelaku lain (layar utama, ubin, pemantau jaringan) — pasangan yang
     * dipanggil terpisah bisa disela `down` milik pengguna dan berakhir menghidupkan
     * tunnel yang baru saja diminta mati.
     *
     * Auto-uji ditekan SEBELUM operasi ini berjalan dan dilepas sesudahnya, lewat [onUi]
     * yang mengantre ke main thread secara FIFO: `testSuppressAuto = true` pasti
     * diproses sebelum applyState(DOWN/UP), dan `= false` pasti sesudahnya. Tanpa itu,
     * uji akan berjalan dua kali — sekali dari sini, sekali lagi dari perubahan status.
     */
    private fun rotateEndpointAndReconnect() {
        if (!prefs.wasUp || !prefs.isRegistered) return // pengguna memutus di tengah jalan
        val current = prefs.effectiveEndpoint
        onUi {
            testSuppressAuto = true
            ui.setTestTextRes(R.string.test_searching)
        }
        try {
            if (!EndpointProbe.rotate(prefs, current)) {
                // Jujur tentang artinya: rotasi bisa gagal karena tidak ada kandidat lain
                // yang terukur, ATAU karena host efektif hasilnya sama dengan yang gagal.
                // Keduanya berarti uji ulang memakai host yang sama — bukan "endpoint lama"
                // seolah tidak ada yang berubah di Prefs.
                VelumLog.w(TAG, "endpoint efektif tidak berpindah; uji ulang memakai host yang sama")
                return
            }
            if (!VelumConnectionContract.reconnect(app, prefs, HANDSHAKE_WAIT_RETRY_MS) { !prefs.wasUp }) {
                VelumLog.w(TAG, "rotasi endpoint tidak menghasilkan handshake")
            }
            onUi { ui.refreshStaticInfo() }
        } catch (e: Exception) {
            VelumLog.w(TAG, "putar endpoint & sambung ulang gagal", e)
        } finally {
            onUi { testSuppressAuto = false }
        }
    }

    /**
     * Membatalkan uji tertunda; [invalidate] juga membatalkan hasil uji yang sedang jalan.
     *
     * Bila ada uji yang batal di tengah jalan, baris "Uji terakhir" dikembalikan ke hasil
     * sah terakhir — sebelumnya ia bisa tertinggal selamanya di "Menunggu data…" karena
     * hasil yang dibatalkan tidak pernah ditampilkan (keluhan nyata di perangkat).
     */
    private fun cancelPendingTest(invalidate: Boolean = true) {
        if (invalidate) testJobId.incrementAndGet()
        pendingTest?.let { main.removeCallbacks(it) }
        pendingTest = null
        if (invalidate && (testInFlight || buttonTestStatusShown)) {
            val wasInFlight = testInFlight
            testInFlight = false
            onUi {
                if (wasInFlight) ui.showTest(prefs.lastTest)
                if (buttonTestStatusShown) {
                    buttonTestStatusShown = false
                    ui.render(VelumTunnel.state)
                }
            }
        }
    }

    private fun setBusy(value: Boolean) {
        busy = value
        onUi { ui.setBusy(value) }
    }

    /** Tampilkan kegagalan dengan pesan yang sesuai jenisnya (bukan sekadar teks exception). */
    private fun fail(resId: Int, e: Exception) {
        onUi {
            setBusy(false)
            applyState(VelumTunnel.state)
            ui.setMessage(messageFor(resId, e))
        }
    }

    private fun messageFor(resId: Int, e: Exception): String {
        val detail = e.message ?: e.javaClass.simpleName
        return when (VelumError.kindOf(e)) {
            VelumError.Kind.NETWORK -> app.getString(R.string.err_network, detail)
            VelumError.Kind.SERVER_REJECT -> app.getString(
                R.string.err_server_reject,
                (e as? VelumApi.HttpError)?.code?.toString() ?: detail
            )
            VelumError.Kind.SERVICE_BLOCKED -> app.getString(R.string.err_connect_closed)
            VelumError.Kind.KEYSTORE -> app.getString(R.string.err_keystore)
            VelumError.Kind.STORAGE -> app.getString(R.string.err_storage)
            VelumError.Kind.UNKNOWN -> app.getString(resId, detail)
        }
    }

    private companion object {
        const val TAG = "Velum"

        /** Batas menunggu handshake sebelum uji trace dijalankan. */
        const val HANDSHAKE_WAIT_MS = VelumConnectionContract.HANDSHAKE_WAIT_MS
        /** Validasi handshake saat connect memakai batas yang sama dengan uji otomatis. */
        const val CONNECT_HANDSHAKE_WAIT_MS = VelumConnectionContract.HANDSHAKE_WAIT_MS
        /**
         * Batas verifikasi handshake untuk SETIAP kandidat pengganti pada rotasi.
         * Sengaja lebih ketat dari [CONNECT_HANDSHAKE_WAIT_MS]: kandidat utama diberi
         * waktu lebih karena pembangunan tunnel pertama + resolusi DNS bisa lambat di
         * jaringan nyata, tetapi kandidat pengganti diuji pada tunnel yang baru dibangun
         * ulang dengan host literal — handshake WireGuard pertama memang seharusnya
         * terjadi dalam hitungan satu-dua detik; lebih dari itu berarti jaringan ini
         * tidak meneruskan UDP ke endpoint itu.
         */
        const val VERIFIED_HANDSHAKE_WAIT_MS = 3000L
        /**
         * Percobaan kedua menunggu lebih lama: tunnel baru saja dibangun ulang dengan
         * endpoint yang berbeda, jadi wajar bila handshake-nya butuh beberapa detik lagi.
         */
        const val HANDSHAKE_WAIT_RETRY_MS = 10000L
        /** Jeda ulangan bila hasil uji negatif padahal tunnel masih UP. */
        const val TEST_RETRY_MS = 1500L
        const val MAX_TEST_ATTEMPTS = 2
        /** Batas rotasi aktual agar koneksi tidak menggantung terlalu lama. */
        const val MAX_ENDPOINT_FALLBACKS = 3
        /** Jeda sebelum registrasi diulang satu kali. */
        const val REGISTER_RETRY_MS = 1500L
    }
}
