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
            endpoint = validateEndpoint(
                normalizeEndpoint(peer.optJSONObject("endpoint")?.optString("host", ""))
            )
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
        val isV6 = trimmed.contains(":") && trimmed.count { it == ':' } > 1
        val sudahBerport = if (trimmed.startsWith("[")) {
            trimmed.contains("]:")
        } else {
            trimmed.count { it == ':' } == 1 && !isV6
        }
        if (sudahBerport) return trimmed
        val hostPart = if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
            trimmed
        } else if (isV6) {
            "[$trimmed]"
        } else {
            trimmed
        }
        return "$hostPart:$WG_PORT"
    }

    /** Menolak endpoint malformed sebelum disimpan sebagai profil terdaftar. */
    @Throws(BadResponse::class)
    fun validateEndpoint(endpoint: String): String {
        val trimmed = endpoint.trim()
        val host: String
        val portText: String
        if (trimmed.startsWith("[")) {
            val closing = trimmed.indexOf(']')
            if (closing <= 1 || closing + 1 >= trimmed.length || trimmed[closing + 1] != ':') {
                throw BadResponse("endpoint IPv6 tidak valid")
            }
            host = trimmed.substring(1, closing)
            portText = trimmed.substring(closing + 2)
            if (!host.matches(Regex("[0-9A-Fa-f:.]+")) || !host.contains(':')) {
                throw BadResponse("host IPv6 tidak valid")
            }
        } else {
            val separator = trimmed.lastIndexOf(':')
            if (separator <= 0 || separator == trimmed.lastIndex) {
                throw BadResponse("endpoint harus memiliki host dan port")
            }
            host = trimmed.substring(0, separator)
            portText = trimmed.substring(separator + 1)
            if (host.contains(':') || !host.matches(Regex("[A-Za-z0-9](?:[A-Za-z0-9.-]*[A-Za-z0-9])?"))) {
                throw BadResponse("host endpoint tidak valid")
            }
        }
        val port = portText.toIntOrNull() ?: throw BadResponse("port endpoint bukan angka")
        if (port !in 1..65535) throw BadResponse("port endpoint di luar rentang")
        return trimmed
    }

    private fun JSONObject.wajib(key: String): String {
        val nilai = optString(key, "").trim()
        if (nilai.isEmpty()) throw BadResponse("field wajib '$key' hilang atau kosong")
        return nilai
    }
}
