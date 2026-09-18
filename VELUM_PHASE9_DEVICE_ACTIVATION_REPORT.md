# Velum Phase 9 Device Activation Report

## Objective

Phase 9 creates the shortest deterministic bridge from an operator-provided Android device to the first Velum runtime smoke test. It prioritizes physical USB ADB, supports Android Wireless Debugging as a second path, and reuses the Phase 5 harness and Phase 7 workflow without redesigning them.

The current environment did not provide hardware, so the activation package is complete but the first real-device smoke test is hard-blocked.

## Environment Discovery

Discovery was performed on the Phase 9 branch:

- Linux x86_64, kernel 6.18.38+.
- ADB 37.0.1 available from the project-local Android SDK.
- `aapt` available from Android build-tools.
- Java and Git available.
- Host GitHub connectivity preflight passed.
- `/dev/kvm` unavailable.
- Local AVD definitions exist, but no usable accelerated emulator is available.
- `adb devices -l` output was empty:

```text
List of devices attached
```

No existing remote ADB endpoint, self-hosted runner, or device-farm configuration was found.

## Device

**BLOCKED.** No physical Android device is connected by USB or Wireless ADB. No device identity was collected and no runtime command was run against a nonexistent target.

## ADB

Host ADB is installed and operational as a server, but no target is authorized:

```text
Android Debug Bridge version 1.0.41
Version 37.0.1-15733141
```

The new device preflight rejected the empty target list with exit code 2. This is a real fail-closed result, not a simulated failure.

## Host

**READY for activation preparation.** `scripts/phase9/host_preflight.sh` passed checks for:

- ADB;
- Java;
- Git;
- repository checkout;
- `aapt`;
- outbound GitHub connectivity.

The sandbox is not a persistent or registered self-hosted runner, so it is not READY for CI runtime execution.

## Runner

**BLOCKED.** No self-hosted runner is registered or online. The existing workflow remains manual-only and uses labels:

```text
self-hosted
android
velum-runtime
```

No registration token or secret was requested, printed, or stored.

## Runner Security

The existing real-device workflow remains restricted to `workflow_dispatch`, with no arbitrary push, pull-request, or fork execution on trusted hardware. Concurrency locking prevents parallel use of the same device. Evidence upload runs on failure, and cleanup avoids factory reset, unrelated uninstallation, and personal-data deletion.

The runner should be dedicated, placed in a restricted runner group, and treated as high-risk infrastructure. No credentials were embedded in the activation package.

## Device Onboarding

Two executable modes are provided:

- **USB ADB:** unlock device, enable Developer Options and USB debugging, connect cable, accept RSA authorization, then run `scripts/phase9/adb_connect.sh usb`.
- **Wireless ADB:** enable Wireless Debugging on Android 11+, keep host and device on a trusted network, then run `scripts/phase9/adb_connect.sh wireless <connect-host:port> <pair-host:port>`. The script prompts for the pairing code without storing or echoing it.

Both modes require exactly one serial in state `device` before continuing.

## Device Health

`scripts/phase9/device_preflight.sh artifacts/phase9/device` checks:

- exactly one ADB target;
- state `device`;
- boot completion;
- shell command;
- package manager;
- connectivity service;
- device manufacturer, model, Android/API, ABI, serial, and fingerprint capture.

On the current environment it produced:

```text
Expected exactly one ADB device; found 0
List of devices attached
device_preflight_exit=2
```

## APK Install

**UNVERIFIED.** No device was available for installation. The activation script is prepared to run `assembleDebug`, discover the actual universal APK and package ID with `aapt`, and install that exact APK through the existing harness. It does not guess the package ID.

## App Launch

**UNVERIFIED.** No Activity was launched because preflight stopped before build/install activation. The script will derive the launchable Activity from the actual APK and capture logcat around launch.

## VPN Smoke

**UNVERIFIED.** No VPN smoke test was started. The first smoke sequence must remain limited to:

```text
Launch → VPN permission → Connect → VpnService → VPN interface
→ fresh handshake → actual HTTPS → Disconnect
```

A UI label alone cannot establish a PASS.

## VPN Service Evidence

**UNVERIFIED.** No physical VpnService evidence exists.

## VPN Interface Evidence

**UNVERIFIED.** No device-side `ip addr` or `ip route` evidence exists.

## Fresh Handshake Evidence

**UNVERIFIED.** No connection session was initiated and no fresh handshake timestamp exists.

## HTTPS Evidence

**UNVERIFIED.** No actual device HTTPS request through the VPN was performed.

## Disconnect Evidence

**UNVERIFIED.** No VPN session was established, so disconnect behavior is unverified.

## Full Runtime Trigger

