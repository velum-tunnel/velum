# VELUM PHASE 3 RUNTIME QA REPORT

## 1. Baseline

- **Phase 2 commit:** `c1d657c` — `fix: harden audited lifecycle and storage handling`.
- **Phase 2 report commit:** `bcda2f8` — `docs: record phase 2 verification results`.
- **Phase 3 branch:** `audit/phase3-runtime`.
- **Phase 3 source state:** no production source changes were made in Phase 3 because no Android runtime reached `adb` state `device`; therefore no runtime bug was proven and no unsupported fix was added.

## 2. Environment

- **JDK:** OpenJDK 17.0.20.
- **Gradle:** 9.7.1 via repository wrapper.
- **Android SDK:** `/home/ubuntu/.android/sdk`, platform 36, build-tools 36.0.0, platform-tools 37.0.1, emulator 37.1.11.0.
- **System image:** API 36 Google APIs / Google Play x86_64 and API 36 Google APIs ARM64 images were installed from the official Google SDK repository.
- **Emulator:** AVD `medium_phone` was created successfully, but x86_64 startup failed because hardware acceleration is unavailable.
- **Physical device:** None. `adb devices -l` showed no devices.
- **Android version / ABI:** No booted runtime. API 36 x86_64 and ARM64 images were available, but neither reached a running device.

### Runtime blocker evidence

The x86_64 emulator log reported:

> `x86_64 emulation currently requires hardware acceleration!`
>
> `/dev/kvm is not found: VT disabled in BIOS or KVM kernel module not loaded`

A bounded ARM64 software-emulation attempt also failed before boot:

> `CPU Architecture 'arm64-v8a' is not supported by the QEMU2 emulator`

The policy forbids adding `/dev/kvm` access or changing sandbox virtualization boundaries. Consequently, no APK was installed or launched on a real Android runtime.

## 3. Build

All Phase 3 gates were run after `./gradlew clean`:

| Gate | Result |
|---|---|
| `./gradlew clean` | PASS |
| `./gradlew testDebugUnitTest` | PASS |
| `./gradlew lintDebug` | PASS |
| `./gradlew assembleDebug` | PASS |
| `./gradlew assemblePreview` | PASS |
| `./gradlew check` | PASS |

APK validation with `aapt` passed for debug and preview ABI outputs. Package names, version names/codes, compile SDK 36, and native ABI payloads were confirmed. `assemblePreview` passed with R8 enabled.

## 4. Runtime Matrix

Because no emulator or physical device reached `adb state=device`, every runtime scenario is **UNVERIFIED RUNTIME**, not PASS:

| Scenario | Debug | Preview | Result |
|---|---|---|---|
| Launch | UNVERIFIED | UNVERIFIED | No device booted |
| Permission | UNVERIFIED | UNVERIFIED | No device booted |
| Register | UNVERIFIED | UNVERIFIED | No device booted |
| Connect | UNVERIFIED | UNVERIFIED | No device booted |
| Handshake | UNVERIFIED | UNVERIFIED | No device booted |
| Traffic | UNVERIFIED | UNVERIFIED | No device booted |
| Disconnect | UNVERIFIED | UNVERIFIED | No device booted |
| Reconnect | UNVERIFIED | UNVERIFIED | No device booted |
| Rotation | UNVERIFIED | UNVERIFIED | No device booted |
| Background | UNVERIFIED | UNVERIFIED | No device booted |
| Tile | UNVERIFIED | UNVERIFIED | No device booted |
| Network change | UNVERIFIED | UNVERIFIED | No device booted |
| Endpoint failover | UNVERIFIED | UNVERIFIED | No device booted |
| Boot recovery | UNVERIFIED | UNVERIFIED | No device booted |
| Storage persistence | UNVERIFIED | UNVERIFIED | No device booted |
| Notification | UNVERIFIED | UNVERIFIED | No device booted |

