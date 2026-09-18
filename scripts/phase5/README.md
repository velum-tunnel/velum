# Phase 5 Android Runtime Harness

`runtime.sh` is a reusable ADB harness for a maintainer, self-hosted runner, or device-farm worker. It **fails closed** unless exactly one authorized device appears in `adb devices` with state `device`; `offline`, `unauthorized`, empty output, and multiple devices are rejected.

## Prerequisites

The operator must provide a real Android device, USB debugging, and an authorized ADB connection. This repository does not contain device credentials or a device-farm endpoint. The harness never creates fake device output and never treats a successful APK build as runtime evidence.

## Typical sequence

```bash
adb start-server
scripts/phase5/runtime.sh detect_device
scripts/phase5/runtime.sh verify_device
scripts/phase5/runtime.sh build_apk debug
scripts/phase5/runtime.sh install_apk app/build/outputs/apk/debug/<actual-debug-apk>.apk <actual-package-id>
scripts/phase5/runtime.sh launch_app <actual-package-id> <launcher-activity>
scripts/phase5/runtime.sh run_runtime_smoke_test <actual-package-id> <launcher-activity> evidence/smoke
scripts/phase5/runtime.sh collect_artifacts evidence/full <actual-package-id>
```

For a release-like artifact, use `build_apk preview` and install the actual APK path printed by the build command. Determine the package ID and launcher Activity from the built manifest rather than guessing.

## Evidence and privacy

The harness captures logcat, package state, connectivity, IP address/route, service state, and airplane-mode state into an operator-selected directory. Log output is filtered for common bearer-token, private-key, token, and unusually long-value patterns, but the operator must review artifacts before committing or sharing them. Do not place private configuration, credentials, or keystores in the evidence directory or repository.

`run_runtime_smoke_test` verifies launch and scans logs for common crash markers only. It does **not** claim VPN success. VPN success requires separate evidence of the VpnService, VPN interface, fresh handshake, actual HTTPS/DNS traffic, disconnect, and reconnect.

## Device-farm CI

A self-hosted runner or managed device-farm integration may invoke this harness after securely provisioning ADB. GitHub-hosted runners must not be treated as real-device infrastructure. If no authorized device is available, the correct result is `RUNTIME INFRASTRUCTURE = BLOCKED`, not a skipped or synthetic PASS.
