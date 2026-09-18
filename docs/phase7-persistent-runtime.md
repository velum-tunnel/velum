# Velum Phase 7 Persistent Runtime Setup

Phase 7 prepares a real-device execution path; it does not claim that a device is currently attached. The supported target is one physical Android device connected to a dedicated self-hosted runner, with an approved device farm as a future alternative.

## Physical-device path

Use a dedicated Linux host that remains online and can communicate with GitHub. Install the repository-compatible JDK, Android SDK platform-tools, Android API 36/build-tools 36.0.0, and the repository checkout. Connect one Android phone by USB, enable Developer Options and USB debugging, accept the RSA authorization prompt, and verify:

```bash
adb start-server
adb devices -l
adb shell getprop sys.boot_completed
adb shell pm path android
adb shell echo VELUM_RUNTIME_PREFLIGHT
```

The only valid result is exactly one serial with state `device`, boot completion `1`, a working shell, and a working package manager. `offline`, `unauthorized`, an empty list, or multiple devices are rejected. The runner should expose labels:

```text
self-hosted, android, velum-runtime
```

The operator should also set `ANDROID_HOME` or `ANDROID_SDK_ROOT` in the runner service environment and ensure `adb`, `aapt`, and Gradle are on `PATH`. Do not commit runner registration tokens, device-farm credentials, keystores, or private configuration.

## GitHub Actions workflow

`.github/workflows/velum-real-device-runtime.yml` is deliberately `workflow_dispatch`-only. It runs only on the labels above and uses repository-level concurrency so one job cannot use the same device simultaneously. The workflow performs:

1. strict one-device preflight and identity capture;
2. debug APK build and package/activity discovery from the actual APK;
3. APK installation;
4. launch/log smoke testing;
5. package, VPN, network, service, and log evidence collection;
6. cleanup without uninstalling packages or deleting user data;
7. artifact upload with `if: always()` so failures remain diagnosable.

This workflow is only a smoke path. A successful build or launch does not establish VPN functional success. The full Phase 6 matrix must still prove VpnService state, VPN interface, fresh handshake, actual HTTPS/DNS traffic, disconnect, reconnect, and second-session evidence.

## Security boundary

The runner is trusted infrastructure and must not accept arbitrary pull-request or fork execution. The workflow has no push or pull-request trigger. Keep the runner in a restricted group, use a dedicated device, review workflow changes, and avoid exposing secrets to runtime jobs. Evidence must be reviewed for tokens, private keys, authorization headers, private VPN configuration, and personal data before sharing.

## Device cleanup

The harness stops the package and removes only temporary probe files. It does not factory-reset the device, uninstall the application, clear user data, or change global network settings. A maintainer may perform any additional reset explicitly required by the test plan.

## Device-farm status

No provider, endpoint, or credential is configured in this repository or current environment. Therefore a device-farm adapter is **PREPARED conceptually, not READY**. A provider can be integrated only after its actual APK install, shell/log access, VPN support, interface inspection, network routing, disconnect/reconnect, and artifact capabilities are verified.

## Current environment status

Phase 7 discovery still finds no ADB device and no `/dev/kvm`. The local sandbox is therefore not a persistent runtime runner. The workflow and harness are ready for a properly provisioned self-hosted runner, but runtime validation remains blocked until one exists.
