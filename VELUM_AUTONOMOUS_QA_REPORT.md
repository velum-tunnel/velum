# VELUM AUTONOMOUS QA REPORT

## 1. Baseline

Repository yang diaudit adalah `velum-tunnel/velum` pada branch `main`, commit `4f54158` (`fix: enforce verified connections and atomic recovery intent`). Tidak ada `AGENTS.md` aktif di root; `README.md` menyatakan bahwa `docs/archive/AGENTS.md` bersifat historis. Aturan aktif yang dibaca adalah `README.md` dan `docs/autonomous-toolchain-policy.md`, bersama konfigurasi Gradle dan seluruh workflow GitHub Actions.

| Quality gate | Hasil aktual | Bukti / alasan |
|---|---|---|
| `./gradlew testDebugUnitTest` | BLOCKED | Gradle berhenti sebelum task berjalan: Android SDK tidak ditemukan (`SDK location not found`; tidak ada `ANDROID_HOME`, `ANDROID_SDK_ROOT`, atau `local.properties`). |
| `./gradlew lintDebug` | BLOCKED | Hambatan SDK yang sama; lint tidak dieksekusi. |
| `./gradlew assembleDebug` | BLOCKED | Hambatan SDK yang sama; kompilasi tidak dieksekusi. |
| `./gradlew assemblePreview` | BLOCKED | Hambatan SDK yang sama; R8/release-like build tidak dieksekusi. |
| `./gradlew test` | BLOCKED | Hambatan SDK yang sama. |
| `./gradlew check` | BLOCKED | Hambatan SDK yang sama. |

Baseline dan post-fix verification sama-sama berhenti pada discovery SDK, bukan pada kegagalan source atau test. Karena itu tidak ada klaim bahwa kode berhasil dikompilasi, unit test berhasil, lint berhasil, atau APK berhasil dibangun.

## 2. Findings

| ID | Severity | Confidence | Area | Root Cause | Status |
|---|---|---|---|---|---|
| VELUM-001 | HIGH | CONFIRMED | Activity lifecycle / listener ownership | `VelumController.destroy()` menghapus `VelumTunnel.listener` secara unconditional. Controller lama yang dihancurkan setelah Activity recreation dapat menghapus listener milik controller baru. | Fixed, regression test added |
| VELUM-002 | MEDIUM | CONFIRMED | Notification permission policy | `MainActivity.onCreate()` meminta `POST_NOTIFICATIONS` setiap kali permission masih ditolak, tanpa state persisted bahwa prompt sudah pernah ditampilkan. | Fixed, regression test added |
| VELUM-003 | HIGH | CONFIRMED by source inspection; runtime unverified | Encrypted preferences recovery | `Prefs.open()` menangkap semua `Exception`, menghapus file encrypted preferences, lalu mencoba membuka ulang. Kode tidak membedakan corruption dari temporary keystore/platform failure, sehingga error non-corruption dapat menyebabkan data registration hilang. | Not changed; requires Android-specific failure-injection seam and device/SDK verification |
| VELUM-004 | MEDIUM | UNVERIFIED RUNTIME | Boot recovery lifetime | `BootReceiver` menjalankan blocking recovery dalam `goAsync()` dan sendiri mendokumentasikan kemungkinan DNS retry melampaui budget receiver. Durasi dicatat, tetapi tidak ada perangkat untuk mengukur perilaku nyata. | Unverified; manual device verification required |
| VELUM-005 | MEDIUM | UNVERIFIED RUNTIME | Handshake freshness | `awaitHandshake()` hanya memeriksa `latestHandshakeMs > 0`; korelasi timestamp dengan session tunnel tidak dapat dibuktikan dari unit test yang tersedia atau runtime tanpa backend/device. | Unverified; no speculative fix applied |

## 3. Confirmed Bugs

### VELUM-001 — stale Activity dapat menghapus listener Activity baru

**Symptom.** Setelah Activity lama dihancurkan dan Activity baru memasang listener, penghancuran controller lama dapat membuat callback global menjadi `null`. UI Activity baru kemudian tidak menerima perubahan state tunnel.

