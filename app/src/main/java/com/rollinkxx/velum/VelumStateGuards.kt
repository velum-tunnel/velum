package com.rollinkxx.velum

import java.util.concurrent.atomic.AtomicInteger

/** Tracks DOWN transitions requested by the app so backend callbacks are classified safely. */
internal class ExpectedDownTransitions {
    private val count = AtomicInteger(0)

    fun expectIfUp(isUp: Boolean) {
        if (isUp) count.incrementAndGet()
    }

    fun consume(): Boolean {
        while (true) {
            val current = count.get()
            if (current <= 0) return false
            if (count.compareAndSet(current, current - 1)) return true
        }
    }

    fun cancel() {
        count.updateAndGet { (it - 1).coerceAtLeast(0) }
    }

    internal fun pendingCount(): Int = count.get()
}

/** Pure UI decision used when traffic resumes after a stale-connection warning. */
internal object VelumTrafficDecision {
    fun trafficResumed(rxBytes: Long, lastRxBytes: Long, txBytes: Long, lastTxBytes: Long): Boolean =
        rxBytes != lastRxBytes || txBytes != lastTxBytes
}