Not triggered. The full Phase 6 matrix must begin only after the first smoke chain passes with layered evidence. The existing workflow and Phase 5 harness are the intended next execution path once a device is available.

## Bugs

| ID | Classification | Evidence | Status |
|---|---|---|---|
| P9-INFRA-001 | No Android device | Empty `adb devices -l` | BLOCKED |
| P9-INFRA-002 | No self-hosted runner | No registered/online runner | BLOCKED |
| P9-INFRA-003 | No hardware acceleration | `/dev/kvm` unavailable | BLOCKED |

No Velum application bug was reproduced.

## Fixes

No production code was changed. The activation-only package added:

- `scripts/phase9/host_preflight.sh`;
- `scripts/phase9/device_preflight.sh`;
- `scripts/phase9/adb_connect.sh`;
- `scripts/phase9/runtime_activate.sh`;
- `docs/phase9-device-activation.md`.

The existing Phase 5 harness and Phase 7 workflow were reused.

## Artifacts

When a device is available, the activation package writes timestamped evidence under the operator-selected directory, including device identity, ADB state, APK target metadata, install output, launch/smoke output, package state, logcat, connectivity, IP address/routes, services, and network state.

Current actual evidence consists of host preflight output, empty ADB discovery, the zero-device preflight exit 2, and passing Gradle gates. No device-side evidence exists.

## Activation Runbook

The executable runbook is [docs/phase9-device-activation.md](/home/ubuntu/work/velum/docs/phase9-device-activation.md). The operator sequence is:

```bash
scripts/phase9/host_preflight.sh
scripts/phase9/adb_connect.sh usb
# or: scripts/phase9/adb_connect.sh wireless <connect-host:port> <pair-host:port>
scripts/phase9/device_preflight.sh artifacts/phase9/device
scripts/phase9/runtime_activate.sh artifacts/phase9
```

If any preflight fails, stop, preserve artifacts, classify the failure, and do not start the full matrix. No credentials are saved by the scripts.

## Limitations

A real Android device and trusted persistent host cannot be created from this repository or current sandbox. The host preflight is ready, but device/ADB/runner activation requires operator-provided hardware and authorization. Repeating the known emulator attempt would not change the infrastructure.

## Automated Quality

The Phase 9 build workflow completed successfully: [run 35379594377](https://github.com/velum-tunnel/velum/actions/runs/35379594377), including unit tests, debug build, preview/R8, lint, release signing/verification, and artifact upload. The documentation workflow also completed successfully: run `35379594429`.

All required gates passed after adding the activation package:

| Gate | Result |
|---|---|
| `testDebugUnitTest` | PASS |
| `lintDebug` | PASS |
| `assembleDebug` | PASS |
| `assemblePreview` / R8 | PASS |
| `check` | PASS |
| Phase 9 script syntax | PASS |
| Zero-device preflight and activation fail-fast | PASS; expected exit 2 |

## Final Status

**DEVICE: BLOCKED**

No physical USB or Wireless ADB device is available.

**DEVICE TYPE: None**

**ADB: BLOCKED**

ADB is installed, but `adb devices -l` is empty.

**HOST: READY for activation preparation / BLOCKED for runtime**

Host tools and repository are present, but the sandbox is not a persistent trusted runtime host.

**RUNNER: BLOCKED**

No self-hosted runner is registered or online.

**RUNNER SECURITY: PASS by design / UNVERIFIED operationally**

The trusted workflow and runbook preserve manual-only execution, labels, locking, and no-secret handling, but no live runner exists.

**APK INSTALL: UNVERIFIED**

**APP LAUNCH: UNVERIFIED**

**VPN SERVICE: UNVERIFIED**

**VPN INTERFACE: UNVERIFIED**

**FRESH HANDSHAKE: UNVERIFIED**

**ACTUAL HTTPS: UNVERIFIED**

**DISCONNECT: UNVERIFIED**

**SMOKE: UNVERIFIED**

**FULL RUNTIME MATRIX: BLOCKED**

**AUTOMATED QUALITY: PASS**

**RUNTIME INFRASTRUCTURE: BLOCKED**

**RUNTIME QUALITY: UNVERIFIED**

**RELEASE READINESS: NOT READY**

**BUGS FOUND:** 3 infrastructure blockers; 0 application bugs.

**BUGS FIXED:** 0 production bugs; activation package prepared.

**HARDWARE BLOCKER:** no physical/remote Android device and no persistent self-hosted host.

**ACTIVATION RUNBOOK: READY**

## Next Phase

Provide one dedicated Android test device and a trusted host. Execute the runbook through the first smoke chain. If smoke passes, reuse the existing Phase 6 runtime matrix; if it fails, stop and classify before changing production code.
