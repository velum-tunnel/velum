package com.rollinkxx.velum

import android.os.SystemClock
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Memilih endpoint tunnel tercepat: mengukur RTT koneksi TCP:443 (proksi RTT ke
 * PoP anycast yang sama) secara paralel ke endpoint registrasi + kandidat anycast
 * Cloudflare, memakai pemenang selama 1 jam.
 *
 * Daftar kandidat berasal dari DNS-over-HTTPS bila penyegaran terakhir berhasil
 * ([refreshCandidates]), selain itu daftar statis [VelumUpstream.CANDIDATES]. Bila
 * [Prefs.manualEndpoint] diisi, seluruh pemilihan otomatis di sini dilewati.
 *
 * Best-effort total: kegagalan/keraguan apa pun mempertahankan endpoint lama.
 * Semua fungsi blocking: panggil dari thread latar.
 */
object EndpointProbe {
    private const val TAG = "Velum"
    private const val PROBE_PORT = 443
    private const val CONNECT_TIMEOUT_MS = 2000
    private const val TOTAL_TIMEOUT_SEC = 6L
    private const val FRESH_MS = 3600_000L
    private const val WG_PORT = 2408

    /** DoH untuk penyegaran kandidat anycast; basi setelah 24 jam. */
    private const val DOH_URL =
        "https://cloudflare-dns.com/dns-query?name=engage.cloudflareclient.com&type=A"
    private const val DOH_TIMEOUT_MS = 4000
    private const val DOH_FRESH_MS = 86_400_000L

    /**
     * Menyegarkan [Prefs.speedEndpoint] bila basi (>1 jam). Tidak pernah melempar;
     * kegagalan total mempertahankan nilai lama.
     *
     * Dilewati sepenuhnya bila [Prefs.manualEndpoint] diisi: pengguna sudah memilih
     * sendiri, dan menimpanya dengan hasil proba adalah pelanggaran niat.
     */
    fun refresh(prefs: Prefs) {
        try {
            if (!prefs.manualEndpoint.isNullOrBlank()) {
                VelumLog.d(TAG, "endpoint manual diisi: proba otomatis dilewati")
                return
            }
            // Kandidat DoH disegarkan di jalur ini (murah: gerbang 24 jam sendiri).
            refreshCandidates(prefs)
            if (System.currentTimeMillis() - prefs.speedEndpointAt < FRESH_MS) return
            val ranked = measure(prefs)
            if (ranked.isEmpty()) return // gagal total: jangan sentuh apa pun
            val best = ranked.first()
            val regHost = prefs.endpoint?.let(VelumFormat::hostPart)
            prefs.speedEndpoint = VelumProbePolicy.speedEndpointFor(best, regHost, WG_PORT)
            prefs.speedEndpointAt = System.currentTimeMillis()
            VelumLog.d(TAG, "endpoint tercepat: ${prefs.effectiveEndpoint} (${ranked.size} terukur)")
        } catch (e: Exception) {
            VelumLog.w(TAG, "proba endpoint gagal, pakai endpoint lama", e)
        }
    }

