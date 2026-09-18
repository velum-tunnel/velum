# Foreground Service and Link Health Implementation Plan

**Goal:** Menjaga proses Velum lebih diprioritaskan saat VPN aktif dan mencegah UI menganggap interface TUN `UP` sebagai internet sehat.

**Architecture:** Karena `GoBackend` dependency memilih `GoBackend.VpnService.class` secara hardcoded, Velum memakai companion foreground service app-owned untuk meningkatkan process importance tanpa mengganti service TUN library. Lifecycle companion service dimulai sebelum `VelumTunnel.up/restart` dan dihentikan saat koneksi gagal atau pengguna memutus. `VelumLinkHealth` adalah state machine murni berbasis state tunnel, umur handshake, dan kemampuan membaca statistik; `ReconnectMonitor` menyegarkan state secara berkala dan UI merender state terpisah dari `Tunnel.State`.

**Tech Stack:** Kotlin Android, Android framework `Service`, `FOREGROUND_SERVICE_SYSTEM_EXEMPTED`, WireGuard GoBackend, JUnit JVM tests, Gradle GitHub Actions.

## Constraints

- Tidak fork atau mengubah dependency WireGuard pada tahap ini.
- Tidak menambah coroutine, OkHttp, atau dependency baru.
- `systemExempted` hanya dipakai karena Velum adalah VPN app yang terdaftar sebagai VPN service; Android/Play policy tetap harus divalidasi di CI dan perangkat.
- Tombol Putuskan harus menghentikan foreground service dan mencegah recovery.
- `Tunnel.State.UP` tetap dipertahankan sebagai transport state; health state tidak boleh mengubah niat pengguna.
- Build APK dan device test dilakukan GitHub Actions/perangkat nyata karena Android SDK tidak tersedia lokal.

## Tasks

1. Add pure `VelumLinkHealth` state machine and unit tests for connected, degraded, offline, and tunnel-down states.
2. Add `VelumForegroundService`, manifest permissions/service declaration, and notification handoff.
3. Start/stop foreground service around connection contract and failure/disconnect paths.
4. Add periodic health refresh to `ReconnectMonitor` and expose health to controller/UI.
5. Render degraded/offline status and update diagnostic/notification text without claiming healthy connectivity.
6. Update device checklist and security/report docs.
7. Run local static checks and GitHub Actions build; inspect test/build/artifact results before reporting.
