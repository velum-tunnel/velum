package com.rollinkxx.velum

/**
 * Rencana migrasi data era polos (berkas `warp`) ke penyimpanan terenkripsi.
 *
 * Murni supaya teruji unit: kesalahan di sini berarti registrasi perangkat
 * hilang, yaitu kegagalan terburuk yang bisa dialami pengguna.
 */
object VelumMigration {

    /**
     * Menyalin nilai bertipe dikenal apa adanya. Tipe lain **diabaikan** dan kunci
     * tidak pernah diubah namanya — memindahkan data itu lebih baik kehilangan
     * satu nilai daripada merusak seluruh berkas.
     */
    fun plan(legacy: Map<String, Any?>): Map<String, Any?> {
        val hasil = LinkedHashMap<String, Any?>()
        for ((kunci, nilai) in legacy) {
            when (nilai) {
                is String -> hasil[kunci] = nilai
                is Boolean -> hasil[kunci] = nilai
                is Int -> hasil[kunci] = nilai
                is Long -> hasil[kunci] = nilai
                is Float -> hasil[kunci] = nilai
                is Set<*> -> if (nilai.all { it is String }) {
                    hasil[kunci] = nilai.filterIsInstance<String>().toSet()
                }
                else -> Unit // tipe tak dikenal: lewati, jangan ditulis sembarangan
            }
        }
        return hasil
    }
}
