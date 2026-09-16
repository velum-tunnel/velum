package com.rollinkxx.velum

import java.util.concurrent.atomic.AtomicBoolean

/** Pemilik tunggal claim recovery; check-then-set tidak boleh dipakai pada callback jaringan. */
class VelumRecoveryClaim {
    private val claimed = AtomicBoolean(false)
    fun tryClaim(): Boolean = claimed.compareAndSet(false, true)
    /** Pelepasan idempoten untuk semua jalur cleanup. */
    fun release() { claimed.set(false) }
}
