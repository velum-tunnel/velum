package com.rollinkxx.velum

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VelumNotificationPermissionTest {
    @Test
    fun preAndroid13NeverRequests() {
        assertFalse(VelumNotificationPermission.shouldRequest(false, false, false))
    }

    @Test
    fun grantedPermissionNeverRequests() {
        assertFalse(VelumNotificationPermission.shouldRequest(true, true, false))
    }

    @Test
    fun deniedPermissionRequestsOnlyBeforeFirstPrompt() {
        assertTrue(VelumNotificationPermission.shouldRequest(true, false, false))
        assertFalse(VelumNotificationPermission.shouldRequest(true, false, true))
    }
}
