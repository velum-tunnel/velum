package com.rollinkxx.velum

/** Kebijakan murni untuk input dan cache proba endpoint. */
object VelumProbePolicy {
    fun sanitizeDohCandidates(raw: String?): List<String> = raw
        ?.split(',')
        ?.asSequence()
        ?.map(String::trim)
        ?.filter(VelumFormat::isIpv4)
        ?.distinct()
        ?.take(VelumDoh.MAX_STORED)
        ?.toList()
        .orEmpty()

    fun speedEndpointFor(host: String, registrationHost: String?, wgPort: Int): String? {
        if (host == registrationHost || !VelumFormat.isIpLiteral(host)) return null
        val safeHost = if (VelumFormat.isIpv6(host)) "[$host]" else host
        return "$safeHost:$wgPort"
    }

    fun shouldInvalidateSpeed(current: String?, failed: String?): Boolean =
        !failed.isNullOrEmpty() && current == failed
}
