# Velum Phase 10 First Real Device Report

## Objective

Phase 10 was intended to execute the first actual Android runtime chain using the Phase 5–9 tooling: device preflight, APK installation, launch, VPN permission, VpnService, VPN interface, fresh handshake, actual HTTPS/DNS traffic, disconnect, reconnect, and then the full runtime matrix.

The mandatory first discovery found no Android target. Under the Phase 10 zero-device rule, execution stopped before build/install/launch/VPN so no synthetic runtime evidence could be produced.

## Device

**BLOCKED.** No physical USB device, Wireless ADB device, device-farm target, or usable emulator was available.

Discovery output:

```text
List of devices attached
```

No manufacturer, model, Android version, API, ABI, or serial was recorded because no device existed.

## ADB

ADB is installed and the server starts:

```text
Android Debug Bridge version 1.0.41
Version 37.0.1-15733141
```

The existing Phase 9 preflight was reused without modification:

```text
Expected exactly one ADB device; found 0
List of devices attached
device_preflight_exit=2
```

This is the intended fail-closed result. No `adb shell`, package-manager, logcat, install, or runtime command was run against a nonexistent target.

## Host

The sandbox has the Android SDK and ADB tools, but it is not a persistent trusted runtime host or registered self-hosted runner. `/dev/kvm` is unavailable. No existing runner or device-farm configuration was discovered.

## APK

**NOT BUILT FOR RUNTIME.** The Phase 10 zero-device rule requires stopping immediately after discovery/preflight. Existing Phase 9 automated CI already established that the repository builds successfully, but that is not a Phase 10 device execution result.

## Installation

**UNVERIFIED.** No APK was installed because no valid ADB device was available.

## Launch

**UNVERIFIED.** No Activity was launched and no device logcat was captured.

## VPN Permission

**UNVERIFIED.** No permission request, grant, denial, or cancellation flow was run.

## VpnService

**UNVERIFIED.** No actual `WireGuard GoBackend$VpnService` or other VPN service state was observed.

## VPN Interface

**UNVERIFIED.** No before/after `adb shell ip addr` or `adb shell ip route` snapshots exist.

## Fresh Handshake

**UNVERIFIED.** No connection session was initiated, so no T0/T1/T2 handshake evidence exists.

## HTTPS

**UNVERIFIED.** No actual HTTPS request through Velum was attempted.

## DNS

**UNVERIFIED.** No device-side DNS resolution was attempted.

## Disconnect

**UNVERIFIED.** No VPN session was established, so disconnect behavior cannot be evaluated.

## Reconnect

**UNVERIFIED.** No first connection existed from which to perform disconnect/reconnect or collect fresh handshake #2 and traffic #2.

## Lifecycle

**UNVERIFIED.** No activity background/foreground, recreation, listener, service, notification, or reconnect-monitor behavior was exercised.

## Notification

**UNVERIFIED.** No foreground VPN notification was observed.

## TileService

**UNVERIFIED.** No Quick Settings interaction was possible.

## Network Transition

**UNVERIFIED.** No Wi-Fi/mobile transition was exercised.

## Airplane Mode

**UNVERIFIED.** No airplane-mode scenario was run; device settings were not changed.

## Failover

**UNVERIFIED.** No endpoint failover scenario was run.

## Boot

**UNVERIFIED.** No device reboot or boot-recovery scenario was run.

## Process Death

**UNVERIFIED.** No package was installed or force-stopped on a target device.

## Storage Persistence

**UNVERIFIED.** No device state was written or read across process/device lifecycle.

## R8 / Release-like

**UNVERIFIED on device.** No release-like APK was installed or exercised on Android hardware.

## Stress

**UNVERIFIED.** No connect/disconnect cycles ran.

## Bugs

| ID | Classification | Evidence | Status |
|---|---|---|---|
| P10-INFRA-001 | No Android target available | Empty `adb devices -l` | BLOCKED |
| P10-INFRA-002 | No usable accelerated emulator | `/dev/kvm` unavailable | BLOCKED |
| P10-INFRA-003 | No runner/device-farm target | No configured resource found | BLOCKED |

No application, network, endpoint, or device/OS bug was reproduced. The run stopped before those categories could be tested.

## Fixes

**Production code changes: NONE.** Existing Phase 5–9 tooling was reused. No harness modification, speculative fix, or source refactor was made.

## Regression

No source files changed during Phase 10. The inherited Phase 9 branch already had successful automated quality and CI evidence. Re-running static/build gates would not unblock the explicitly absent runtime device and was intentionally not used to manufacture progress.

## Evidence

Actual Phase 10 evidence:

- ADB version output;
- empty `adb devices -l` output;
- `/dev/kvm` unavailable;
- Phase 9 device preflight exit code 2;
- no device identity or runtime artifacts.

No fake logcat, fake interface, fake handshake, fake traffic, or fake screenshot was created. No secret was stored in artifacts.

## Limitations

The current sandbox cannot provide or authorize a physical Android device and has no remote device-farm target or persistent self-hosted runner. The first real-device chain cannot proceed until an operator supplies one authorized device and a trusted host.

The next execution must begin with:

```text
adb devices -l
→ exactly one <serial>    device
```

Only then should the existing Phase 9 activation script and Phase 5 harness run. Full Phase 10 matrix execution is permitted only after core smoke passes with VpnService, interface, fresh handshake, actual HTTPS/DNS, disconnect, reconnect, fresh handshake #2, and actual traffic #2 evidence.

## Final Status

**DEVICE: BLOCKED**

**ADB: BLOCKED**

**DEVICE HEALTH: UNVERIFIED**

**APK INSTALL: UNVERIFIED**

**APP LAUNCH: UNVERIFIED**

**VPN SERVICE: UNVERIFIED**

**VPN INTERFACE: UNVERIFIED**

**FRESH HANDSHAKE: UNVERIFIED**

**ACTUAL HTTPS: UNVERIFIED**

**DNS: UNVERIFIED**

**DISCONNECT: UNVERIFIED**

**RECONNECT: UNVERIFIED**

**SMOKE: UNVERIFIED**

**FULL RUNTIME: BLOCKED**

**AUTOMATED QUALITY: PASS (inherited from Phase 9; no source changes in Phase 10)**

**RUNTIME INFRASTRUCTURE: BLOCKED**

**RUNTIME QUALITY: UNVERIFIED**

**RELEASE READINESS: NOT READY**

## Next Phase

Provide one dedicated Android test device and one trusted host or approved device-farm target. Authorize ADB, run the existing preflight, then execute only the first smoke chain. Stop and triage on any failure; do not start the full matrix until the core chain passes.
