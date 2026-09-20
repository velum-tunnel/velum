package com.rollinkxx.velum

/**
 * Ringkasan keadaan aplikasi untuk dilampirkan pada laporan gangguan.
 *
 * Sengaja **ramah privasi**: tidak memuat kunci privat, identitas perangkat, token,
 * maupun alamat IP **pengguna**. Yang dikirim hanya keadaan teknis yang aman dibagikan.
 *
 * Batas yang jujur: baris `Endpoint` memang memuat sebuah alamat IP, tetapi itu IP PoP
 * anycast Cloudflare yang dipakai bersama oleh semua pelanggan di wilayah tersebut —
 * bukan pengenal pengguna. Kalimat catatan di dalam ringkasan ditulis mengikuti batas
 * itu, supaya tidak mengklaim sesuatu yang bisa dibantah dengan menunjuk laporannya sendiri.
 *
 * Murni (tanpa Android framework) supaya teruji unit.
 *
 * **Kenapa keadaan internal ikut ditampilkan (2026-09-13, atas persetujuan maintainer).**
 * Maintainer menguji di perangkat **tanpa adb** — tidak ada komputer, tidak ada logcat.
 * Selama ini keadaan yang menentukan benar/tidaknya perilaku konkurensi (niat tersimpan,
 * generasi niat, hidup/matinya pemantau, umur proses, hasil percobaan sambung ulang saat
 * boot) hanya ada di logcat, jadi satu-satunya cara memeriksanya adalah alat yang tidak
 * dimiliki maintainer. Menampilkannya di sini mengubah uji yang tadinya mustahil menjadi
 * uji yang cukup **dilihat**: misalnya "tunnel menyambung sendiri setelah diputus" bisa
 * dikonfirmasi dari baris Niat yang masih `Hidup` sementara Status `Terputus`.
 *
 * Batasnya tetap: yang ditampilkan hanya boolean, angka generasi, dan durasi — tidak ada
 * kunci, token, identitas perangkat, atau IP pengguna.
 */
object VelumDiagnostics {

    data class Snapshot(
        val appVersion: String,
        val state: String,
        val endpoint: String?,
        /** Umur handshake terakhir dalam detik; null bila belum pernah handshake. */
        val handshakeAgeSec: Long?,
        val rxBytes: Long,
        val txBytes: Long,
        val connectedSec: Long,
        val excludedApps: List<String> = emptyList(),
        /** Hasil uji terakhir yang sudah siap dibaca (mis. "Aktif · DC SIN · 15:25"). */
        val lastTest: String? = null,
        /**
         * Niat tersimpan ([Prefs.wasUp]): apakah tunnel DIHARAPKAN hidup. Dibandingkan
         * dengan [state] inilah ketahuan apakah niat bocor — Status `Terputus` sementara
         * Niat `Hidup` berarti sesuatu akan menyambungkannya lagi tanpa diminta.
         */
        val wasUp: Boolean = false,
        /**
         * Generasi niat terakhir ([VelumTunnel.currentIntent]). Angkanya sendiri tidak
         * berarti apa-apa bagi pengguna; yang berarti adalah **naik atau tidaknya** angka
         * itu setelah sebuah aksi. Bila Anda menekan Putuskan lalu angkanya naik dua kali,
         * ada pelaku lain yang ikut bertindak.
         */
        val intentGen: Int = 0,
        /** Apakah pemantau sambung ulang sedang terdaftar ([ReconnectMonitor.isActive]). */
        val monitorActive: Boolean = false,
        /**
         * Umur PROSES ini dalam detik (`Process.getStartElapsedRealtime`). Bila angka ini
         * jauh lebih kecil daripada lamanya perangkat dibiarkan di latar, berarti proses
         * pernah mati dan lahir lagi — dan tunnel ikut mati bersamanya. Ini pengganti
         * `dumpsys`/logcat untuk memeriksa apakah proses foreground-service VPN sempat
         * mati dan lahir kembali.
         */
        val processAgeSec: Long = 0,
        /** Rekaman percobaan sambung ulang otomatis terakhir; null bila belum pernah. */
        val boot: Boot? = null,
        /** Waktu ringkasan dibuat (epoch ms), untuk menghitung umur rekaman [boot]. */
        val nowEpochMs: Long = 0L
    )

    /**
     * Rekaman percobaan sambung ulang otomatis oleh [BootReceiver] (saat boot atau setelah
     * aplikasi diperbarui).
     *
     * @param outcome salah satu dari [BOOT_OK], [BOOT_FAIL], [BOOT_NO_VPN].
     * @param durationMs lama percobaan; inilah angka yang menjawab "apakah `goAsync()`
     *   melewati anggaran 10 detik" tanpa perlu logcat.
     * @param atEpochMs kapan percobaan terjadi, supaya rekaman lama tidak terbaca sebagai
     *   hasil boot barusan.
     */
    data class Boot(val outcome: String, val durationMs: Long, val atEpochMs: Long)

    const val BOOT_OK = "ok"
    const val BOOT_FAIL = "gagal"
    const val BOOT_NO_VPN = "tanpa-izin"

