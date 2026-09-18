# Velum Phase 8 Device Provisioning

This document is an operational checklist for activating one trusted physical Android test device and one restricted self-hosted runner. It does not claim that either resource is currently available.

## 1. Dedicated test device

Use a device that contains no personal accounts, banking applications, private files, password stores, production VPN configuration, or sensitive user data. Keep it unlocked during onboarding and connect it to a trusted host by USB.

On the device:

1. Enable Developer Options.
2. Enable USB debugging.
3. Keep the device unlocked.
4. Accept the host RSA debugging authorization.
5. Do not factory-reset or delete unrelated user data as part of onboarding.

On the host:

```bash
adb start-server
adb devices -l
```

Continue only when exactly one serial appears with state `device`. Reject empty, `offline`, `unauthorized`, and multiple-device results.

## 2. Health and identity gate

Run the Phase 7/8 preflight before installing Velum:

```bash
scripts/phase5/runtime.sh preflight artifacts/phase8/device
```

The gate checks ADB state, boot completion, shell execution, package manager, connectivity service, and captures minimal device identity. Review the generated files for personal or sensitive information before sharing.

## 3. Trusted runner

Use a dedicated host that can reach GitHub and remains online while the workflow runs. Install the repository-compatible JDK, Android SDK platform-tools, API 36/build-tools 36.0.0, and Gradle support. Ensure `adb`, `aapt`, and Gradle are available to the runner service, not only to an interactive shell.

Register the runner only in a restricted runner group and apply these labels:

```text
self-hosted
android
velum-runtime
```

Do not expose the runner to arbitrary pull requests, forks, or untrusted repository workflows. The Velum runtime workflow is intentionally `workflow_dispatch`-only and uses concurrency locking.

Never commit or print runner registration tokens, ADB private keys, device-farm credentials, signing keys, or passwords.

## 4. First smoke path

After health passes, invoke the manual workflow `.github/workflows/velum-real-device-runtime.yml`. It builds the actual debug APK, discovers package/activity metadata from the APK, installs it, launches a smoke test, collects evidence even on failure, and performs conservative cleanup.

A launch smoke PASS is not a VPN PASS. The next gate must prove VpnService state, VPN interface, fresh handshake, actual HTTPS traffic, and disconnect before the full Phase 6 matrix is started.

## 5. Network and cleanup policy

Use non-destructive host and device connectivity checks. Do not permanently change airplane mode, routing, accounts, or global settings. After testing, stop Velum, disconnect the VPN, remove only test-created temporary files, collect redacted evidence, and leave the device usable. Do not factory-reset or delete unrelated user data.

## 6. Current status

The current sandbox has no authorized Android device, no online self-hosted runner, no configured device farm, and no `/dev/kvm`. Therefore this checklist is prepared but cannot be completed here.
