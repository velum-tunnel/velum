package com.rollinkxx.velum

import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Klasifikasi kegagalan jaringan/API — murni tanpa Android framework supaya teruji unit.
 *
 * Tujuannya agar pesan ke pengguna tidak generik: "Layanan menolak klien ini (HTTP 403)"
 * jelas berbeda perlakuannya dari "Kesalahan jaringan: timeout".
 */
object VelumError {

    enum class Kind {
        /** Jaringan putus/lambat — layak dicoba ulang. */
        NETWORK,

        /** Upstream menolak klien ini — mengulang percuma, butuh pembaruan aplikasi. */
        SERVER_REJECT,

        /**
         * Penyimpanan terenkripsi perangkat tidak bisa dipakai — bukan salah jaringan
         * dan bukan salah server; kunci privat tidak akan disimpan tanpa enkripsi,
         * jadi pengguna diminta memperbaiki keystore lalu mendaftar ulang.
         */
        KEYSTORE,

        /**
         * Sistem menolak/menutup layanan VPN milik aplikasi (bukan salah jaringan).
         *
         * Sejak `targetSdk` 36 izin layanan latar depan diperketat, dan Android bisa
         * menolak `startForeground` atau menutup layanan yang sudah jalan. Petunjuknya
         * hanya berupa teks dari framework/library, jadi klasifikasi ini bersifat
         * heuristik — tujuannya mengganti pesan "Kesalahan jaringan" yang menyesatkan
         * dengan langkah yang benar-benar bisa dicoba pengguna.
         */
        SERVICE_BLOCKED,

        /** Tidak terklasifikasi — tampilkan pesan bawaan pemanggil. */
        UNKNOWN
    }

    /** Potongan teks (huruf kecil) yang menandai penolakan layanan latar depan/VPN. */
    private val SERVICE_MARKERS = listOf(
        "foreground service",
        "foregroundservice",
        "specialuse",
        "startforeground",
        "background start not allowed",
        "vpnservice"
    )

    fun kindOf(e: Throwable?): Kind = when (e) {
        null -> Kind.UNKNOWN
        // Harus sebelum IOException: HttpError adalah IOException juga.
        is VelumApi.HttpError ->
            if (VelumUpstream.isClientRejected(e.code)) Kind.SERVER_REJECT else Kind.UNKNOWN
        // Juga sebelum IOException: kegagalan keystore bukanlah masalah jaringan, dan
        // melaporkannya sebagai "Kesalahan jaringan" menyuruh pengguna mengganti Wi-Fi
        // untuk masalah yang tidak ada hubungannya dengan jaringan.
        is KeystoreUnavailableException -> Kind.KEYSTORE
        // BadResponse = respons server tidak sesuai format yang diharapkan — bukan
        // masalah jaringan, jangan di-retry sebagai NETWORK. Sebelumnya ia jatuh ke
        // IOException -> NETWORK karena BadResponse extends IOException, sehingga
        // registerWithRetry mengulang sekali untuk kesalahan yang tidak akan sembuh
        // dengan retry.
        is VelumRegistration.BadResponse -> Kind.UNKNOWN
        is UnknownHostException, is SocketTimeoutException, is ConnectException -> Kind.NETWORK
        // Penanda layanan diperiksa SEBELUM memutuskan "ini masalah jaringan".
        // Penolakan layanan latar depan bisa datang terbungkus IOException (mis. dari
        // lapisan I/O milik library), dan `is IOException -> NETWORK` yang dievaluasi lebih
        // dulu membuatnya dilaporkan sebagai "Kesalahan jaringan" — persis pesan
        // menyesatkan yang klasifikasi ini dibuat untuk mencegahnya. Tiga tipe di baris
        // atas tetap NETWORK tanpa syarat: ketiganya tidak pernah berarti penolakan layanan.
        is IOException -> if (looksLikeServiceBlock(e)) Kind.SERVICE_BLOCKED else Kind.NETWORK
        else -> if (looksLikeServiceBlock(e)) Kind.SERVICE_BLOCKED else Kind.UNKNOWN
    }

    /**
     * Apakah kegagalan tampak berasal dari penolakan layanan (bukan jaringan).
     * Sengaja hanya melihat tipe/teks, tanpa Android framework, agar bisa diuji unit.
     */
    private fun looksLikeServiceBlock(e: Throwable): Boolean {
        val text = buildString {
            append(e.javaClass.simpleName)
            append(' ')
            append(e.message.orEmpty())
            var cause = e.cause
            var depth = 0
            while (cause != null && depth < 3) { // rantai penyebab dibatasi agar murah
                append(' ')
                append(cause.javaClass.simpleName)
                append(' ')
                append(cause.message.orEmpty())
                cause = cause.cause
                depth++
            }
        }.lowercase()
        return SERVICE_MARKERS.any { it in text }
    }
}
