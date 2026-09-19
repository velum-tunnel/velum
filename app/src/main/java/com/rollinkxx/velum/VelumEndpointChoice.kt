package com.rollinkxx.velum

/**
 * Keputusan pemilihan endpoint — **murni**: tanpa Android, tanpa I/O, tanpa [Prefs].
 *
 * Sengaja dipisah dari [EndpointProbe]. Alasannya bukan kerapian: keputusan inilah yang
 * menentukan apakah aplikasi boleh mengklaim "endpoint sudah diganti", dan klaim itu
 * pernah salah (rotasi melaporkan kegagalan sambil tetap mengubah endpoint efektif,
 * sehingga pemanggilnya menguji ulang host yang sama dan mencatat "dilanjutkan dengan
 * endpoint lama"). Logika yang bisa salah seperti ini harus bisa diuji di JVM tanpa
 * soket, tanpa perangkat, dan tanpa menunggu 6 detik anggaran proba.
 *
 * Semua masukan adalah data yang sudah dikumpulkan [EndpointProbe.measure]; fungsi ini
 * tidak memutuskan host mana yang cepat, hanya apa arti hasilnya.
 */
object VelumEndpointChoice {

    /**
     * Hasil keputusan rotasi endpoint.
     *
     * @property host kandidat terpilih (host tanpa port), atau `null` bila daftar terukur
     *   tidak menawarkan apa pun yang berbeda dari yang sedang gagal.
     * @property speedEndpoint nilai yang layak dipasang ke `Prefs.speedEndpoint`, atau
     *   `null` bila [host] bukan literal IPv4. Nama domain sengaja tidak dipasang di
     *   sana: ia harus di-resolve setiap kali tunnel dibangun (dan resolusi itu punya
     *   anggaran retry sendiri di backend), jadi ia bukan "endpoint cepat hasil proba".
     * @property effectiveHost host yang benar-benar terpakai SETELAH pemasangan, dihitung
     *   dengan aturan `Prefs.effectiveEndpoint` dan dengan asumsi `workingEndpoint`
     *   dikosongkan — bukti "terbukti bekerja" baru saja dibantah oleh gagalnya handshake.
     * @property changed apakah perpindahannya NYATA: [effectiveHost] ada dan berbeda dari
     *   host yang sedang gagal. Hanya bila inilah pemanggil boleh berkata "endpoint
     *   diganti" dan menyambung ulang.
     */
    data class Decision(
        val host: String?,
        val speedEndpoint: String?,
        val effectiveHost: String?,
        val changed: Boolean
    )

    /**
     * Memilih pengganti untuk [currentHost] dari [ranked] (sudah terurut tercepat dulu).
     *
     * @param ranked host terurut hasil pengukuran; boleh kosong bila proba gagal total.
     * @param currentHost host yang sedang dipakai dan baru saja gagal handshake; `null`
     *   bila pemanggil tidak tahu (semua kandidat lalu dianggap layak).
     * @param registrationHost host dari endpoint registrasi — tujuan jatuh tempo bila
     *   tidak ada `speedEndpoint` yang dipasang.
     * @param wgPort port WireGuard yang dipakai menyusun `speedEndpoint`.
     */
    fun rotate(
        ranked: List<String>,
        currentHost: String?,
        registrationHost: String?,
        wgPort: Int
    ): Decision {
        val next = ranked.firstOrNull { it != currentHost }
            ?: return Decision(host = null, speedEndpoint = null, effectiveHost = currentHost, changed = false)
        val speed = if (VelumFormat.isIpLiteral(next)) {
            // isIpLiteral sudah pakai isIpv6Strict, jadi cek IPv6 konsisten
            val isV6 = VelumFormat.isIpv6Strict(next.trim('[', ']'))
            val safeNext = if (isV6 && !next.startsWith("[")) "[$next]" else next
            "$safeNext:$wgPort"
        } else null
        // workingEndpoint dianggap sudah dikosongkan, jadi urutan jatuhnya adalah
        // speedEndpoint lalu endpoint registrasi — sama dengan Prefs.effectiveEndpoint.
        val effective = speed?.let(VelumFormat::hostPart) ?: registrationHost
        return Decision(
            host = next,
            speedEndpoint = speed,
            effectiveHost = effective,
            changed = effective != null && effective != currentHost
        )
    }
}
