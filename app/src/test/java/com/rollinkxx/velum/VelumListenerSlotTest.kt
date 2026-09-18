package com.rollinkxx.velum

import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class VelumListenerSlotTest {
    @Test
    fun staleOwnerCannotClearReplacementListener() {
        val slot = VelumListenerSlot<Any>()
        val oldOwner = Any()
        val newOwner = Any()
        slot.current = oldOwner
        slot.current = newOwner

        assertTrue(!slot.clearIfCurrent(oldOwner))
        assertSame(newOwner, slot.current)
    }

    @Test
    fun currentOwnerCanClearItsListener() {
        val slot = VelumListenerSlot<Any>()
        val owner = Any()
        slot.current = owner

        assertTrue(slot.clearIfCurrent(owner))
        assertTrue(slot.current == null)
    }
}
