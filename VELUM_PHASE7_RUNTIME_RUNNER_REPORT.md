# Velum Phase 7 Runtime Runner Report

## Objective

Phase 7 prepares a persistent, repeatable Android runtime path for eventually executing the Phase 6 real-device matrix. The preferred design is one dedicated physical Android device connected to a trusted self-hosted GitHub Actions runner. An approved device farm remains an alternative only after a real provider and its VPN/network capabilities are verified.

This phase does not claim that a device or runner is currently available.

## Infrastructure

The current sandbox is not a persistent Android runner:

- Linux x86_64, kernel 6.18.38+.
- Android SDK installed at `/home/ubuntu/.android/sdk`.
- ADB platform-tools 37.0.1 available.
- Emulator binary available.
- `sdkmanager` is not on `PATH`.
- `/dev/kvm` is unavailable.
- `adb devices -l` is empty.
- No USB physical device is attached.
- No remote ADB endpoint, device-farm provider, or self-hosted runner is configured.

The Phase 5 runtime harness remains the single runtime logic entry point and has been extended rather than duplicated.

## Runner Host

**Current runner:** unavailable.

The intended host must be a dedicated, always-on Linux/macOS/Windows machine with:

- GitHub connectivity;
- repository-compatible JDK and Gradle access;
- Android SDK platform-tools, API 36, and build-tools 36.0.0;
- `adb`, `aapt`, and Gradle on the service `PATH`;
- one authorized Android device;
- runner labels `self-hosted`, `android`, and `velum-runtime`.

Runner registration and device credentials must be provisioned outside the repository. No runner token or secret was requested, printed, or stored.

## Android Device

**Current device:** none.

A valid future target must be exactly one ADB serial in state `device`, boot-completed, shell-accessible, and package-manager-accessible. Offline, unauthorized, empty, or multiple-device states are rejected.

## ADB

Phase 7 discovery executed:

```text
adb version
Android Debug Bridge version 1.0.41
Version 37.0.1-15733141

adb devices -l
List of devices attached
```

No valid serial exists. `/dev/kvm` is also absent, so the existing local AVDs are not a viable replacement. The current result is **ADB BLOCKED**, not a test pass.

## SDK

- `ANDROID_HOME=/home/ubuntu/.android/sdk`.
- Platform-tools 37.0.1.
- API 36 platform and build-tools 36.0.0 installed.
- Repository-compatible JDK 17 remains installed and was used for Gradle gates.

The self-hosted runner workflow preserves a runner-provided `ANDROID_HOME`/`ANDROID_SDK_ROOT`; it does not overwrite those values with an empty repository variable.

## Device Health

No device health command was run against a nonexistent target. The new preflight path will require:

1. ADB available and server startable;
2. exactly one serial in state `device`;
3. `sys.boot_completed=1`;
4. a working `adb shell` probe;
5. a working Android package-manager probe;
6. a working connectivity-service probe;
7. device identity capture.

The preflight exits with code 2 on infrastructure failure and writes no synthetic identity.

## GitHub Actions Integration

Added `.github/workflows/velum-real-device-runtime.yml` with the following controls:

- `workflow_dispatch` only; no push or pull-request execution on trusted hardware;
- self-hosted labels `self-hosted`, `android`, `velum-runtime`;
- repository-level concurrency lock to prevent two jobs sharing one device;
- strict preflight before build/install;
- actual APK build and package/activity discovery from the APK;
- install and launch smoke test through `scripts/phase5/runtime.sh`;
- evidence collection with `if: always()`;
- cleanup without uninstalling packages, deleting user data, or factory-resetting the device;
- artifact upload with a bounded retention period.

The workflow is **READY FOR DEVICE**, but it has not run because no matching self-hosted runner exists. A successful workflow run would still be only launch/smoke evidence; full VPN success requires the Phase 6 chain.

## Device Farm Integration

**NOT CONFIGURED.** No provider, endpoint, or credential is available. No adapter was implemented because an abstraction without a real provider would falsely suggest readiness.

A future provider must be evaluated for APK upload/install, device selection, shell or equivalent access, log retrieval, VPN interface inspection, actual HTTPS/DNS traffic, disconnect/reconnect, network transitions, and artifact download. Provider limitations must be reported rather than hidden.

## Security

The self-hosted runner is treated as trusted, high-risk infrastructure:

- runtime workflow is manual-only;
- fork and pull-request execution is not enabled;
- runner labels are explicit;
- concurrency prevents device races;
- no secrets are embedded in workflow or scripts;
- log redaction remains in the Phase 5 harness;
- artifacts are retained only for a bounded period;
- cleanup is conservative and does not erase user data;
- operators must review artifacts for credentials, private keys, authorization headers, private VPN configuration, and personal data before sharing.

Production code was not changed.

## Smoke Test

**UNVERIFIED / BLOCKED.** The harness preflight was executed in the current sandbox and correctly rejected zero devices with exit code 2. APK installation and launch smoke testing require the future self-hosted runner/device.

