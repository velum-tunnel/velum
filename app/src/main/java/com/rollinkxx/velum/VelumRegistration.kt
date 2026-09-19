package com.rollinkxx.velum

import org.json.JSONObject
import java.io.IOException

/**
 * Parse & validasi respons registrasi perangkat (`POST /reg`).
 *
 * Murni (tanpa Android framework) supaya teruji unit. Alasannya: bila bentuk
 * respons upstream berubah atau sebuah field penting kosong, kegagalannya harus
 * muncul di sini dengan pesan yang jelas — bukan sebagai registrasi "sukses"
 * berdata kosong yang baru terasa saat tunnel gagal dibentuk dan pengguna tidak
 * tahu penyebabnya.
 */
object VelumRegistration {

    private const val WG_PORT = 2408

    /** Hasil registrasi yang sudah divalidasi. */
    data class Result(
        val id: String,
        val token: String,
        val addressV4: String,
        /** Alamat IPv6 bila ada; null bila tidak dikirim upstream. */
        val addressV6: String?,
        val peerPublicKey: String,
        /** Endpoint siap pakai (selalu berbentuk `host:port`). */
        val endpoint: String
    )

    /** Respons registrasi tidak bisa dipakai: field wajib hilang atau kosong. */
    class BadResponse(message: String) : IOException(message)

    @Throws(BadResponse::class)
    fun parse(json: String): Result {
        val root = try {
            JSONObject(json)
        } catch (e: Exception) {
            throw BadResponse("respons bukan JSON: ${e.message}")
        }
        val config = root.optJSONObject("config") ?: throw BadResponse("respons tanpa objek config")
        val iface = config.optJSONObject("interface") ?: throw BadResponse("config tanpa objek interface")
        val addresses = iface.optJSONObject("addresses") ?: throw BadResponse("interface tanpa objek addresses")
        val peers = config.optJSONArray("peers") ?: throw BadResponse("config tanpa array peers")
        val peer = peers.optJSONObject(0) ?: throw BadResponse("config tanpa peer pertama")
        return Result(
            id = root.wajib("id"),
            token = root.wajib("token"),
            addressV4 = addresses.wajib("v4"),
            addressV6 = addresses.optString("v6", "").takeIf { it.isNotBlank() },
            peerPublicKey = peer.wajib("public_key"),
            endpoint = normalizeEndpoint(peer.optJSONObject("endpoint")?.optString("host", ""))
        )
    }

    /**
     * Bentuk akhir endpoint: host kosong → endpoint cadangan; host tanpa port →
     * dilengkapi port WireGuard, karena `Peer.Builder.parseEndpoint` menolak host
     * yang tidak berport.
     */
    fun normalizeEndpoint(host: String?): String {
        val trimmed = host?.trim().orEmpty()
        if (trimmed.isEmpty()) return VelumUpstream.DEFAULT_ENDPOINT
        return VelumFormat.normalizeEndpoint(trimmed, WG_PORT)
            ?: throw BadResponse("endpoint registrasi tidak sah")
    }

    private fun JSONObject.wajib(key: String): String {
        val nilai = optString(key, "").trim()
        if (nilai.isEmpty()) throw BadResponse("field wajib '$key' hilang atau kosong")
        return nilai
    }
}
