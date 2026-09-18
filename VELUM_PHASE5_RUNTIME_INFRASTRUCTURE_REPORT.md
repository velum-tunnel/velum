# Velum Phase 5 Runtime Infrastructure Report

## Executive Summary

Phase 5 investigated whether this environment could provide a real Android runtime for the mandatory VPN validation matrix. No physical device, remote ADB device, configured device farm, or hardware-accelerated emulator was available. The correct infrastructure result is **BLOCKED**, not PASS.

To leave a reusable result rather than repeatedly retrying the same unavailable environment, this phase adds `scripts/phase5/runtime.sh`, a fail-fast ADB harness, and operator documentation. The harness rejects an empty, offline, unauthorized, or multi-device ADB state and never fabricates runtime evidence.

## Repository / Branch

- **Branch:** `audit/phase5-runtime-infrastructure`.
- **Baseline:** Phase 4 commit `87adeaa`, with Phase 2 and Phase 3 history preserved.
- **Main:** untouched.
- **Production source changes:** none.

## Environment

- Linux x86_64, kernel 6.18.38+.
- OpenJDK 21 is the shell default; repository-compatible JDK 17 remains installed and was used for Gradle verification.
- No physical Android device is attached.
- No remote ADB endpoint or device-farm connector is configured in the environment.
- No device credential names or secret values were read or printed.

## Android SDK

- `ANDROID_HOME=/home/ubuntu/.android/sdk`.
- Platform-tools 37.0.1; `adb` is available.
- Android platform 36 and build-tools 36.0.0 are installed.
- Emulator 37.1.11.0 is installed.
- `sdkmanager` and `avdmanager` standalone commands are not present; Android CLI manages the local SDK/AVDs.

## ADB

Discovery commands executed:

```text
adb start-server
adb devices -l
```

Actual result:

```text
List of devices attached
```

There was no serial in state `device`. Therefore no device identity, fingerprint, battery, airplane-mode, boot-completed, or `adb shell` health data could be collected.

The new harness was executed as:

```text
scripts/phase5/runtime.sh detect_device
```

It failed closed with exit code 2 and reported zero authorized devices. This is the intended safety behavior.

## Physical Device

**Unavailable.** No USB Android device is connected or authorized.

## Remote Device / Device Farm

**Unavailable / not configured.** Repository and environment discovery found no existing remote ADB endpoint, self-hosted Android runner, device-farm configuration, or approved credential path. No new service or credential was created.

## Emulator Availability

Local AVD names are present: `arm64_runtime`, `medium_phone`, and `small_phone`. Their presence is not runtime availability. Phase 3 established that x86_64 startup fails because `/dev/kvm` is unavailable, while ARM64 QEMU2 emulation is unsupported on this x86 host.

Phase 5 did not repeat emulator attempts because the blocker is already confirmed and the policy prohibits changing virtualization boundaries or granting `/dev/kvm` access.

## KVM Availability

**KVM_UNAVAILABLE.** `/dev/kvm` does not exist. Hardware-accelerated x86_64 emulator execution is therefore blocked.

## Device Identity

Not applicable. No ADB device reached state `device`, so manufacturer, model, Android version, API level, ABI, build type, fingerprint, unlock state, and UI automation capability are unavailable.

## Device Health

Not run because no device was present. The harness requires exactly one authorized device before executing health commands or writing a probe file on the device.

## APK Build

All required automated gates passed after adding the harness and documentation:

| Gate | Result |
|---|---|
| `testDebugUnitTest` | PASS |
| `lintDebug` | PASS |
| `assembleDebug` | PASS |
| `assemblePreview` | PASS |
| `check` | PASS |

APK generation, R8/preview compilation, and CI are not treated as runtime evidence.

## APK Installation

**UNVERIFIED.** No APK was installed because no authorized ADB device was available. The harness requires the actual APK path and actual package ID and will fail before installation when no device exists.

## Runtime Smoke Test

**BLOCKED.** Launch, logcat capture, package state, VPN state, and network state require a real ADB device. `runtime.sh run_runtime_smoke_test` is ready for a maintainer or device-farm runner but was not claimed as executed here.

## VPN Runtime Validation

**UNVERIFIED.** No VpnService state, VPN interface, fresh handshake, service state, disconnect behavior, or reconnect behavior was observed.

## Network Traffic Validation