The smoke workflow will not claim VPN success from a launch result. It must be followed by service, interface, handshake, traffic, disconnect, and reconnect evidence.

## Full Runtime Validation

**UNVERIFIED.** No Phase 6 runtime scenario could execute. The following remain pending on a real device:

- VPN permission;
- WireGuard GoBackend VpnService state;
- VPN interface and route snapshots;
- fresh handshake for two sessions;
- actual HTTPS and DNS traffic;
- disconnect/reconnect;
- lifecycle and background/foreground;
- notification and TileService;
- network transitions and airplane mode;
- endpoint failover;
- boot recovery;
- process death;
- encrypted storage persistence;
- R8/release-like runtime;
- stress and stability.

## Artifacts

The workflow is configured to collect, even on failure:

- `adb devices -l` state;
- device identity;
- install/package state;
- launch result;
- redacted logcat;
- package dumpsys;
- connectivity dumpsys;
- `ip addr` and `ip route`;
- service state;
- airplane-mode state;
- runtime evidence directory.

No runtime artifact exists from this sandbox because no device was available. No secret or private VPN configuration was added to the repository.

## Failures

| ID | Failure | Classification | Evidence | Status |
|---|---|---|---|---|
| P7-INFRA-001 | No physical/remote Android device | Infrastructure blocker | Empty `adb devices -l` | BLOCKED |
| P7-INFRA-002 | No hardware acceleration | Infrastructure blocker | `/dev/kvm` absent | BLOCKED |
| P7-INFRA-003 | No configured self-hosted runner/device farm | Infrastructure blocker | Runner/farm discovery found none | BLOCKED |

No Velum application failure was inferred from these infrastructure failures.

## Fixes

No production-code fix was made. Infrastructure-only changes:

- extended `scripts/phase5/runtime.sh` with strict ADB preflight, identity capture, package-manager/connectivity health checks, and conservative cleanup;
- added `.github/workflows/velum-real-device-runtime.yml`;
- added `docs/phase7-persistent-runtime.md` onboarding and security documentation.

## Automated Verification

All required gates passed after these changes:

| Gate | Result |
|---|---|
| `testDebugUnitTest` | PASS |
| `lintDebug` | PASS |
| `assembleDebug` | PASS |
| `assemblePreview` / R8 | PASS |
| `check` | PASS |
| Harness syntax (`bash -n`) | PASS |
| Empty-device preflight fail-fast | PASS; expected exit 2 |

## Limitations

The sandbox cannot become a physical-device runner through repository changes. A maintainer must provide the host, register the self-hosted runner with labels, connect and authorize one Android device, and run the workflow manually. Until then, the workflow is prepared but runtime infrastructure remains blocked.

A GitHub-hosted runner is not an acceptable substitute for a real device. Local AVD names are not evidence of a usable emulator, and the missing `/dev/kvm` blocker is unchanged.

## Final Status

## CI

The Phase 7 build workflow completed successfully: [run 35377453860](https://github.com/velum-tunnel/velum/actions/runs/35377453860), including unit tests, debug build, preview/R8, lint, release signing/verification, and artifact upload. The documentation workflow also completed successfully: run `35377454007`.

**RUNNER: BLOCKED**

No self-hosted runner is registered or reachable for this repository.

**DEVICE: BLOCKED**

No physical, remote, farm, or usable accelerated emulator target exists.

**DEVICE TYPE: None**

**ADB: BLOCKED**

ADB is installed, but no target is in state `device`.

**DEVICE HEALTH: UNVERIFIED**

No target exists against which health can be measured.

**GITHUB ACTIONS RUNTIME: BLOCKED**

The workflow template is READY FOR DEVICE, but no matching runner exists and no runtime job can execute.

**DEVICE FARM: NOT CONFIGURED**

No provider or credential path is available.

**APK INSTALL: UNVERIFIED**

APK build is available, but no target exists for installation.

**SMOKE TEST: UNVERIFIED**

The harness preflight blocks before launch.

**VPN CORE RUNTIME: UNVERIFIED**

No service, interface, fresh handshake, or traffic evidence exists.

**FULL RUNTIME MATRIX: UNVERIFIED**

The Phase 6 matrix remains pending.

**AUTOMATED QUALITY: PASS**

**RUNTIME INFRASTRUCTURE: BLOCKED**

**RUNTIME QUALITY: UNVERIFIED**

**RELEASE READINESS: NOT READY**

**BUGS FOUND:** 3 confirmed infrastructure blockers; 0 confirmed Velum runtime bugs.

**BUGS FIXED:** 0 production bugs; infrastructure harness/workflow prepared.

## Recommendation for Phase 8

Provision one trusted self-hosted runner and one authorized Android device, run the manual smoke workflow, and only after its layered VPN evidence passes execute the full Phase 6 runtime matrix. Phase 8 should not repeat static audits; it should consume the persistent runner to collect actual VPN runtime evidence and address only reproducible runtime defects.
