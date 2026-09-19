package com.rollinkxx.velum

import java.net.Inet6Address
import java.net.InetAddress
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Pembantu murni: tanpa Android framework, tanpa I/O, tanpa keadaan global.
 * Sengaja dipisah dari Activity/API supaya logika yang rawan salah (parse
 * `cdn-cgi/trace`, pemformatan, pemilihan endpoint) bisa diuji oleh unit test JVM.
 */
object VelumFormat {

    /** Hasil parse `cdn-cgi/trace`. Nilai yang tidak ada = string kosong. */
    class TraceInfo(val warp: String, val colo: String, val ip: String)

    /** Mem-parse teks `cdn-cgi/trace` baris demi baris (`kunci=nilai`). */
    fun parseTrace(text: String): TraceInfo {
        var warp = ""
        var colo = ""
        var ip = ""
        for (line in text.lineSequence()) {
            when {
                line.startsWith("warp=") -> warp = line.removePrefix("warp=").trim()
                line.startsWith("colo=") -> colo = line.removePrefix("colo=").trim()
                line.startsWith("ip=") -> ip = line.removePrefix("ip=").trim()
            }
        }
        return TraceInfo(warp, colo, ip)
    }

    /**
     * Apakah trace membuktikan lalu lintas lewat jalur ingress WARP.
     * Hanya `on` dan `plus` yang dianggap aktif; `off` dan nilai kosong tidak.
     */
    fun isWarpActive(trace: TraceInfo): Boolean = trace.warp == "on" || trace.warp == "plus"

    /**
     * Memformat jumlah byte ke satuan paling masuk akal (B/KB/MB/GB), memakai **koma**
     * sebagai pemisah desimal — sama dengan [formatSeconds] dan konvensi Indonesia.
     *
     * Sebelumnya fungsi ini memakai titik, sehingga baris `Data` menulis "5.1 MB"
     * sementara baris `Boot` pada ringkasan diagnostik yang sama menulis "14,2 detik".
     */
    fun formatBytes(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return String.format(Locale.US, "%.1f KB", kb).replace('.', ',')
        val mb = kb / 1024.0
        if (mb < 1024) return String.format(Locale.US, "%.1f MB", mb).replace('.', ',')
        return String.format(Locale.US, "%.2f GB", mb / 1024.0).replace('.', ',')
    }

    /** Memformat durasi (ms) ke `mm:ss` atau `h:mm:ss` bila lebih dari satu jam. */
    fun formatDuration(ms: Long): String {
        val totalSec = (ms / 1000).coerceAtLeast(0)
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return if (h > 0) {
            String.format(Locale.US, "%d:%02d:%02d", h, m, s)
        } else {
            String.format(Locale.US, "%02d:%02d", m, s)
        }
    }

    /**
     * Durasi dalam detik dengan satu desimal, memakai koma (konvensi Indonesia):
     * `14200` -> `"14,2 detik"`.
     *
     * Dipakai untuk angka yang presisinya penting bagi keputusan, misalnya berapa lama
     * `BootReceiver` menghabiskan anggaran `goAsync()` (batasnya 10 detik, jadi "14 detik"
     * dan "14,2 detik" punya arti berbeda). `formatDuration` tidak dipakai di sini karena
     * membulatkan ke detik penuh.
     */
    fun formatSeconds(ms: Long): String {
        val clamped = ms.coerceAtLeast(0)
        val detik = clamped / 1000.0
        val teks = String.format(Locale.US, "%.1f", detik).replace('.', ',')
        return "$teks detik"
    }

    /**
     * Umur relatif dalam bahasa manusia: `"42 detik lalu"`, `"5 menit lalu"`,
     * `"3 jam lalu"`, `"3 hari lalu"`. Nilai negatif dianggap nol.
     *
     * Baris `Handshake` pada diagnostik SENGAJA tidak dipindah ke fungsi ini: formatnya
     * (`"N detik lalu"`) sudah dikunci oleh `VelumDiagnosticsTest` dan dibaca pengguna
     * sejak lama. Fungsi ini hanya untuk baris baru.
     */
    fun formatAge(sec: Long): String {
        val s = sec.coerceAtLeast(0)
        return when {
            s < 60 -> "$s detik lalu"
            s < 3_600 -> "${s / 60} menit lalu"
            s < 86_400 -> "${s / 3_600} jam lalu"
            else -> "${s / 86_400} hari lalu"
        }
    }

