# Velum Phase 9 Device Activation Runbook

This is the shortest supported path from an operator-provided Android device to the first Velum launch smoke test. It does not create a fake device and it stops before runtime when preflight fails.

## Hardware needed

Use one dedicated Android test device and one trusted host. The device should contain no personal accounts, banking data, private files, password stores, production credentials, or sensitive VPN configuration. Use a reliable USB cable for Mode A. Wireless Mode B requires Android 11 or newer, Wireless Debugging, and the device and host on the same trusted network.

## Host requirements

The host needs Git, Java, Android SDK platform-tools, Android API 36/build-tools 36.0.0, network access to GitHub, and a checkout of this repository. The repository-compatible JDK 17 is preferred. The host must be trusted because the runtime workflow and device are privileged test infrastructure.

Run:

```bash
scripts/phase9/host_preflight.sh
```

The script checks `adb`, Java, Git, the checkout, `aapt` availability, and GitHub connectivity. It never prints secret environment values.

## Mode A — USB ADB

On the device:

1. Unlock the device.
2. Enable Developer Options.
3. Enable USB debugging.
4. Connect the USB cable to the trusted host.
5. Accept the RSA debugging authorization prompt.

On the host:

```bash
scripts/phase9/adb_connect.sh usb
```

Expected output contains exactly one serial with state `device`. `unauthorized`, `offline`, empty output, and multiple devices are failures.

## Mode B — Wireless Debugging

On Android 11+, enable **Developer Options → Wireless debugging**. Keep the host and device on the same trusted network. Use the pairing address/port and the separate connection address/port shown by Android:

```bash
scripts/phase9/adb_connect.sh wireless <device-host:connect-port> <device-host:pair-port>
```

The script prompts for the one-time pairing code without saving or echoing it, runs `adb pair`, then `adb connect`. Do not use insecure legacy TCP exposure or forward ports beyond the trusted network without an explicit security design.

## Device health

Once ADB shows exactly one target, run:

```bash
scripts/phase9/device_preflight.sh artifacts/phase9/device
```

The gate checks boot completion, shell execution, package manager, connectivity service, and captures minimal identity. It exits 2 on zero, unauthorized, offline, multiple, unbooted, or unhealthy devices.

## First activation

Run the deterministic activation script:

```bash
scripts/phase9/runtime_activate.sh artifacts/phase9
```

It performs host preflight, device preflight, `assembleDebug`, actual APK/package/activity discovery, install, launch/log smoke testing, evidence collection, and conservative cleanup. It does not claim VPN success. VPN smoke is PASS only after separate evidence proves VpnService state, VPN interface, fresh handshake, actual HTTPS traffic, and disconnect.

If the activation script fails, stop before the full matrix. Preserve `artifacts/phase9`, classify the failure as infrastructure or application, reproduce it, and inspect logs before changing code.

## Trusted runner option

If the host is safe for GitHub Actions, register a restricted self-hosted runner with labels:

```text
self-hosted
android
velum-runtime
```

Use the existing manual-only workflow `.github/workflows/velum-real-device-runtime.yml`. Do not enable pull-request or fork execution on the trusted runner. The workflow has concurrency locking and uploads artifacts even on failure.

## Expected readiness evidence

A ready activation host must show:

```text
adb devices -l
<one-serial>    device
adb shell getprop sys.boot_completed
1
adb shell echo VELUM_PHASE9_DEVICE_READY
VELUM_PHASE9_DEVICE_READY
```

It must also have successful package-manager and connectivity probes, a successful APK installation, and a launch smoke artifact. UI Connected alone is never VPN evidence.

## Cleanup and privacy

After testing, disconnect Velum, stop the package, remove only test-created temporary files, and collect redacted evidence. Do not factory-reset the device, uninstall unrelated applications, delete personal data, or commit logcat containing credentials, tokens, private keys, authorization headers, or private VPN configuration.

## Current environment

The current sandbox has no Android device and no connected host runner. Its ADB output is only `List of devices attached`; therefore activation is prepared but blocked until the operator provides hardware and a trusted host.
