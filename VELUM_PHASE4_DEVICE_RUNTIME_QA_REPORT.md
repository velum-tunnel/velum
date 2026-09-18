# VELUM PHASE 4 DEVICE RUNTIME QA REPORT

## 1. Baseline

- **Phase 2 source fix:** `c1d657c` — `fix: harden audited lifecycle and storage handling`.
- **Phase 2 report:** `bcda2f8`.
- **Phase 3 runtime report:** `b6d4bb4`.
- **Phase 3 CI update:** `8615839`.
- **Phase 4 branch:** `audit/phase4-device-runtime`.
- **Phase 4 source changes:** none. No runtime behavior was changed without a real-device reproducer.

`c1d657c` is an ancestor of this branch. Phase 2 listener ownership, notification permission policy, and encrypted-storage hardening remain present.

## 2. ADB Device Discovery

The required discovery commands were run with the project-local Android SDK:

- **ADB:** Android Debug Bridge 1.0.41, platform-tools 37.0.1.
- **`adb devices -l`:** returned only the header; no connected device.
- **`adb get-state`:** not applicable because no device was present.
- **`adb shell getprop ...`:** not run against any device because no device was present.
- **`adb shell dumpsys battery`:** not run because no device was present.
- **Physical device:** none available.
- **USB debugging / unlock / UI automation:** unverified because no physical device was available.

The local AVD inventory was `arm64_runtime`, `medium_phone`, and `small_phone`. These are not a substitute for an ADB device: the x86_64 AVDs require unavailable `/dev/kvm`, and the ARM64 fallback is unsupported by the host QEMU2 emulator. No AVD was running.

## 3. Automated Quality Before Install

All required gates passed on the inherited Phase 3 source:

| Gate | Result |
|---|---|
| `testDebugUnitTest` | PASS |
| `lintDebug` | PASS |
| `assembleDebug` | PASS |
| `assemblePreview` | PASS |
| `check` | PASS |

No APK was installed because `adb devices -l` found no target device.

## 4. APK and Static Package Validation

The actual generated APKs were enumerated under `app/build/outputs/apk`.

- Debug universal package: `com.rollinkxx.velum.debug`, version `0.1.0`, version code `1`, compile SDK 36, native ABIs `arm64-v8a`, `armeabi-v7a`, `x86`, and `x86_64`.
- Preview universal package: `com.rollinkxx.velum.preview`, version `0.1.0-preview`, version code `1`, compile SDK 36, native ABIs `arm64-v8a`, `armeabi-v7a`, `x86`, and `x86_64`.
- ABI-specific debug and preview APKs were also produced for arm64-v8a, armeabi-v7a, and x86_64.
- `assemblePreview` passed with R8 enabled.

Static manifest review confirmed the existing permissions, launcher Activity, Quick Settings Tile service, boot receiver, non-exported VPN service, and `android:allowBackup="false"`. The network security configuration keeps cleartext disabled and retains the existing certificate pinning configuration.

## 5. Runtime Test Matrix

No runtime scenario was executed because no Android target reached `adb` state `device`. Therefore every item is explicitly **UNVERIFIED**, not PASS:

| ID | Scenario | Expected | Actual | Evidence | Status |
|---|---|---|---|---|---|
| R01 | Cold launch | No crash/ANR; persistent state loads | Not run | No ADB device | UNVERIFIED |
| R02 | Warm/background/foreground | State and listener remain correct | Not run | No ADB device | UNVERIFIED |
| R03 | VPN permission | Grant/deny handled without loop | Not run | No ADB device | UNVERIFIED |
| R04 | VPN connect | Service, interface, fresh handshake, and traffic verified | Not run | No ADB device | UNVERIFIED |
| R05 | Handshake freshness | Session 2 cannot reuse session 1 evidence | Not run | No ADB device | UNVERIFIED |
| R06 | Disconnect | Tunnel/service/traffic stop correctly | Not run | No ADB device | UNVERIFIED |
| R07 | Reconnect cycles | No duplicate service/listener or orphan worker | Not run | No ADB device | UNVERIFIED |
| R08 | Connect/disconnect race | One authoritative tunnel state | Not run | No ADB device | UNVERIFIED |
| R09 | Activity recreation | New Activity receives current state; old listener is harmless | Not run | No ADB device | UNVERIFIED |
| R10 | Background/screen off | Foreground service and notification remain valid | Not run | No ADB device | UNVERIFIED |
| R11 | Foreground service compliance | No start restriction/security exception | Not run | No ADB device | UNVERIFIED |
| R12 | Notification behavior | Permission/channel/state behavior is correct | Not run | No ADB device | UNVERIFIED |
| R13 | Quick Settings Tile | Tile mirrors actual tunnel state without duplicate operations | Not run | No ADB device | UNVERIFIED |
| R14 | Network transition | Reconnect and endpoint behavior are correct | Not run | No ADB device | UNVERIFIED |
| R15 | Endpoint rotation/failover | Successful endpoint is verified; failed endpoint is not retained | Not run | No ADB device | UNVERIFIED |
| R16 | Boot recovery | Recovery follows persisted user intent and platform timing | Not run | No ADB device | UNVERIFIED |
| R17 | Package update/process death | Encrypted state and lifecycle remain valid | Not run | No ADB device | UNVERIFIED |
| R18 | Encrypted storage runtime | No destructive reset on unknown failure; no plaintext fallback | Not run | No ADB device | UNVERIFIED |
| R19 | Notification permission/recreation | No duplicate prompt or stale notification | Not run | No ADB device | UNVERIFIED |
| R20 | Memory/thread/callback stress | No monotonic leak over repeated cycles | Not run | No ADB device | UNVERIFIED |
| R21 | ANR/main-thread audit | No blocking network/tunnel work on UI thread | Not run | No ADB device | UNVERIFIED |
| R22 | Preview/R8 runtime | No class/linker/JNI/reflection failure | Not run | No ADB device | UNVERIFIED |
| R23 | Low-resource/interruption | No stale state or endless retry | Not run | No ADB device | UNVERIFIED |
| R24 | Runtime logging privacy | No secrets in logcat | Not run | No ADB device | UNVERIFIED |

## 6. Bugs Found

| ID | Severity | Confidence | Area | Status |
|---|---|---|---|---|
| P4-ENV-001 | Informational | CONFIRMED | Device availability | Environment blocker, not a Velum defect |

`P4-ENV-001` is confirmed by `adb devices -l` returning no device. No application bug can be confirmed without executing the application.

## 7. Fixed Bugs

None in Phase 4. There was no device runtime evidence from which to derive a safe root cause, reproducer, or regression test. Phase 2 fixes remain intact and passed all automated gates.

## 8. Rejected Findings

No suspected application behavior was promoted to a bug. Successful compilation, APK generation, and CI are not treated as VPN runtime evidence.

## 9. Remaining Risks

### CONFIRMED

- ADB device runtime testing cannot be performed in this environment because no physical device or accessible remote device is connected.

### LIKELY

- None established from Phase 4 runtime evidence.

### UNVERIFIED

- Actual VPN permission flow, VpnService lifecycle, foreground-service compliance, WireGuard handshake freshness, network traffic routing, disconnect, reconnect, endpoint rotation, boot recovery, TileService, notification behavior, Activity recreation, process death, encrypted storage persistence, logging privacy, memory/thread leaks, and ANR behavior.

## 10. Security and Privacy

- No device logcat was captured because no device was available.
- No private key, registration token, credential, keystore, authorization header, or private configuration was read or added to the repository.
- Static checks found the existing backup, cleartext, pinning, exported-component, encrypted-storage, and R8 configurations unchanged.

## 11. CI

The Phase 4 branch contains only this report at this stage and was not yet pushed when this report was written. CI must be triggered after the branch push; no Phase 4 CI PASS is claimed in this document before that workflow completes.

## 12. Git

- **Branch:** `audit/phase4-device-runtime`.
- **Phase 2/3 history:** preserved; no history rewrite.
- **Main:** untouched.
- **Phase 4 source changes:** none apart from this report.

## 13. Final Status

**AUTOMATED QUALITY: PASS**

All required local unit-test, lint, debug-build, preview/R8, and check gates passed.

**RUNTIME QUALITY: UNVERIFIED**

No physical or remote Android device was visible through ADB, and no emulator reached a running state. No runtime PASS claim is made.

**RELEASE READINESS: NOT READY**

Release readiness requires a maintainer to provide an unlocked Android device or remote ADB lab and execute the runtime matrix with layered evidence for service state, VPN interface, fresh handshake, actual traffic, disconnect, and reconnect.
