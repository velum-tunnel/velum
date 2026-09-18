# Velum Phase 8 Device Provisioning Report

## Objective

Phase 8 was intended to activate one secure real-Android path, verify ADB and device health, and execute the first real-device smoke test. The current environment still has no physical Android device, no online self-hosted runner, and no configured device farm. This phase therefore stops at the infrastructure boundary and does not modify production code or manufacture runtime evidence.

## Environment

- Linux x86_64, kernel 6.18.38+.
- Android SDK root: `/home/ubuntu/.android/sdk`.
- ADB platform-tools 37.0.1 available.
- Emulator binary available.
- Local AVD names exist, but `/dev/kvm` is unavailable and no emulator is a valid replacement for the missing device.
- No online self-hosted runner was found in the local environment.
- No device-farm endpoint, provider configuration, or credential path was found.

## Runner Host

The current sandbox is not a registered self-hosted GitHub Actions runner and cannot be promoted to one through repository changes. It lacks the physical device and persistent host boundary required for Phase 8.

A future trusted host must provide GitHub connectivity, the repository-compatible JDK/Gradle/Android SDK, ADB access, a dedicated test device, and runner service environment variables. Registration tokens and credentials must remain outside the repository.

## Android Device

**Unavailable.** No physical or approved remote Android device is attached to this environment.

The required discovery result was:

```text
List of devices attached
```

No serial appeared in state `device`.

## Device Identity

Not collected because no valid ADB target existed. Manufacturer, model, Android version/API, ABI, serial, fingerprint, unlock state, and UI automation capability remain unavailable.

## ADB

ADB itself is installed and starts successfully:

```text
Android Debug Bridge version 1.0.41
Version 37.0.1-15733141
```

However, ADB has no target. The Phase 7/8 preflight was executed:

```text
scripts/phase5/runtime.sh preflight /tmp/phase8-preflight
```

Actual result:

```text
ERROR: expected exactly one authorized ADB device in state 'device'; found 0
List of devices attached
preflight_exit=2
```

This is the intended fail-fast behavior. No device health or installation command was run against a nonexistent device.

## Device Health

**UNVERIFIED.** The health gate could not run because there was no target. On a future target it will require boot completion, shell execution, package-manager access, and connectivity-service access before installation.

## Network Health

Host-level network access is available for repository operations, but device network health is **UNVERIFIED** because no Android device is connected. No persistent device network settings were changed.

## Runner Registration

**BLOCKED.** No self-hosted runner is registered or available to this task. No runner token was requested, printed, stored, or committed.

## Runner Labels

The existing manual-only workflow is prepared for these explicit labels:

```text
self-hosted
android
velum-runtime
```

No labels were claimed as active because no runner is registered.

## Runner Security

The existing runtime workflow remains manual-only and does not accept arbitrary push, pull-request, or fork execution on trusted hardware. It uses explicit labels, concurrency locking, strict preflight, artifact retention, redacted evidence capture, and conservative cleanup.

A future runner should be restricted to this repository or trusted workflow group. It should not be exposed to untrusted external contributions. No secret, ADB private key, device-farm credential, signing key, password, token, or private VPN configuration was read or stored.

## Device Access Policy

A future device must be dedicated to testing and contain no personal accounts, banking data, private files, password stores, production credentials, or sensitive VPN configuration. The onboarding sequence is documented in `docs/phase8-device-provisioning.md`:

1. unlock device;
2. enable Developer Options;
3. enable USB debugging;
4. connect the trusted host;
5. accept RSA authorization;
6. require exactly one ADB serial in state `device`;
7. run the preflight health gate;
8. only then install and launch Velum.

## APK Build

All required automated gates passed after the Phase 8 documentation change:

| Gate | Result |
|---|---|
| `testDebugUnitTest` | PASS |
| `lintDebug` | PASS |
| `assembleDebug` | PASS |
| `assemblePreview` / R8 | PASS |
| `check` | PASS |
| Harness syntax and preflight fail-fast | PASS |

## APK Installation

**UNVERIFIED.** No APK was installed because no valid ADB target was available. The actual APK build path and package discovery remain available through the Phase 7 workflow, but installation cannot be claimed.

## App Launch