**UNVERIFIED.** No device-side HTTPS, DNS, route, or tunnel traffic evidence was collected.

## Lifecycle Validation

**UNVERIFIED.** Cold launch, background/foreground, Activity recreation, process death, and listener ownership were not executed on Android OS.

## Notification Validation

**UNVERIFIED.** No notification permission, channel, foreground-service notification, or process-recreation notification behavior was observed.

## TileService Validation

**UNVERIFIED.** No Quick Settings Tile interaction was possible.

## Network Transition Validation

**UNVERIFIED.** Wi-Fi/mobile-data, airplane-mode, network-loss, callback, and endpoint failover scenarios were not run.

## Boot Validation

**UNVERIFIED.** No Android device was available for reboot or BootReceiver observation.

## Process Death Validation

**UNVERIFIED.** No package process was launched on a real device, so no device-side process death or state persistence claim is made.

## Storage Persistence Validation

**UNVERIFIED.** The Phase 2 encrypted-storage hardening remains in source and automated tests, but Android Keystore persistence and failure injection require a real device or emulator.

## R8 / Release-like Validation

**PASS for automated build only; UNVERIFIED at runtime.** `assemblePreview` passed with R8 enabled. Preview APK installation and launch on Android were not performed.

## Performance / ANR Observations

**UNVERIFIED.** No device memory, thread, ANR, batterystats, reconnect-loop, or main-thread runtime measurements were collected.

## Bugs Found

No Velum runtime bug was found because the application never ran on an Android target. The confirmed Phase 5 issue is infrastructure-only:

| ID | Severity | Confidence | Area | Status |
|---|---|---|---|---|
| P5-INFRA-001 | Informational | CONFIRMED | Runtime device access | No physical/remote ADB device and no KVM |

## Fixes Applied

No production behavior was changed. The following reusable tooling was added:

- `scripts/phase5/runtime.sh`: device detection, device health, debug/preview build, install/uninstall, launch, redacted log capture, package state, VPN/network state, smoke test, and artifact collection.
- `scripts/phase5/README.md`: physical-device/device-farm usage, evidence boundaries, and explicit distinction between launch smoke testing and VPN validation.

The harness was tested against the current environment and correctly failed closed with zero devices.

## Regression Tests

- `scripts/phase5/runtime.sh detect_device`: expected fail-fast exit code 2 with empty ADB list — PASS.
- `testDebugUnitTest`: PASS.
- `lintDebug`: PASS.
- `assembleDebug`: PASS.
- `assemblePreview`: PASS.
- `check`: PASS.

No assertion was weakened and no production test was removed.

## Evidence

- `adb devices -l` output contained no device.
- `/dev/kvm` was unavailable.
- Local AVD inventory was present but not a usable runtime.
- Harness fail-fast output explicitly reported zero authorized devices.
- All automated Gradle gates passed.

No private key, registration token, credential, keystore, authorization header, or private configuration was stored in the repository or report.

## Limitations

A real device or approved remote device-farm connection is a prerequisite for the mandatory runtime matrix. GitHub-hosted runners must not be treated as physical-device infrastructure. A future self-hosted runner/device-farm workflow must provision ADB securely, fail if no device is present, redact artifacts, and retain logcat/dumpsys evidence without secrets.

## Final Status

**AUTOMATED QUALITY: PASS**

Unit tests, lint, debug build, preview/R8 build, check, and harness fail-fast validation passed.

**RUNTIME INFRASTRUCTURE: BLOCKED**

No ADB device in state `device`, no physical device, no remote farm, and no KVM.

**RUNTIME QUALITY: UNVERIFIED**

The application was not run on Android, so VPN and device behavior remain unverified rather than passed or failed.

**RELEASE READINESS: NOT READY**

Mandatory runtime evidence is absent. A release decision requires a real device or approved device farm and layered evidence for service state, VPN interface, fresh handshake, actual traffic, disconnect, reconnect, lifecycle, notification, tile, network transition, boot, and persistence.

## Recommendation for Phase 6

Provide an authorized physical Android device or approved remote device-farm/self-hosted runner. Run `scripts/phase5/runtime.sh verify_device`, build and install the actual debug APK, execute the Phase 4 matrix with redacted evidence, then repeat the core matrix against preview/R8. Only after those results are available should runtime quality or release readiness be reconsidered.
