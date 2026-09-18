# VELUM PHASE 2 VERIFICATION REPORT

## Environment

- **JDK:** OpenJDK 17.0.20 (`/usr/lib/jvm/java-17-openjdk-amd64`), installed from the Ubuntu package repository because the repository CI explicitly uses JDK 17.
- **Android SDK:** User-local SDK at `/home/ubuntu/.android/sdk`; Android CLI 1.0.16261425 from the official Google Android CLI installer. Installed packages: `platform-tools 37.0.1`, `platforms;android-36 2.0.0`, and `build-tools;36.0.0`.
- **Gradle:** Wrapper Gradle 9.7.1, verified with `./gradlew --version`.
- **Emulator/device:** None available. `/dev/kvm` was not available; no runtime/device claims are made.

No repository dependency versions were changed. The SDK was installed project/user-scoped and no global project configuration was modified.

## Existing fixes

### Listener race

**PASS at source and unit-test level.** `VelumController` now retains its callback identity and calls `VelumTunnel.clearListenerIfCurrent()`. `VelumListenerSlot` synchronizes replacement and conditional clear, so Controller A cannot clear Controller B’s listener after Activity recreation. Regression coverage is provided by `VelumListenerSlotTest`, including stale-owner and current-owner cases.

### Notification permission

**PASS at policy/unit-test level; device dialog behavior unverified.** `notificationPermissionRequested` is persisted in encrypted preferences. The pure policy requests permission only on API 33+, when not already granted, and before the first prompt. `VelumNotificationPermissionTest` covers pre-Android 13, granted, first request, and denied-after-first-request states.

## Storage

### Residual risk

The previous `Prefs.open()` implementation deleted `velum.xml` after any first `Exception`, then retried. The resolved `androidx.security:security-crypto:1.1.0` API declares `EncryptedSharedPreferences.create()` with `GeneralSecurityException` and `IOException`. Those broad classes do not prove unrecoverable encrypted-file corruption and can represent keystore unavailability, key invalidation, transient platform failure, or I/O failure. Deleting at that boundary could permanently remove the private key and registration token.

### Status

**Fixed at code level; Android-specific corruption/failure injection remains unverified.** `Prefs.open()` now retries once without deleting the encrypted preferences file. If the second attempt fails, it throws `KeystoreUnavailableException`; there is still no plaintext fallback. Legacy migration continues to use `File` for the separate historical `warp.xml` file and was not changed.

### Evidence

- Resolved `EncryptedSharedPreferences.create()` bytecode was inspected from the actual Gradle-resolved AAR and confirmed to declare broad `GeneralSecurityException`/`IOException` failure paths.
- The destructive `deleteEncryptedFile()` path was removed.
- `testDebugUnitTest` and all build/lint gates pass after the hardening.
- A real Android keystore invalidation/corruption reproducer was not available because no device/emulator exists.

## Local tests

| Test / gate | Result |
|---|---|
| `./gradlew testDebugUnitTest` | PASS |
| `./gradlew lintDebug` | PASS |
| `./gradlew assembleDebug` | PASS |
| `./gradlew assemblePreview` | PASS |
| `./gradlew check` | PASS |
| `git diff --check` | PASS |

The first post-hardening run exposed a missing `java.io.File` import; that compile error was fixed without changing behavior, and the complete suite was rerun successfully.

## Artifact validation

Both debug and preview builds produced ABI-specific and universal APKs. `aapt dump badging` confirmed compile SDK 36, distinct ABI version codes, and expected native libraries:

| Variant | Artifacts | Version codes | Native ABIs observed |
|---|---|---|---|
| Debug | arm64, armeabi-v7a, x86_64, universal | 3001, 1001, 2001, 1 | arm64-v8a, armeabi-v7a, x86_64; universal also contains x86 from the upstream native payload |
| Preview/R8 | arm64, armeabi-v7a, x86_64, universal | 3001, 1001, 2001, 1 | arm64-v8a, armeabi-v7a, x86_64; universal also contains x86 from the upstream native payload |

`assemblePreview` passed with shrinker/R8 enabled. No broad `-keep class ** { *; }` rule was added or enabled.

## Device tests

No emulator or physical device was available. All scenarios below remain **UNVERIFIED RUNTIME**:

| Scenario | Result |
|---|---|
| Launch | UNVERIFIED RUNTIME |
| Register | UNVERIFIED RUNTIME |
| Connect | UNVERIFIED RUNTIME |
| Disconnect | UNVERIFIED RUNTIME |
| Reconnect | UNVERIFIED RUNTIME |
| Rotation/recreation | UNVERIFIED RUNTIME |
| Permission denial | UNVERIFIED RUNTIME |
| Boot recovery | UNVERIFIED RUNTIME |
| Endpoint rotation | UNVERIFIED RUNTIME |
| Manual endpoint | UNVERIFIED RUNTIME |

## CI

- **Workflow:** `.github/workflows/build.yml` (`verify` job), plus CodeQL and documentation workflows.
- **Commit:** `c1d657cafe1ff46c065cd839e9d70c0072e97c0b`.
- **Runs:** [build run 35334161270](https://github.com/velum-tunnel/velum/actions/runs/35334161270) and [documentation run 35334161275](https://github.com/velum-tunnel/velum/actions/runs/35334161275).
- **Final CI status:** SUCCESS. The build workflow passed unit tests, debug APK, preview/R8 APK, lint, release APK signing/verification, and artifact upload. The documentation workflow also passed.

## Git

- **Branch:** `audit/phase2-lifecycle-storage`
- **Commit:** `c1d657c` (`fix: harden audited lifecycle and storage handling`).
- **Pushed:** yes, to the audit branch; `main` was not modified directly.

## Static security review

- `MainActivity` is exported for launcher use.
- `VelumTileService` is exported with `BIND_QUICK_SETTINGS_TILE`.
- `BootReceiver` remains exported for system boot/package replacement compatibility and is a residual review item for maintainer/device validation.
- `AppExclusionActivity` and WireGuard VpnService are non-exported.
- `android:allowBackup="false"` is enabled.
- Network security configuration disallows cleartext traffic for configured domains; source-level certificate pinning and endpoint validation were inspected.
- Private key and token paths remain in `EncryptedSharedPreferences`; no plaintext fallback was added.
- Diagnostics tests assert that token/private-key material is not included in copied diagnostics.
- Secret-pattern scan found no private keys, access tokens, or credential blobs in source, resources, tests, or the diff. Test fixtures contain only short synthetic values such as `tok-abc`.
- R8 rules remain scoped to WireGuard, Tink generated message fields, manifest components, and diagnostics attributes.

## Remaining Risks

1. Android device/emulator runtime behavior remains unverified, including Activity recreation on a real lifecycle, notification denial behavior, BootReceiver timing, Quick Settings Tile races, VPN permission loss, endpoint rotation, and network changes.
2. The storage hardening prevents destructive deletion on unclassified failures, but no device-level reproducer proves how each Android Keystore failure class surfaces on target API levels.
3. `BootReceiver` is still exported and should be validated on target Android versions before any manifest tightening; changing it without testing system broadcasts could break boot/update recovery.
4. Runtime/device verification remains optional but should be completed by a maintainer before relying on VPN, boot, and system-dialog behavior in production.

## FINAL STATUS

# PASS

All mandatory local gates and release-like artifact checks passed. Both required GitHub Actions workflows completed successfully on the pushed audit commit. Device verification was not available, but it is optional in this phase and is explicitly recorded as `UNVERIFIED RUNTIME`; no device behavior is being claimed as tested.