**Root cause.** Sebelum perubahan, `VelumController.destroy()` menjalankan `VelumTunnel.listener = null`. Listener disimpan sebagai callback global proses, sedangkan controller dibuat per-Activity. Urutan `new listener -> old destroy -> null` valid saat recreation.

**Reproducer.** Buat slot listener dengan owner lama, pasang owner baru, lalu jalankan operasi clear milik owner lama. Pada implementasi sebelum fix, listener baru hilang.

**Affected files.** `VelumController.kt` dan `VelumTunnel.kt`.

**Fix.** Menambahkan `VelumListenerSlot`, menyimpan identity callback di setiap controller, dan mengganti clear unconditional dengan `VelumTunnel.clearListenerIfCurrent(tunnelListener)`. Owner lama tidak dapat menghapus callback yang sudah digantikan.

**Regression test.** `VelumListenerSlotTest.staleOwnerCannotClearReplacementListener` dan `currentOwnerCanClearItsListener`.

### VELUM-002 — permission notification dapat diminta berulang setelah denial

**Symptom.** Pada Android 13+, setiap recreation atau pembukaan Activity ketika permission masih denied dapat memanggil `launch(POST_NOTIFICATIONS)` lagi.

**Root cause.** Kebijakan sebelumnya hanya memeriksa `granted`; tidak ada state persisted untuk membedakan first request dari denial yang sudah pernah dipresentasikan.

**Reproducer.** Jalankan Activity pada API 33+, tolak permission, hancurkan/buat ulang Activity, lalu amati bahwa jalur request tetap memenuhi kondisi `!granted`.

**Affected files.** `MainActivity.kt` dan `Prefs.kt`.

**Fix.** Menambahkan `notificationPermissionRequested` pada encrypted preferences dan policy pure `VelumNotificationPermission.shouldRequest()`. Flag ditulis sebelum prompt pertama; permission yang sudah granted maupun denial setelah prompt pertama tidak memicu prompt tambahan.

**Regression test.** `VelumNotificationPermissionTest` mencakup API lama, granted, first request, dan denied-after-first-request.

## 4. Potential Bugs Investigated

### Fixed

`VelumController` listener ownership dan notification permission request policy telah diperbaiki dengan perubahan minimal serta test seam pure JVM.

### Rejected / no change

Tidak ada perubahan spekulatif pada handshake, endpoint probing, reconnect, R8, BootReceiver, atau encrypted-storage recovery. Source menunjukkan sejumlah guard yang memang sudah ada: intent generation global, handshake verification sebelum accepted connection, VPN transport filtering, endpoint working evidence, atomic registration write, dan `allowBackup="false"`. Mengubah area tersebut tanpa runtime/device proof akan berisiko mengubah semantik produk tanpa bukti yang cukup.

### Unverified

Device/emulator tidak tersedia. Dengan demikian boot timing, notification dialog system behavior, VPN permission loss, process recreation nyata, TileService lifecycle, WireGuard handshake freshness, R8 runtime reflection/JNI behavior, dan ABI installability tidak dinyatakan berhasil atau gagal melalui runtime.

## 5. Tests Added

| Test | Tujuan |
|---|---|
| `VelumListenerSlotTest` | Mengunci ownership callback dan mencegah stale owner menghapus replacement listener. |
| `VelumNotificationPermissionTest` | Mengunci kebijakan one-time prompt untuk Android 13+ setelah denial. |

## 6. Tests Executed

Perintah berikut dijalankan sebelum perubahan dan kembali dijalankan setelah perubahan:

```text
./gradlew testDebugUnitTest
./gradlew lintDebug
./gradlew assembleDebug
./gradlew assemblePreview
./gradlew test
./gradlew check
```

Semua command berhenti sebelum task dapat dieksekusi karena Android SDK tidak tersedia di sandbox. Tidak ada test yang secara jujur dapat dilaporkan PASS. `git diff --check` berhasil setelah perubahan.

## 7. Runtime Verification

