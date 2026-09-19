package com.rollinkxx.velum

import android.os.SystemClock
import com.wireguard.crypto.KeyPair
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

/**
 * Klien minimal API registrasi Cloudflare WARP.
 * Hanya memakai HttpURLConnection + org.json bawaan Android agar tidak menambah dependensi.
 * Semua fungsi bersifat blocking: panggil dari thread latar.
 */
object VelumApi {
    /** Kode & pesan HTTP yang gagal, supaya UI bisa membedakan penolakan klien. */
    class HttpError(val code: Int, message: String) : IOException(message)

    private const val BASE = VelumUpstream.BASE
    private const val USER_AGENT = VelumUpstream.USER_AGENT
    const val DEFAULT_ENDPOINT = VelumUpstream.DEFAULT_ENDPOINT

    /**
     * Matikan keep-alive HTTP. Android tidak memindahkan soket yang SUDAH terbuka ke VPN,
     * jadi koneksi yang tersisa dari sebelum tunnel aktif akan dipakai ulang dan keluar
     * langsung ke internet — hasil `cdn-cgi/trace` lalu salah (`warp=off`) meski tunnel UP.
     */
    init {
        runCatching { System.setProperty("http.keepAlive", "false") }
    }

    /** Mendaftarkan perangkat baru dan menyimpan hasilnya ke [prefs]. */
    @Throws(IOException::class)
    fun register(prefs: Prefs) {
        val keyPair = KeyPair()
        val body = JSONObject()
            .put("key", keyPair.publicKey.toBase64())
            .put("install_id", "")
            .put("fcm_token", "")
            .put("tos", isoNow())
            .put("model", "Android")
            .put("type", "Android")
            .put("locale", "id_ID")
            .put("serial_number", UUID.randomUUID().toString())
            .put("warp_enabled", true)

        val hasil = VelumRegistration.parse(request("POST", "$BASE/reg", body.toString(), null))

        // Satu transaksi, bukan tujuh tulisan terpisah: proses yang mati di tengah
        // penulisan dulu bisa meninggalkan kunci privat baru bercampur endpoint lama
        // (lihat Prefs.saveRegistration).
        prefs.saveRegistration(hasil, keyPair.privateKey.toBase64())
    }

