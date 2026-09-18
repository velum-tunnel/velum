package com.rollinkxx.velum

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VelumMigrationTest {

    @Test
    fun semuaTipeDikenal_tersalin() {
        val lama = linkedMapOf<String, Any?>(
            "private_key" to "kunci",
            "warp_enabled" to true,
            "umur" to 42,
            "epoch" to 1_700_000_000_000L,
            "rasio" to 1.5f,
            "daftar" to setOf("com.a", "com.b")
        )
        val hasil = VelumMigration.plan(lama)
        assertEquals(lama, hasil)
    }

    @Test
    fun tipeTakDikenal_diabaikanBukanMerusak() {
        val hasil = VelumMigration.plan(
            linkedMapOf(
                "private_key" to "kunci",
                "aneh" to Any()
            )
        )
        assertEquals(1, hasil.size)
        assertEquals("kunci", hasil["private_key"])
        assertFalse(hasil.containsKey("aneh"))
    }

    @Test
    fun kunciTidakPernahDiubahNamanya() {
        val hasil = VelumMigration.plan(linkedMapOf("was_up" to true, "endpoint" to "h:2408"))
        assertTrue(hasil.containsKey("was_up"))
        assertTrue(hasil.containsKey("endpoint"))
    }

    @Test
    fun petaKosong_hasilKosong() {
        assertTrue(VelumMigration.plan(emptyMap()).isEmpty())
    }

    @Test
    fun nilaiNull_diabaikan() {
        assertTrue(VelumMigration.plan(linkedMapOf("kosong" to null)).isEmpty())
    }

    @Test
    fun setDenganTipeCampuran_diabaikanTanpaMerusakMigrasi() {
        val hasil = VelumMigration.plan(
            linkedMapOf(
                "private_key" to "kunci",
                "campuran" to setOf<Any>("com.a", 42)
            )
        )
        assertEquals(mapOf("private_key" to "kunci"), hasil)
    }
}