    /**
     * Percobaan dibatalkan karena pelaku lain (layar utama, ubin pengaturan cepat)
     * menyatakan niat yang lebih baru — mis. pengguna menekan Putuskan sementara
     * percobaan sambung ulang boot masih berjalan.
     *
     * Sengaja DIBEDAKAN dari [BOOT_FAIL]: pada keadaan ini tunnel tidak gagal
     * menyambung, ia memang tidak boleh dinyalakan lagi. Mencampur keduanya membuat
     * baris `Boot` menuduh `up()` gagal padahal yang terjadi adalah pembatalan yang
     * benar.
     */
    const val BOOT_SKIPPED = "dibatalkan"

    private val BOOT_OUTCOMES = setOf(BOOT_OK, BOOT_FAIL, BOOT_NO_VPN, BOOT_SKIPPED)

    /** Format simpan: `outcome|durationMs|atEpochMs`. Dipakai [Prefs.bootRecord]. */
    fun encodeBoot(b: Boot): String = "${b.outcome}|${b.durationMs}|${b.atEpochMs}"

    /**
     * Baca balik rekaman tersimpan. Mengembalikan `null` — bukan melempar — untuk masukan
     * cacat: nilai ini datang dari penyimpanan yang bisa saja berasal dari versi aplikasi
     * lama atau berkas yang rusak, dan diagnostik yang crash justru menghilangkan satu-satunya
     * alat yang dipakai mendiagnosis.
     */
    fun decodeBoot(raw: String?): Boot? {
        if (raw.isNullOrEmpty()) return null
        val bagian = raw.split('|')
        if (bagian.size != 3) return null
        if (bagian[0] !in BOOT_OUTCOMES) return null
        val durasi = bagian[1].toLongOrNull() ?: return null
        val waktu = bagian[2].toLongOrNull() ?: return null
        if (durasi < 0 || waktu <= 0) return null
        return Boot(bagian[0], durasi, waktu)
    }

    /** Label outcome dalam bahasa pengguna. */
    fun bootOutcomeLabel(outcome: String): String = when (outcome) {
        BOOT_OK -> "berhasil"
        BOOT_FAIL -> "GAGAL"
        BOOT_NO_VPN -> "dilewati (izin VPN tidak ada)"
        BOOT_SKIPPED -> "dibatalkan (niat pengguna lebih baru)"
        else -> outcome
    }

    /** Teks ringkasan siap salin. */
    fun render(s: Snapshot): String = buildString {
        append("Velum ").append(s.appVersion).append('\n')
        append("Status      : ").append(s.state).append('\n')
        append("Endpoint    : ").append(s.endpoint ?: "-").append('\n')
        append("Handshake   : ").append(
            if (s.handshakeAgeSec == null) "belum ada" else "${s.handshakeAgeSec} detik lalu"
        ).append('\n')
        append("Durasi      : ").append(VelumFormat.formatDuration(s.connectedSec * 1000)).append('\n')
        append("Trafik      : turun ").append(VelumFormat.formatBytes(s.rxBytes))
            .append(" · naik ").append(VelumFormat.formatBytes(s.txBytes)).append('\n')
        append("Uji terakhir: ").append(
            if (s.lastTest.isNullOrEmpty()) "belum ada" else s.lastTest
        ).append('\n')
        append("Dikecualikan: ").append(
            if (s.excludedApps.isEmpty()) "tidak ada" else "${s.excludedApps.size} aplikasi"
        ).append('\n')
        // Keadaan internal. Sengaja ditampilkan permanen di semua varian build (keputusan
        // maintainer 2026-09-13) karena maintainer menguji tanpa adb: tanpa baris-baris ini
        // perilaku konkurensi dan daya tahan proses tidak bisa diperiksa sama sekali dari
        // perangkat. Semuanya boolean/angka/durasi — tidak ada yang mengidentifikasi pengguna.
        append("Niat        : ").append(if (s.wasUp) "Hidup" else "Mati")
            .append(" · aksi ke-").append(s.intentGen).append('\n')
        append("Pemantau    : ").append(if (s.monitorActive) "aktif" else "mati").append('\n')
        append("Proses      : hidup ")
            .append(VelumFormat.formatDuration(s.processAgeSec * 1000)).append('\n')
        val boot = s.boot
        append("Boot        : ")
        if (boot == null) {
            append("belum ada percobaan")
        } else {
            append(VelumFormat.formatSeconds(boot.durationMs))
                .append(" · ").append(bootOutcomeLabel(boot.outcome))
            if (s.nowEpochMs > 0 && boot.atEpochMs in 1..s.nowEpochMs) {
                append(" · ").append(VelumFormat.formatAge((s.nowEpochMs - boot.atEpochMs) / 1000))
            }
        }
        append('\n')
        // Dulu berbunyi "tanpa kunci, identitas perangkat, atau alamat IP" — padahal baris
        // Endpoint di atas jelas memuat sebuah alamat IP. Yang dimaksud memang IP pengguna,
        // bukan IP PoP Cloudflare, tetapi bagi aplikasi yang menawarkan privasi kalimat yang
        // bisa dibantah dengan menunjuk laporannya sendiri adalah kerugian yang tak perlu.
        append("Catatan     : tanpa kunci privat, identitas perangkat, atau alamat IP Anda").append('\n')
    }
}
