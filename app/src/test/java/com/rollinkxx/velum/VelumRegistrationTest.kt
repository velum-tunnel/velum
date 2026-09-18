package com.rollinkxx.velum

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pengujian validasi respons registrasi. Lapisan ini yang mencegah registrasi
 * "sukses" berdata kosong — kegagalan terburuk karena pengguna hanya melihat
 * aplikasi tidak bisa menyambung tanpa sebab yang jelas.
 */
class VelumRegistrationTest {

    // Catatan: raw string Kotlin ("""…""") tidak memproses escape \", jadi JSON
    // dirangkai dengan string biasa.
    private fun jsonLengkap(
        host: String = "engage.cloudflareclient.com:2408",
        v6: String? = "2606:4700:110:8c7a:1c8:d6d1:8a1b:df9f"
    ): String = "{\"id\":\"dev-123\",\"token\":\"tok-abc\"," +
        "\"config\":{\"interface\":{\"addresses\":{\"v4\":\"172.16.0.2\"" +
        (if (v6 == null) "" else ",\"v6\":\"$v6\"") +
        "}},\"peers\":[{\"public_key\":\"kunci-peer\",\"endpoint\":{\"host\":\"$host\"}}]}}"

    @Test
    fun responsLengkap_terparse() {
        val r = VelumRegistration.parse(jsonLengkap())
        assertEquals("dev-123", r.id)
        assertEquals("tok-abc", r.token)
        assertEquals("172.16.0.2", r.addressV4)
        assertEquals("2606:4700:110:8c7a:1c8:d6d1:8a1b:df9f", r.addressV6)
        assertEquals("kunci-peer", r.peerPublicKey)
        assertEquals("engage.cloudflareclient.com:2408", r.endpoint)
    }

    @Test
    fun tanpaV6_addressV6Null() {
        assertNull(VelumRegistration.parse(jsonLengkap(v6 = null)).addressV6)
    }

    @Test
    fun endpointKosong_memakaiCadangan() {
        assertEquals(
            VelumUpstream.DEFAULT_ENDPOINT,
            VelumRegistration.parse(jsonLengkap(host = "")).endpoint
        )
    }

    @Test
    fun hostTanpaPort_dilengkapiPortWireGuard() {
        assertEquals(
            "162.159.192.1:2408",
            VelumRegistration.parse(jsonLengkap(host = "162.159.192.1")).endpoint
        )
    }

    @Test
    fun hostBerport_dibiarkanUtuh() {
        assertEquals(
            "188.114.97.1:2408",
            VelumRegistration.parse(jsonLengkap(host = "188.114.97.1:2408")).endpoint
        )
    }

    @Test(expected = VelumRegistration.BadResponse::class)
    fun portBukanAngka_ditolak() {
        VelumRegistration.parse(jsonLengkap(host = "example.com:abc"))
    }

    @Test(expected = VelumRegistration.BadResponse::class)
    fun portDiLuarRentang_ditolak() {
        VelumRegistration.parse(jsonLengkap(host = "example.com:65536"))
    }

    @Test
    fun endpointIpv6Valid_diterima() {
        assertEquals(
            "[2001:db8::1]:2408",
            VelumRegistration.parse(jsonLengkap(host = "[2001:db8::1]:2408")).endpoint
        )
    }

    @Test
    fun tanpaObjekEndpoint_memakaiCadangan() {
        val json = """{"id":"a","token":"b",
            "config":{"interface":{"addresses":{"v4":"172.16.0.2"}},
            "peers":[{"public_key":"pk"}]}}"""
        assertEquals(VelumUpstream.DEFAULT_ENDPOINT, VelumRegistration.parse(json).endpoint)
    }

    @Test
    fun normalisasiEndpoint_ipv6DalamKurungSiku() {
        assertEquals("[fd00::1]:2408", VelumRegistration.normalizeEndpoint("[fd00::1]"))
        assertEquals("[fd00::1]:2408", VelumRegistration.normalizeEndpoint("[fd00::1]:2408"))
        assertEquals("[fd00::1]:2408", VelumRegistration.normalizeEndpoint("fd00::1"))
        assertEquals("1.2.3.4:2408", VelumRegistration.normalizeEndpoint("1.2.3.4"))
        assertEquals("host:2408", VelumRegistration.normalizeEndpoint("  host  "))
    }

    @Test(expected = VelumRegistration.BadResponse::class)
    fun tokenKosong_ditolak() {
        VelumRegistration.parse(jsonLengkap().replace("\"token\":\"tok-abc\"", "\"token\":\"\""))
    }

    @Test(expected = VelumRegistration.BadResponse::class)
    fun tanpaConfig_ditolak() {
        VelumRegistration.parse("""{"id":"a","token":"b"}""")
    }

    @Test(expected = VelumRegistration.BadResponse::class)
    fun tanpaPeer_ditolak() {
        VelumRegistration.parse(
            """{"id":"a","token":"b",
                "config":{"interface":{"addresses":{"v4":"172.16.0.2"}},"peers":[]}}"""
        )
    }

    @Test(expected = VelumRegistration.BadResponse::class)
    fun tanpaAlamatV4_ditolak() {
        VelumRegistration.parse(
            """{"id":"a","token":"b",
                "config":{"interface":{"addresses":{}},
                "peers":[{"public_key":"pk","endpoint":{"host":"h:2408"}}]}}"""
        )
    }

    @Test
    fun bukanJson_pesanKesalahannyaJelas() {
        val e = runCatching { VelumRegistration.parse("<html>") }.exceptionOrNull()
        assertTrue("harus BadResponse, dapat: $e", e is VelumRegistration.BadResponse)
        assertTrue(e!!.message!!.contains("respons bukan JSON"))
    }
}
