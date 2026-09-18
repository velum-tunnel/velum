# Velum Phase 6 Real Device Runtime Validation Report

## Executive Summary

Phase 6 attempted to onboard a real Android target and execute the mandatory runtime validation chain. The environment still has no physical device, approved remote device, device farm, or hardware-accelerated emulator. `adb devices -l` returned no device in state `device`, and the Phase 5 harness correctly failed closed with exit code 2.

The automated source/build gates pass, but no APK was installed and the app did not run on Android. Consequently, VPN functional validation remains **UNVERIFIED**, runtime infrastructure is **BLOCKED**, and release readiness remains **NOT READY**. No fake device, fake logcat, simulated VPN evidence, or fabricated runtime result was created.

## Branch / Commit

- **Branch:** `audit/phase6-real-device-validation`.
- **Baseline:** Phase 5 commit `29def37`, including harness commit `bf16f30`.
- **Production source changes:** none.
- **Main:** untouched.

## Runtime Environment

- Linux x86_64, kernel 6.18.38+.
- Repository-compatible JDK 17 used for Gradle verification.
- Android SDK: `/home/ubuntu/.android/sdk`.
- Platform-tools / ADB: 37.0.1.
- Emulator AVDs present: `arm64_runtime`, `medium_phone`, `small_phone`.
- `/dev/kvm`: unavailable.
- No configured remote device-farm endpoint or credential path.

## Device Information

No valid runtime target was available.

| Field | Result |
|---|---|
| Device type | None |
| Manufacturer/model | Unavailable |
| Android version/API | Unavailable |
| ABI | Unavailable |
| Build fingerprint | Unavailable |
| Boot completion | Unavailable |
| UI automation | Unavailable |

## ADB Validation

Commands executed:

```text
adb start-server
adb version
adb devices -l
```

Actual device result:

```text
List of devices attached
```

No serial appeared in state `device`. The reusable Phase 5 harness was also executed:

```text
scripts/phase5/runtime.sh detect_device
ERROR: expected exactly one authorized ADB device in state 'device'; found 0
harness_exit=2
```

This is a confirmed infrastructure blocker. No device health or ADB stability commands were run against a nonexistent target.

## APK Build

All mandatory automated gates passed:

| Gate | Result |
|---|---|
| `testDebugUnitTest` | PASS |
| `lintDebug` | PASS |
| `assembleDebug` | PASS |
| `assemblePreview` | PASS |
| `check` | PASS |

Actual APK outputs included:

- `app/build/outputs/apk/debug/app-universal-debug.apk`
- `app/build/outputs/apk/preview/app-universal-preview.apk`
- ABI-specific debug and preview APKs for arm64-v8a, armeabi-v7a, and x86_64.

The universal debug APK reports package `com.rollinkxx.velum.debug`, version `0.1.0`, version code `1`, compile SDK 36, and ABIs arm64-v8a, armeabi-v7a, x86, and x86_64.

The universal preview APK reports package `com.rollinkxx.velum.preview`, version `0.1.0-preview`, version code `1`, compile SDK 36, and the same universal ABI set.

## APK Installation

**BLOCKED.** No APK was installed because no valid ADB target was available. The actual package IDs were identified statically; installation was not claimed.

## Application Launch

**UNVERIFIED.** No Activity was launched on Android and no device logcat was captured.

Static source/manifest inspection identifies `MainActivity` as the application Activity and the WireGuard library service declaration as:

```text
com.wireguard.android.backend.GoBackend$VpnService
```

Static identification is not runtime evidence.

## VPN Permission

**UNVERIFIED.** No Android VPN permission dialog, grant, denial, cancellation, or lifecycle behavior was observed.

## VpnService Validation

**UNVERIFIED.** The repository statically declares the WireGuard GoBackend VpnService and uses `VpnService.prepare`, but no device-side service state was captured from `dumpsys activity services`.

## VPN Interface Validation

**UNVERIFIED.** No `ip addr` or `ip route` snapshot could be collected before or after connection.

## Fresh Handshake Validation

**UNVERIFIED.** No connection session ran, so no timestamped before/after handshake evidence exists. A positive or cached handshake was not inferred from source or UI.

## HTTPS Traffic Validation

**UNVERIFIED.** No device-side HTTPS request was initiated or observed through the tunnel.

## DNS Validation

**UNVERIFIED.** No Android-device DNS resolution was performed.

## Disconnect Validation

**UNVERIFIED.** No VPN connection was established, so service, interface, route, traffic, notification, and UI disconnect behavior were not tested.

## Reconnect Validation

**UNVERIFIED.** No connect → disconnect → connect sequence ran. Fresh handshake and second-session traffic evidence are absent.

## Lifecycle Validation

**UNVERIFIED.** Activity recreation, background/foreground, listener ownership, stale callbacks, and service stability were not tested on Android OS.

## Background / Foreground

**UNVERIFIED.** No connected VPN state existed on a device from which to test screen/background transitions.

## Notification Validation

**UNVERIFIED.** No Android notification permission, channel, VPN notification, duplicate notification, or stop behavior was observed.

## TileService Validation

**UNVERIFIED.** No Quick Settings Tile was added, toggled, or compared with actual tunnel state.

## Network Transition

**UNVERIFIED.** Wi-Fi/mobile-data transitions, network loss, reconnect behavior, and new handshake evidence were not tested.

## Airplane Mode

**UNVERIFIED.** No device was available for airplane-mode interruption and recovery.

## Endpoint Failover

**UNVERIFIED.** No safe runtime endpoint-failure environment was available and no production endpoint was disturbed.

## Boot Recovery