    /** Jam menit lokal (`HH:mm`) untuk cap waktu hasil uji. */
    fun formatClock(epochMillis: Long): String =
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(epochMillis))

    /**
     * Apakah hasil parse ini benar-benar berasal dari keluaran `cdn-cgi/trace`.
     *
     * Portal tawanan (captive portal) dan halaman galat proxy menjawab HTTP 200 berisi
     * HTML, yang diparse menjadi tiga bidang kosong. Tanpa pemeriksaan ini keadaan itu
     * dilaporkan sebagai "Belum aktif" — seolah tunnelnya tidak bekerja, padahal yang
     * sebenarnya terjadi jaringan ini meminta login lebih dulu. Diagnosis salah arah,
     * persis kelas masalah yang dulu membuat "Kesalahan jaringan: Unable to resolve host"
     * menyesatkan pengguna.
     */
    fun isUsable(trace: TraceInfo): Boolean {
        val validWarp = trace.warp in setOf("on", "plus", "off")
        val validColo = trace.colo.matches(Regex("[A-Z0-9]{3}"))
        val validIp = isIpv4(trace.ip) || isIpv6(trace.ip)
        return validWarp || validColo || validIp
    }

    /** Memisahkan host dari "host:port" (aman untuk literal IPv6 dalam kurung siku). */
    fun hostPart(endpoint: String): String {
        if (endpoint.startsWith("[")) return endpoint.substringBefore("]").removePrefix("[")
        return if (endpoint.count { it == ':' } == 1) endpoint.substringBeforeLast(":") else endpoint
    }

    /** Apakah [host] literal IPv4 atau IPv6 (bukan nama domain). */
    fun isIpLiteral(host: String): Boolean {
        return isIpv4(host) || isIpv6(host)
    }

    /**
     * Apakah [s] literal IPv4 SAH: empat oktet 0–255 tanpa nol di depan.
     * Lebih ketat dari [isIpLiteral] (yang hanya membedakan literal vs nama domain) —
     * dipakai untuk data yang masuk penyimpanan (kandidat DoH, endpoint manual).
     */
    fun isIpv4(s: String): Boolean {
        val parts = s.split('.')
        return parts.size == 4 && parts.all { p ->
            p.isNotEmpty() && p.length <= 3 && p.all(Char::isDigit) &&
                !(p.length > 1 && p.startsWith('0')) && p.toInt() <= 255
        }
    }

    /** Literal IPv6 sah tanpa nama zona (`%wlan0`) atau kurung siku. */
    fun isIpv6(s: String): Boolean {
        if (s.isEmpty() || ':' !in s || '%' in s || '[' in s || ']' in s) return false
        return try {
            InetAddress.getByName(s) is Inet6Address
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Menormalkan endpoint dengan validator host dan port yang sama untuk semua sumber.
     * Bila [defaultPort] null, port eksplisit wajib ada. IPv6 telanjang hanya diterima
     * ketika port default tersedia karena selain itu pemisah port ambigu.
     */
    fun normalizeEndpoint(raw: String?, defaultPort: Int?): String? {
        val s = raw?.trim().orEmpty()
        if (s.isEmpty()) return null
        if (s.startsWith("[")) {
            val end = s.indexOf(']')
            if (end <= 1) return null
            val host = s.substring(1, end)
            if (!isIpv6(host)) return null
            val suffix = s.substring(end + 1)
            val port = when {
                suffix.isEmpty() -> defaultPort?.toString() ?: return null
                suffix.startsWith(":") && suffix.length > 1 && ':' !in suffix.drop(1) -> suffix.drop(1)
                else -> return null
            }
            return if (isValidPort(port)) "[$host]:$port" else null
        }

        val colonCount = s.count { it == ':' }
        if (colonCount > 1) {
            val port = defaultPort ?: return null
            return if (isIpv6(s)) "[$s]:$port" else null
        }
        val host: String
        val port: String
        if (colonCount == 1) {
            host = s.substringBeforeLast(':')
            port = s.substringAfterLast(':')
        } else {
            host = s
            port = defaultPort?.toString() ?: return null
        }
        if (!isValidHost(host) || !isValidPort(port)) return null
        return "$host:$port"
    }

    /**
     * Menormalkan masukan endpoint manual pengguna menjadi `host:port` yang bisa dipakai
     * WireGuard, atau `null` bila tidak sah. Diterima: IPv4, nama domain, atau IPv6
     * dalam kurung siku; port wajib 1–65535. IPv6 telanjang (tanpa kurung) ditolak
     * karena ambigu terhadap pemisah port — sejalan dengan `Peer.Builder.parseEndpoint`.
     */
    fun normalizeManualEndpoint(raw: String?): String? {
        return normalizeEndpoint(raw, defaultPort = null)
    }

    /** Port 1–65535 (angka digit murni). */
    private fun isValidPort(p: String): Boolean =
        p.isNotEmpty() && p.length <= 5 && p.all(Char::isDigit) && p.toInt() in 1..65535

    private fun isValidHost(host: String): Boolean {
        if (host.isEmpty()) return false
        val tampakIp = host.all { it.isDigit() || it == '.' }
        return if (tampakIp) isIpv4(host) else isDomainName(host)
    }

    /** Nama domain yang layak jadi host endpoint (label non-kosong, karakter wajar). */
    private fun isDomainName(s: String): Boolean {
        if (s.isEmpty() || s.length > 253) return false
        return s.split('.').all { label ->
            label.isNotEmpty() && !label.startsWith('-') && !label.endsWith('-') &&
                label.all { it.isLetterOrDigit() || it == '-' || it == '_' }
        }
    }
}
