package com.rollinkxx.velum

import java.io.ByteArrayInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class VelumIoTest {
    @Test
    fun readUtf8Bounded_menerimaResponsDiBawahBatas() {
        assertEquals("halo", VelumIo.readUtf8Bounded(ByteArrayInputStream("halo".toByteArray()), 4))
    }

    @Test
    fun readUtf8Bounded_menolakResponsMelebihiBatas() {
        assertThrows(java.io.IOException::class.java) {
            VelumIo.readUtf8Bounded(ByteArrayInputStream("12345".toByteArray()), 4)
        }
    }

    @Test
    fun sanitizeDohCandidates_memvalidasiDedupDanMembatasi() {
        val raw = "1.1.1.1, 999.1.1.1, 1.1.1.1, 2606:4700::1"
        val result = VelumProbePolicy.sanitizeDohCandidates(raw)
        assertEquals(listOf("1.1.1.1"), result)
        assertTrue(VelumProbePolicy.shouldInvalidateSpeed("1.1.1.1:2408", "1.1.1.1:2408"))
    }
}