    /**
     * Memilih kandidat **berbeda** dari [exclude] sebagai endpoint pengganti.
     *
     * `refresh()` tidak bisa dipakai untuk keperluan ini: ia memilih pemenang RTT, dan bila
     * pemenang itu justru endpoint yang sedang gagal handshake, hasilnya tidak berubah.
     * Di sini pemenang yang sama dengan endpoint sekarang sengaja dilewati.
     *
     * Keputusannya sendiri ada di [VelumEndpointChoice] (murni, teruji unit); fungsi ini
     * hanya mengukur, memasang hasilnya ke [Prefs], dan melaporkan apa yang benar-benar
     * terjadi. Blocking ≤ ~6 detik. Tidak pernah melempar.
     *
     * @return true HANYA bila endpoint efektif benar-benar berpindah. `false` berarti uji
     *   ulang akan memakai host yang sama, dan pemanggil tidak boleh berkata sebaliknya.
     */
    fun rotate(prefs: Prefs, exclude: String?): Boolean {
        // Endpoint manual mengalahkan rotasi: tidak ada "kandidat lain" yang sah bila
        // pengguna sudah mematok pilihannya sendiri.
        if (!prefs.manualEndpoint.isNullOrBlank()) {
            VelumLog.d(TAG, "endpoint manual diisi: rotasi dilewati")
            return false
        }
        val current = exclude?.let(VelumFormat::hostPart)
        return try {
            val ranked = measure(prefs)
            val d = VelumEndpointChoice.rotate(
                ranked = ranked,
                currentHost = current,
                registrationHost = prefs.endpoint?.let(VelumFormat::hostPart),
                wgPort = WG_PORT
            )
            if (d.host == null) {
                VelumLog.w(TAG, "putar endpoint: tidak ada kandidat lain yang terukur; Prefs tidak disentuh")
                return false
            }
            // Endpoint yang tadinya dianggap terbukti bekerja BARU SAJA gagal handshake,
            // jadi buktinya dilepas apakah ada pengganti maupun tidak — mempertahankannya
            // berarti aplikasi terus mengutamakan host yang baru saja terbukti tidak bisa
            // dipakai. Konsekuensinya (endpoint efektif bisa jatuh ke hasil proba atau ke
            // endpoint registrasi) sudah dihitung di `d.effectiveHost`, jadi laporannya
            // tidak pernah mengklaim perpindahan yang tidak terjadi.
            prefs.workingEndpoint = null
            prefs.speedEndpoint = d.speedEndpoint
            prefs.speedEndpointAt = System.currentTimeMillis()
            if (!d.changed) {
                VelumLog.d(
                    TAG,
                    "putar endpoint: tidak ada perpindahan nyata (efektif ${d.effectiveHost} " +
                        "= yang gagal); bukti endpoint lama tetap dilepas"
                )
                false
            } else {
                VelumLog.d(TAG, "endpoint diputar ke ${prefs.effectiveEndpoint} (${ranked.size} kandidat terukur)")
                true
            }
        } catch (e: Exception) {
            VelumLog.w(TAG, "putar endpoint gagal", e)
            false
        }
    }

    /**
     * Host terurut dari tercepat (endpoint registrasi + kandidat anycast); kosong bila
     * proba gagal total. Blocking ≤ ~6 detik.
     *
     * Diekspos untuk jalur verifikasi handshake di [VelumController]: pengukuran
     * dilakukan SEKALI per rotasi, lalu kandidat dipasang satu per satu lewat
     * [applyCandidate] sampai ada yang lolos handshake. (Jalur `rotate` yang lama
     * mengukur ulang pada SETIAP percobaan fallback — hingga 3 × 6 detik.)
     */
    fun measureRanked(prefs: Prefs): List<String> = measure(prefs)

    /**
     * Penyegaran kandidat anycast dari DNS-over-HTTPS (`cloudflare-dns.com`, skema
     * `application/dns-json`), basi setelah 24 jam. Fallback bertingkat saat gagal:
     * daftar DoH lama tetap dipakai, dan bila belum pernah ada, daftar statis
     * [VelumUpstream.CANDIDATES]. Tidak pernah melempar.
     */
    fun refreshCandidates(prefs: Prefs) {
        try {
            if (System.currentTimeMillis() - prefs.dohCandidatesAt < DOH_FRESH_MS) return
            val ips = fetchDohAddresses().filter(VelumFormat::isIpv4).distinct().take(VelumDoh.MAX_STORED)
            if (ips.isEmpty()) return // gagal total: pertahankan daftar lama/statis
            prefs.dohCandidates = ips.joinToString(",")
            prefs.dohCandidatesAt = System.currentTimeMillis()
            VelumLog.d(TAG, "kandidat anycast disegarkan dari DoH (${ips.size} alamat)")
        } catch (e: Exception) {
            VelumLog.w(TAG, "penyegaran kandidat DoH gagal; pakai daftar lama/statis", e)
        }
    }

    /** Mengambil alamat IPv4 kandidat dari DoH; daftar kosong pada kegagalan apa pun. */
    private fun fetchDohAddresses(): List<String> {
        val conn = (URL(DOH_URL).openConnection() as HttpURLConnection)
        return try {
            conn.connectTimeout = DOH_TIMEOUT_MS
            conn.readTimeout = DOH_TIMEOUT_MS
            conn.setRequestProperty("Accept", "application/dns-json")
            conn.setRequestProperty("Connection", "close")
            conn.useCaches = false
            if (conn.responseCode !in 200..299) return emptyList()
            VelumDoh.parseARecords(conn.inputStream.use { VelumIo.readUtf8Bounded(it, DOH_MAX_BYTES) })
        } catch (_: Exception) {
            emptyList()
        } finally {
            conn.disconnect()
        }
    }