| Kategori | Status |
|---|---|
| Emulator/device verified | Tidak tersedia pada sandbox; tidak ada klaim runtime. |
| Unit test / build verification | BLOCKED sebelum kompilasi oleh Android SDK yang hilang. |
| Manual verification required | Activity recreation listener delivery; notification denial/recreation; boot recovery duration; VPN permission loss; TileService versus MainActivity races; R8 preview install/run; ABI APK installation. |

## 8. Files Changed

| File | Alasan |
|---|---|
| `app/src/main/java/com/rollinkxx/velum/VelumListenerSlot.kt` | Primitive ownership callback baru. |
| `app/src/main/java/com/rollinkxx/velum/VelumTunnel.kt` | Expose conditional listener clear. |
| `app/src/main/java/com/rollinkxx/velum/VelumController.kt` | Simpan callback identity dan clear hanya bila masih current. |
| `app/src/test/java/com/rollinkxx/velum/VelumListenerSlotTest.kt` | Regression test listener race. |
| `app/src/main/java/com/rollinkxx/velum/VelumNotificationPermission.kt` | Policy pure untuk keputusan request. |
| `app/src/main/java/com/rollinkxx/velum/Prefs.kt` | Persisted one-time prompt state. |
| `app/src/main/java/com/rollinkxx/velum/MainActivity.kt` | Terapkan policy dan catat prompt pertama. |
| `app/src/test/java/com/rollinkxx/velum/VelumNotificationPermissionTest.kt` | Regression test permission policy. |

Tidak ada secret, token, private key, keystore, credential, atau file `.env` yang dibaca, disalin, dicetak, atau diubah.

## 9. Security Review

Manifest memiliki `MainActivity` exported untuk launcher dan `VelumTileService` exported dengan permission `BIND_QUICK_SETTINGS_TILE`. `BootReceiver` masih `exported="true"` tanpa sender permission; ini adalah residual security risk yang perlu diverifikasi terhadap kebutuhan `BOOT_COMPLETED`/`MY_PACKAGE_REPLACED` pada perangkat target sebelum diperketat. `AppExclusionActivity` dan WireGuard VpnService dideklarasikan non-exported. `android:allowBackup="false"` aktif.

Kunci privat dan token diarahkan ke `EncryptedSharedPreferences`; tidak ada fallback plaintext. Namun VELUM-003 menunjukkan recovery path dapat menghapus encrypted file untuk exception class yang terlalu luas, sehingga data-loss risk masih terbuka. Logging menggunakan helper repository; tidak ada secret yang sengaja dicetak dalam perubahan ini. Clipboard diagnostics dirancang tanpa private key, token, device identity, atau user IP, tetapi tetap memuat endpoint PoP dan ringkasan status sesuai perilaku produk.

`network_security_config.xml`, certificate pinning, malformed response handling, endpoint validation, and cleartext policy were inspected at source level; runtime/TLS verification was not available in this environment.

## 10. Remaining Risks

Risiko terbesar yang belum dapat ditutup adalah Android SDK/device unavailability, sehingga seluruh mandatory Gradle gates tetap BLOCKED. VELUM-003 masih memerlukan desain exception classification dan failure-injection test yang berjalan pada Android framework. BootReceiver duration dan exported-receiver behavior memerlukan perangkat. Handshake timestamp freshness dan R8/JNI compatibility belum memiliki runtime proof. ABI split artifact contents juga belum dapat divalidasi tanpa successful APK builds.

Perubahan belum di-commit atau dipush; maintainer perlu menjalankan quality gates pada lingkungan dengan Android SDK yang kompatibel, lalu melakukan device verification sesuai checklist repository.

## 11. Final Gate

# BLOCKED

Alasan: mandatory build, unit-test, lint, preview/R8, dan check gates tidak dapat dijalankan karena Android SDK tidak tersedia; runtime/device proof juga tidak tersedia. Dua bug confirmed telah diperbaiki dan regression tests telah ditambahkan, tetapi prinsip repository melarang menyatakan PASS tanpa bukti quality gate yang dapat dijalankan.