    /**
     * Menyembuhkan akun lama yang terdaftar tanpa flag WARP (era sebelum registrasi
     * memakai warp_enabled): GET /reg/{id} → bila account.warp_enabled == false → hapus
     * registrasi lama di server lalu daftar ulang (otomatis memakai flag baru).
     * Postur fail-safe: hasil tak meyakinkan atau error jaringan → tidak melakukan apa pun
     * (pengguna tetap bisa Daftar ulang manual). Dipanggil dari thread latar.
     */
    fun ensureWarpEnabled(prefs: Prefs) {
        val id = prefs.deviceId ?: return
        val token = prefs.token ?: return
        val enabled: Boolean? = try {
            val json = JSONObject(request("GET", "$BASE/reg/$id", null, token))
            val account = json.optJSONObject("account")
            if (account != null && account.has("warp_enabled")) {
                account.optBoolean("warp_enabled")
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
        when (enabled) {
            true -> prefs.warpEnabled = true
            false -> {
                // Daftar baru dahulu dan simpan secara atomik. Kredensial lama tidak boleh
                // dihapus sebelum penggantinya tervalidasi serta durabel; DELETE lama hanya
                // cleanup best-effort setelah transaksi baru berhasil.
                register(prefs)
                try {
                    request("DELETE", "$BASE/reg/$id", null, token)
                } catch (_: IOException) {
                    // Registrasi baru sudah aktif; akun lama dapat dibersihkan kemudian.
                }
            }
            null -> Unit // tak meyakinkan: jangan sentuh apa pun
        }
    }

    /** Menghapus registrasi di server (best-effort) lalu membersihkan penyimpanan lokal. */
    fun unregister(prefs: Prefs) {
        val id = prefs.deviceId
        val token = prefs.token
        if (!id.isNullOrEmpty() && !token.isNullOrEmpty()) {
            try {
                request("DELETE", "$BASE/reg/$id", null, token)
            } catch (_: IOException) {
                // Abaikan; data lokal tetap dibersihkan.
            }
        }
        prefs.clear()
    }

    /**
     * Host uji trace; yang kedua cadangan, karena sebagian jaringan memblokir salah satu
     * host Cloudflare sehingga uji gagal padahal tunnelnya sehat.
     */
    private val TRACE_URLS = listOf(
        "https://www.cloudflare.com/cdn-cgi/trace",
        "https://one.one.one.one/cdn-cgi/trace"
    )

    /** Anggaran total: cadangan tidak boleh menambah waktu tunggu tanpa batas. */
    private const val TRACE_BUDGET_MS = 7_000L

    /** Mengambil dan mem-parse cdn-cgi/trace (mengikuti jalur koneksi saat ini). Blocking. */
    @Throws(IOException::class)
    fun fetchTrace(): VelumFormat.TraceInfo {
        val started = SystemClock.elapsedRealtime()
        var lastError: IOException? = null
        for (url in TRACE_URLS) {
            val remaining = TRACE_BUDGET_MS - (SystemClock.elapsedRealtime() - started)
            if (remaining <= 0L) break
            try {
                return fetchTraceFrom(url, remaining)
            } catch (e: IOException) {
                lastError = e
                // Gagal cepat (mis. host diblokir DNS) → masih ada waktu untuk cadangan.
                // Gagal karena menggantung sampai batas waktu → cadangan hanya menambah
                // waktu tunggu, jadi dihentikan saja.
                if (SystemClock.elapsedRealtime() - started >= TRACE_BUDGET_MS) break
            }
        }
        throw lastError ?: IOException("uji trace gagal")
    }

    @Throws(IOException::class)
    private fun fetchTraceFrom(url: String, remainingMs: Long): VelumFormat.TraceInfo {
        val conn = (URL(url).openConnection() as HttpURLConnection)
        try {
            val perStageTimeout = VelumIo.timeoutPerStage(remainingMs, TRACE_TIMEOUT_MS)
            if (perStageTimeout <= 0) throw IOException("anggaran uji trace habis")
            conn.connectTimeout = perStageTimeout
            conn.readTimeout = perStageTimeout
            conn.setRequestProperty("User-Agent", USER_AGENT)
            // Soket baru untuk setiap uji: jangan pakai koneksi dari sebelum tunnel aktif.
            conn.setRequestProperty("Connection", "close")
            conn.useCaches = false
            val code = conn.responseCode
            if (code !in 200..299) throw IOException("HTTP $code")
            val text = conn.inputStream.use { VelumIo.readUtf8Bounded(it, TRACE_MAX_BYTES) }
            val trace = VelumFormat.parseTrace(text)
            // HTTP 200 belum berarti isinya trace: portal tawanan menjawab 200 dengan HTML.
            // Dibiarkan, hasilnya "Belum aktif" — menuduh tunnel padahal jaringan yang
            // meminta login. Dilempar sebagai IOException agar host cadangan dicoba, dan
            // bila keduanya gagal pengguna melihat alasan yang benar.
            if (!VelumFormat.isUsable(trace)) {
                throw IOException("respons bukan keluaran trace (jaringan ini mungkin meminta login)")
            }
            return trace
        } finally {
            conn.disconnect()
        }
    }

    @Throws(IOException::class)
    private fun request(method: String, url: String, body: String?, bearer: String?): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = method
            conn.connectTimeout = 15000
            conn.readTimeout = 15000
            // Header API hidup di satu tempat (VelumUpstream.API_HEADERS) dan dijaga
            // unit test — jangan menambah header registrasi di sini secara terpisah.
            for ((nama, nilai) in VelumUpstream.API_HEADERS) conn.setRequestProperty(nama, nilai)
            conn.setRequestProperty("Connection", "close")
            conn.useCaches = false
            if (bearer != null) conn.setRequestProperty("Authorization", "Bearer $bearer")
            if (body != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.use { VelumIo.readUtf8Bounded(it, API_MAX_BYTES) } ?: ""
            if (code !in 200..299) {
                throw if (VelumUpstream.isClientRejected(code)) {
                    HttpError(code, "HTTP $code")
                } else {
                    IOException("HTTP $code")
                }
            }
            return text
        } finally {
            conn.disconnect()
        }
    }

    private fun isoNow(): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        fmt.timeZone = TimeZone.getTimeZone("UTC")
        return fmt.format(Date())
    }

    private const val TRACE_TIMEOUT_MS = 4_000
    private const val TRACE_MAX_BYTES = 64 * 1024
    private const val API_MAX_BYTES = 256 * 1024
}