    /** Kandidat anycast yang dipakai proba: hasil DoH bila pernah berhasil, selain itu statis. */
    private fun candidatesFor(prefs: Prefs): List<String> {
        val doh = VelumProbePolicy.sanitizeDohCandidates(prefs.dohCandidates)
        return if (!doh.isNullOrEmpty()) doh else VelumUpstream.CANDIDATES
    }

    /**
     * Memasang [host] hasil [measureRanked] sebagai kandidat aktif di [prefs] TANPA
     * mengukur ulang, memakai aturan keputusan yang persis sama dengan [rotate]
     * ([VelumEndpointChoice]): nama domain tidak pernah menjadi `speedEndpoint`,
     * literal IPv6 dibungkus kurung siku, dan bukti "terbukti bekerja" untuk host
     * sebelumnya selalu dilepas — pemanggil sedang mencari pengganti justru karena
     * host itu gagal handshake.
     *
     * @return true bila pemasangan benar-benar memindahkan endpoint efektif; `false`
     *   berarti kandidat ini tidak mengubah apa pun dan tidak layak diuji handshake.
     */
    fun applyCandidate(prefs: Prefs, host: String, failingHost: String?): Boolean {
        val d = VelumEndpointChoice.rotate(
            ranked = listOf(host),
            currentHost = failingHost,
            registrationHost = prefs.endpoint?.let(VelumFormat::hostPart),
            wgPort = WG_PORT
        )
        if (d.host == null) return false
        prefs.pendingEndpoint = d.speedEndpoint ?: prefs.endpoint
        return d.changed
    }

    /** Promosikan kandidat pending hanya setelah handshake baru terbukti. */
    fun promotePendingCandidate(prefs: Prefs) {
        val candidate = prefs.pendingEndpoint ?: return
        prefs.pendingEndpoint = null
        prefs.workingEndpoint = candidate
        prefs.speedEndpoint = if (candidate == prefs.endpoint) null else candidate
        prefs.speedEndpointAt = System.currentTimeMillis()
    }

    /** Buang kandidat belum terverifikasi dan cache RTT yang sama bila baru saja gagal. */
    fun discardPendingCandidate(prefs: Prefs) {
        val failed = prefs.pendingEndpoint
        prefs.pendingEndpoint = null
        if (VelumProbePolicy.shouldInvalidateSpeed(prefs.speedEndpoint, failed)) {
            prefs.speedEndpoint = null
            prefs.speedEndpointAt = 0L
        }
    }

    /** Invalidasi endpoint RTT yang gagal pada percobaan koneksi utama. */
    fun invalidateFailedEndpoint(prefs: Prefs, failed: String?) {
        if (VelumProbePolicy.shouldInvalidateSpeed(prefs.speedEndpoint, failed)) {
            prefs.speedEndpoint = null
            prefs.speedEndpointAt = 0L
        }
        if (prefs.workingEndpoint == failed) prefs.workingEndpoint = null
    }

    /** Host terurut dari tercepat; kosong bila semua gagal. Blocking ≤ ~6 detik. */
    private fun measure(prefs: Prefs): List<String> {
        val hosts = LinkedHashSet<String>()
        prefs.endpoint?.let(VelumFormat::hostPart)?.takeIf { it.isNotEmpty() }?.let { hosts.add(it) }
        hosts.addAll(candidatesFor(prefs))
        if (hosts.isEmpty()) return emptyList()
        val tasks = hosts.map { host -> Callable { host to tcpRttMs(host) } }
        val executor = Executors.newFixedThreadPool(hosts.size) { r ->
            Thread(r, "velum-probe").apply { isDaemon = true }
        }
        return try {
            executor.invokeAll(tasks, TOTAL_TIMEOUT_SEC, TimeUnit.SECONDS)
                .mapNotNull {
                    try {
                        if (it.isCancelled) null else it.get()
                    } catch (_: Exception) {
                        null
                    }
                }
                .filter { it.second >= 0 }
                .sortedBy { it.second }
                .map { it.first }
        } catch (_: Exception) {
            emptyList()
        } finally {
            executor.shutdownNow()
        }
    }

    /** RTTms koneksi TCP, atau -1 bila gagal. */
    private fun tcpRttMs(host: String): Long {
        val start = SystemClock.elapsedRealtime()
        return try {
            Socket().use { s -> s.connect(InetSocketAddress(host, PROBE_PORT), CONNECT_TIMEOUT_MS) }
            SystemClock.elapsedRealtime() - start
        } catch (_: Exception) {
            -1
        }
    }

    private const val DOH_MAX_BYTES = 64 * 1024

}