**UNVERIFIED.** No Android Activity was launched and no device logcat was captured.

## VPN Permission

**UNVERIFIED.** No Android VPN permission prompt, grant, denial, or cancellation behavior was observed.

## VpnService

**UNVERIFIED.** No runtime VpnService state was captured. Static source identification from previous phases is not runtime evidence.

## VPN Interface

**UNVERIFIED.** No device-side `ip addr` or `ip route` snapshot exists.

## Fresh Handshake

**UNVERIFIED.** No connection session ran, so no fresh handshake timestamp exists.

## HTTPS Traffic

**UNVERIFIED.** No actual device HTTPS request was initiated or observed through a VPN tunnel.

## Disconnect

**UNVERIFIED.** No VPN session was established, so disconnect behavior cannot be evaluated.

## Smoke Test

**UNVERIFIED / BLOCKED.** The smoke path correctly stops at preflight because zero devices are present. The first real-device smoke chain remains:

```text
ADB → health → install → launch → permission → connect → VpnService
→ VPN interface → fresh handshake → HTTPS → disconnect
```

Full Phase 6 validation must not begin until this smoke chain passes with layered evidence.

## Full Runtime Matrix

**UNVERIFIED.** No full runtime test was started. This correctly avoids conflating an infrastructure failure with an application failure.

## Device Farm Assessment

**NOT CONFIGURED.** No provider or credential path is available. No provider was selected merely because it might install APKs. Any future provider must demonstrate shell/log access, VPN support, interface visibility, routing, fresh handshake evidence, actual traffic, disconnect/reconnect, and artifact retrieval.

## Bugs

| ID | Title | Classification | Evidence | Status |
|---|---|---|---|---|
| P8-INFRA-001 | No Android device available | Infrastructure blocker | Empty `adb devices -l` | BLOCKED |
| P8-INFRA-002 | No self-hosted runner available | Infrastructure blocker | No registered/online runner | BLOCKED |
| P8-INFRA-003 | No hardware acceleration | Infrastructure blocker | `/dev/kvm` unavailable | BLOCKED |

No Velum runtime application bug was confirmed.

## Fixes

No production code changes were made. Phase 8 added only:

- `docs/phase8-device-provisioning.md`, the secure physical-device and runner onboarding checklist;
- this report.

The existing Phase 7 harness and manual-only workflow were reused without speculative refactoring.

## Evidence

Available evidence:

- host and ADB version output;
- empty `adb devices -l` output;
- `KVM_UNAVAILABLE` result;
- local AVD inventory;
- preflight failure with exit code 2;
- successful local Gradle gate results.

No device identity, logcat, dumpsys, VPN interface, handshake, traffic, screenshot, or runtime artifact exists because no device was available. No sensitive credential or private configuration was stored.

## Limitations

The sandbox cannot attach or authorize a physical Android device and cannot register a persistent self-hosted runner. GitHub-hosted CI build success is not real-device validation. Repeating known emulator attempts without `/dev/kvm` would not provide new evidence.

## Final Status

**DEVICE: BLOCKED**

No physical or approved remote device is available.

**RUNNER: BLOCKED**

No online self-hosted runner is registered.

**ADB: BLOCKED**

ADB is installed, but there is no device in state `device`.

**DEVICE HEALTH: UNVERIFIED**

No target exists for health checks.

**RUNNER SECURITY: PASS for design / UNVERIFIED operationally**

Manual-only execution, explicit labels, concurrency locking, conservative cleanup, and no secret exposure are documented. There is no live runner on which operational security can be exercised.

**SMOKE TEST: UNVERIFIED**

Preflight blocks before installation and launch.

**RUNTIME INFRASTRUCTURE: BLOCKED**

**RUNTIME QUALITY: UNVERIFIED**

**AUTOMATED QUALITY: PASS**

**RELEASE READINESS: NOT READY**

## Recommendation

Provide one dedicated test Android device and one restricted self-hosted runner. Authorize ADB, run `scripts/phase5/runtime.sh preflight artifacts/phase8/device`, then invoke the manual real-device workflow. Stop on any smoke failure, collect evidence, classify infrastructure versus application cause, and only after smoke PASS execute the full Phase 6 matrix.