No logcat, VPN interface, handshake, traffic, notification, service, tile, or lifecycle runtime evidence exists for this phase.

## 5. Bugs Found

| ID | Severity | Confidence | Area | Status |
|---|---|---|---|---|
| P3-ENV-001 | Informational | CONFIRMED | Emulator availability | Environment blocker; not an application bug |

`P3-ENV-001` is confirmed by the emulator log and does not justify a repository code change. No application runtime failure can be classified because the application never ran.

## 6. Fixed Bugs

None in Phase 3. The Phase 2 listener ownership guard, notification permission policy, and encrypted-storage deletion hardening remain present and were included in the clean automated verification.

## 7. Rejected Findings

No application findings were rejected. The x86_64 acceleration error and ARM64 QEMU2 incompatibility were correctly classified as environment limitations rather than Velum defects.

## 8. Remaining Risks

### CONFIRMED

- Runtime testing is blocked in this sandbox by unavailable KVM/hardware acceleration and absence of a physical device.

### LIKELY

- None established from runtime evidence.

### UNVERIFIED

- VPN permission and VpnService behavior.
- WireGuard tunnel initialization, handshake freshness, traffic routing, disconnect, and reconnect.
- Foreground service promotion and modern Android service restrictions.
- Notification permission denial/recreation and notification channel behavior.
- Activity recreation, background/foreground, process death, and listener behavior on a real OS.
- Quick Settings Tile lifecycle and races.
- Network callbacks, endpoint rotation/failover, boot recovery, encrypted storage across process/update, and R8 preview launch.
- Memory/thread/callback leak, ANR, and StrictMode behavior.

## 9. Security Review

- No runtime logcat was available to inspect because no device booted.
- No private key, registration token, credential, keystore, or private configuration was read or included in this report.
- Static manifest/security review from Phase 2 remains valid: backups disabled, encrypted preferences retained, no plaintext storage fallback, and scoped R8 rules.
- Exported components and boot behavior remain candidates for maintainer validation on a real API 34–36 device.

## 10. Performance

- Memory, thread count, reconnect loops, ANR, and main-thread runtime behavior: **UNVERIFIED RUNTIME**.
- No performance claim is inferred from successful compilation or unit tests.

## 11. CI

Phase 3 report changes are documentation-only, so the repository build workflow’s `paths-ignore` rule does not automatically trigger it for this report commit. The Phase 2 source commit used by this branch already passed:

- [Build workflow run 35334161270](https://github.com/velum-tunnel/velum/actions/runs/35334161270): SUCCESS, including unit tests, debug build, preview/R8, lint, release signing/verification, and artifact upload.
- [Documentation workflow run 35334741900](https://github.com/velum-tunnel/velum/actions/runs/35334741900): SUCCESS.

A manual build workflow dispatch for the Phase 3 branch completed successfully: [build run 35337592242](https://github.com/velum-tunnel/velum/actions/runs/35337592242). The documentation workflow for the branch also completed successfully: run `35337574912`.

## 12. Git

- **Branch:** `audit/phase3-runtime`.
- **Phase 3 commit:** `b6d4bb4` (`docs: record phase 3 runtime verification limits`).
- **Remote:** `origin/audit/phase3-runtime`.
- **Main:** untouched; no merge or direct push to `main`.

## 13. Final Status

**AUTOMATED QUALITY: PASS**

The clean Gradle test, lint, debug, preview/R8, and check gates passed locally. The Phase 2 source commit also has successful GitHub Actions evidence.

**RUNTIME QUALITY: UNVERIFIED**

No emulator or physical device reached `adb state=device`. The x86_64 emulator requires unavailable `/dev/kvm`, and ARM64 QEMU2 emulation is unsupported on this host. No runtime PASS claim is made.

**RELEASE READINESS: NOT READY**

Automated artifacts are healthy, but release readiness requires maintainer/device verification of the mandatory VPN, foreground service, notification, lifecycle, network, tile, boot, storage, and traffic scenarios.
