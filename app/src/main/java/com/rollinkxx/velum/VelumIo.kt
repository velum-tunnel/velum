package com.rollinkxx.velum

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import kotlin.math.min

/** Primitive I/O jaringan kecil yang tidak bergantung Android dan dapat diuji di JVM. */
object VelumIo {
    /**
     * Membaca UTF-8 paling banyak [maxBytes]. Byte tambahan pertama menghasilkan galat;
     * respons jaringan tidak pernah dialokasikan tanpa batas lewat `readText()`.
     */
    @Throws(IOException::class)
    fun readUtf8Bounded(input: InputStream, maxBytes: Int): String {
        require(maxBytes > 0)
        val out = ByteArrayOutputStream(min(maxBytes, 8192))
        val buffer = ByteArray(8192)
        var total = 0
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            if (read == 0) continue
            total += read
            if (total > maxBytes) throw IOException("respons terlalu besar")
            out.write(buffer, 0, read)
        }
        return out.toString(Charsets.UTF_8.name())
    }

    /**
     * Batas untuk masing-masing tahap connect/read agar satu request tidak dapat memakai
     * lebih dari budget tersisa hanya karena kedua tahap mengalami timeout penuh.
     */
    fun timeoutPerStage(remainingMs: Long, capMs: Int): Int {
        if (remainingMs <= 0L) return 0
        return min(capMs.toLong(), (remainingMs / 2L).coerceAtLeast(1L)).toInt()
    }
}
