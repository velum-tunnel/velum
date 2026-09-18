package com.rollinkxx.velum

internal object VelumNotificationPermission {
    fun shouldRequest(api33OrNewer: Boolean, granted: Boolean, alreadyRequested: Boolean): Boolean =
        api33OrNewer && !granted && !alreadyRequested
}
