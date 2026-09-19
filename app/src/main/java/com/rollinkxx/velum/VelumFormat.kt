package com.rollinkxx.velum

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
                line.startsWith("warp=") -> warp = line.removePrefix("warp=")
                line.startsWith("colo=") -> colo = line.removePrefix("colo=")
                line.startsWith("ip=") -> ip = line.removePrefix("ip=")
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
    fun isUsable(trace: TraceInfo): Boolean =
        trace.warp.isNotEmpty() || trace.colo.isNotEmpty() || trace.ip.isNotEmpty()

    /** Memisahkan host dari "host:port" (aman untuk literal IPv6 dalam kurung siku). */
    fun hostPart(endpoint: String): String {
        if (endpoint.startsWith("[")) return endpoint.substringBefore("]").removePrefix("[")
        return if (endpoint.count { it == ':' } == 1) endpoint.substringBeforeLast(":") else endpoint
    }

    /** Apakah [host] literal IPv4 atau IPv6 (bukan nama domain). */
    fun isIpLiteral(host: String): Boolean {
        // IPv4 harus SAH (oktet 0-255, tanpa nol di depan) — sebelumnya hanya
        // memeriksa "digit dan titik" sehingga "999.999.999.999" dianggap literal
        // dan dipasang sebagai speedEndpoint, yang kemudian gagal di WireGuard
        // tanpa pesan yang jelas. Pakai isIpv4 ketat untuk jalur ini.
        // IPv6 juga diperketat: sebelumnya "::::" dianggap literal karena hanya
        // cek count ':' >1, sehingga bisa dipasang sebagai speedEndpoint dan gagal
        // dengan error kabur. Sekarang pakai isIpv6Strict.
        val isV4 = isIpv4(host)
        val isV6 = isIpv6Strict(host)
        return isV4 || isV6
    }

    /**
     * Apakah [s] literal IPv6 yang masuk akal: minimal 2 titik dua, mengandung
     * hex digit, hanya hex+colon, tidak mengandung ":::" (tiga colon berurutan),
     * tidak hanya colon, dan "::" muncul maksimal sekali.
     * Dipakai untuk membedakan literal vs nama domain (isIpLiteral) dan untuk
     * validasi endpoint manual (normalizeManualEndpoint).
     */
    fun isIpv6Strict(s: String): Boolean {
        if (s.isEmpty()) return false
        if (s.count { it == ':' } < 2) return false
        if (!s.any { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) return false
        if (!s.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' || it == ':' }) return false
        if (s.contains(":::")) return false
        if (s.trim(':').isEmpty()) return false
        // "::" boleh muncul maksimal sekali — "1::2::3" tidak sah
        var idx = s.indexOf("::")
        if (idx >= 0) {
            if (s.indexOf("::", idx + 2) >= 0) return false
        }
        return true
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

    /**
     * Menormalkan masukan endpoint manual pengguna menjadi `host:port` yang bisa dipakai
     * WireGuard, atau `null` bila tidak sah. Diterima: IPv4, nama domain, atau IPv6
     * dalam kurung siku; port wajib 1–65535. IPv6 telanjang (tanpa kurung) ditolak
     * karena ambigu terhadap pemisah port — sejalan dengan `Peer.Builder.parseEndpoint`.
     */
    fun normalizeManualEndpoint(raw: String?): String? {
        val s = raw?.trim().orEmpty()
        if (s.isEmpty()) return null
        if (s.startsWith("[")) {
            val end = s.indexOf("]:").takeIf { it > 0 } ?: return null
            val host = s.substring(1, end)
            val port = s.substring(end + 2)
            // IPv6 ketat: pakai isIpv6Strict supaya "::::" atau ":::" ditolak
            // dengan pesan invalid di dialog, bukan gagal di WireGuard dengan error kabur.
            val v6Sah = isIpv6Strict(host)
            return if (v6Sah && isValidPort(port)) "[$host]:$port" else null
        }
        val idx = s.lastIndexOf(':')
        if (idx <= 0 || idx == s.length - 1) return null
        val host = s.substring(0, idx)
        val port = s.substring(idx + 1)
        if (host.contains(':')) return null // IPv6 telanjang: wajib dibungkus [..]
        // Host yang isinya hanya angka dan titik adalah IP yang hendak ditulis pengguna
        // — wajib lolos uji IPv4 ketat. Bila tidak, ia justru DITERIMA sebagai nama
        // domain digit (mis. "999.1.1.1" sah sebagai label DNS) dan salah ketiknya baru
        // terlihat belakangan sebagai kegagalan DNS — kabar buruk yang ditunda. Contoh
        // yang ditolak di sini: "999.1.1.1", "01.2.3.4", atau satu angka telanjang.
        val tampakIp = host.all { it.isDigit() || it == '.' }
        if (tampakIp) {
            if (!isIpv4(host)) return null
        } else if (!isDomainName(host)) return null
        return if (isValidPort(port)) "$host:$port" else null
    }

    /** Port 1–65535 (angka digit murni). */
    private fun isValidPort(p: String): Boolean =
        p.isNotEmpty() && p.length <= 5 && p.all(Char::isDigit) && p.toInt() in 1..65535

    /** Nama domain yang layak jadi host endpoint (label non-kosong, karakter wajar). */
    private fun isDomainName(s: String): Boolean {
        if (s.isEmpty() || s.length > 253) return false
        return s.split('.').all { label ->
            label.isNotEmpty() && label.length <= 63 &&
                !label.startsWith('-') && !label.endsWith('-') &&
                label.all { it.isLetterOrDigit() || it == '-' || it == '_' }
        }
    }
}