**UNVERIFIED.** No device reboot or BootReceiver observation was possible.

## Process Death

**UNVERIFIED.** No application or service process was launched on a device, so no force-stop/restart behavior was tested.

## Encrypted Storage Persistence

**UNVERIFIED at runtime.** Phase 2 encrypted-storage hardening remains present and automated tests pass, but Android Keystore persistence across force-stop/reboot requires a real target.

## R8 / Release-like Runtime

**UNVERIFIED at runtime.** `assemblePreview` with R8 passed, but preview installation, launch, permission, connect, traffic, disconnect, and reconnect were not performed.

## Stress / Stability

**UNVERIFIED.** No connect/disconnect cycles, ANR observation, memory measurement, reconnect storm analysis, duplicate listener check, or notification duplication test ran.

## Bugs Found

No application runtime bug was found because the app never ran on Android. One infrastructure blocker was confirmed:

| ID | Title | Environment | Device | Steps | Expected | Actual | Evidence | Root cause | Fix | Regression | Status |
|---|---|---|---|---|---|---|---|---|---|---|---|
| P6-INFRA-001 | No usable Android runtime target | Linux sandbox | None | Run `adb start-server`; run `adb devices -l` | At least one authorized device | Empty device list; KVM unavailable | ADB output and harness exit 2 | Missing physical/remote device and `/dev/kvm` | None; infrastructure dependency | Phase 5 fail-fast harness remains passing | BLOCKED |

## Bugs Fixed

None. No production behavior was changed without runtime evidence and a reproducer.

## Regression Verification

- `testDebugUnitTest`: PASS.
- `lintDebug`: PASS.
- `assembleDebug`: PASS.
- `assemblePreview`: PASS.
- `check`: PASS.
- Phase 5 harness device detection: expected fail-fast exit 2 with zero devices — PASS.
- Phase 2 listener ownership and notification/storage hardening markers remain present.

## Evidence Inventory

Evidence available in this phase:

- ADB version output.
- Empty `adb devices -l` output.
- `KVM_UNAVAILABLE` result.
- Local AVD inventory.
- Harness fail-fast output and exit code 2.
- Gradle gate logs under `/tmp/velum-phase6-*.log` in the execution environment.
- APK enumeration and `aapt` package/ABI output.
- Static VpnService declaration evidence.

No logcat, dumpsys, `ip addr`, `ip route`, device screenshot, handshake timestamp, or actual traffic artifact exists because no device was available. No secret, credential, private key, token, or private VPN configuration was stored.

## Known Limitations

A physical Android device, approved remote Android device, device farm, or host with functional hardware acceleration is required before the mandatory runtime chain can be executed. GitHub-hosted build success does not provide real-device validation. Repeating the known `/dev/kvm`-blocked emulator attempt would not change the infrastructure result.

## Runtime Test Matrix

| Test | Device | Result | Evidence | Notes |
|---|---|---|---|---|
| ADB connectivity | None | BLOCKED | Empty `adb devices -l` | No target |
| Device health | None | BLOCKED | No target | Not run |
| APK install | None | BLOCKED | No target | Actual APK identified only |
| Cold launch | None | UNVERIFIED | No logcat | Not run |
| VPN permission | None | UNVERIFIED | No device | Not run |
| VPN service | None | UNVERIFIED | Static service only | No dumpsys |
| VPN interface | None | UNVERIFIED | No ip snapshot | Not run |
| Fresh handshake | None | UNVERIFIED | No session | Not run |
| HTTPS traffic | None | UNVERIFIED | No device | Not run |
| DNS | None | UNVERIFIED | No device | Not run |
| Disconnect | None | UNVERIFIED | No connected session | Not run |
| Reconnect | None | UNVERIFIED | No connected session | Not run |
| Lifecycle | None | UNVERIFIED | No Activity runtime | Not run |
| Background/Foreground | None | UNVERIFIED | No device | Not run |
| Notification | None | UNVERIFIED | No device | Not run |
| TileService | None | UNVERIFIED | No device | Not run |
| Wi-Fi → Mobile | None | UNVERIFIED | No device | Not run |
| Mobile → Wi-Fi | None | UNVERIFIED | No device | Not run |
| Airplane mode | None | UNVERIFIED | No device | Not run |
| Failover | None | UNVERIFIED | No safe runtime environment | Not run |
| Boot recovery | None | UNVERIFIED | No device | Not run |
| Process death | None | UNVERIFIED | No device | Not run |
| Storage persistence | None | UNVERIFIED | No device/Keystore | Not run |
| R8/release-like | None | UNVERIFIED | Build only | No installation |
| Stress | None | UNVERIFIED | No device | Not run |

## Final Status

**AUTOMATED QUALITY: PASS**

All required local tests, lint, debug/preview builds, check, APK verification, and harness checks passed.

**RUNTIME INFRASTRUCTURE: BLOCKED**

No real or approved remote device was reachable through ADB, and hardware-accelerated emulator support is unavailable.

**RUNTIME QUALITY: UNVERIFIED**

No mandatory runtime evidence exists. In particular, VPN functional validation is not PASS because the chain from installation through fresh handshake, actual HTTPS/DNS traffic, disconnect, reconnect, and second fresh handshake was not executed.

**RELEASE READINESS: NOT READY**

Runtime evidence is required before release readiness can be reconsidered.

## Recommended Next Phase

Provide an authorized physical Android device or approved remote device-farm/self-hosted runner. Begin with `adb devices -l`, run the Phase 5 harness health check, install the actual debug APK, and execute the matrix with structured redacted evidence. Repeat the core VPN chain against the preview/R8 variant before making any runtime or release claim.
