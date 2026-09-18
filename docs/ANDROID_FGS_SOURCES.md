# Android Foreground Service Sources

- Android foreground service type requirements: https://developer.android.com/about/versions/14/changes/fgs-types-required
- Foreground service types, including `systemExempted` and VPN eligibility: https://developer.android.com/develop/background-work/services/fgs/service-types
- Android VPN service lifecycle and persistent notification guidance: https://developer.android.com/develop/connectivity/vpn
- Foreground service background-start restrictions: https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start
- WireGuard `GoBackend.VpnService` source inspected: https://git.zx2c4.com/wireguard-android/tree/tunnel/src/main/java/com/wireguard/android/backend/GoBackend.java

Key facts used by implementation: Android 14+ requires a declared foreground-service type; `systemExempted` lists VPN apps configured through system VPN settings as an eligible category; a VPN app should keep a non-dismissible notification while active; current WireGuard `GoBackend` starts its own hardcoded `GoBackend.VpnService`, so Velum uses an app-owned companion foreground service rather than claiming to replace the library service.
