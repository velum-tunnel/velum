# COMPREHENSIVE BUG & QUALITY AUDIT

**Tanggal audit:** 21 September 2026  
**Repository:** `velum-tunnel/velum`  
**Branch:** `main`  
**HEAD yang diaudit:** `45e72840eec5019b5fe0e0a20ed2946a8628929e`  
**Pendekatan:** static review, source review, unit test, debug build, R8 preview build, lint, artifact/checksum inspection, CI workflow review, dan penilaian eksplisit terhadap keterbatasan runtime.

> **Kesimpulan singkat.** Tidak ditemukan confirmed bug runtime baru dalam ruang lingkup yang dapat diverifikasi pada HEAD ini. Satu **confirmed CI quality defect** ditemukan: step lint di GitHub Actions menggunakan `continue-on-error: true`, sehingga kegagalan lint dapat menghasilkan job hijau walaupun `abortOnError = true` di Gradle. Runtime/device verification belum dapat dilakukan karena sandbox tidak memiliki perangkat, emulator, atau `adb`.

## 1. Executive Summary

Implementasi saat ini menunjukkan hardening yang cukup luas pada lifecycle VPN, verifikasi handshake, persistence kredensial, validasi endpoint, pembatasan respons jaringan, dan supply-chain artifact. Unit test lokal berhasil dengan **152 test, 0 failure, 0 error, dan 0 skipped**. `assembleDebug`, `assemblePreview` dengan R8, checksum AAR, kontrak notifikasi, dan gerbang konsistensi dokumentasi juga berhasil. Lint berhasil dengan **0 error dan 46 warning**.

Temuan utama bukan kegagalan fitur pengguna yang terkonfirmasi, melainkan kelemahan pada confidence pipeline. Workflow build secara eksplisit menjadikan lint advisori dan meneruskan error lint sebagai sukses. Hal ini dapat menyembunyikan regresi lint/security correctness dari status CI. Selain itu, beberapa area penting tetap berstatus **NOT VERIFIED**, terutama perilaku `VpnService` pada device nyata, OEM Android, network transition, Doze, Always-on/Lockdown VPN, Keystore failure, dan operasi concurrent yang benar-benar terjadi pada runtime.

Audit tidak mengubah source code produksi. Perubahan lokal hanya berupa instalasi toolchain sandbox dan berkas sementara untuk menjalankan verifikasi; berkas sementara tersebut dihapus setelah pengumpulan bukti.

## 2. Repository/HEAD Audited

Repository diambil langsung dari GitHub dan diperiksa pada branch `main`. `git status` sebelum audit bersih dan HEAD lokal sama dengan `origin/main` pada commit `45e7284`. Dokumentasi aktif, changelog, laporan audit sebelumnya, konfigurasi Gradle, manifest, resources, source Kotlin, unit tests, ProGuard, workflow CI, dan artifact AAR lokal diperiksa.

Area arsitektur yang teridentifikasi meliputi `MainActivity`, `AppExclusionActivity`, `VelumController`, `VelumTunnel`, `ReconnectMonitor`, `BootReceiver`, `VelumTileService`, `VelumApi`, `EndpointProbe`, `Prefs`, Android Keystore/`EncryptedSharedPreferences`, konfigurasi network security, dan fork WireGuard AAR lokal.

Laporan audit lama dipakai sebagai input regresi, bukan sebagai bukti otomatis bahwa masalah telah selesai. Klaim foreground service, handshake freshness, notification contract, checksum artifact, dan storage tanpa plaintext diverifikasi ulang terhadap source dan artifact HEAD saat ini.

## 3. Environment

Verifikasi akhir menggunakan OpenJDK 21.0.12, Gradle Wrapper 9.6.0, Android SDK platform 37.2 sebagai platform lengkap, alias lingkungan 37.0 agar AGP dapat menyelesaikan `compileSdk = 37`, build-tools 36.0.0, dan platform-tools 37.0.1. Alias SDK tersebut hanya dibuat di luar repository dan tidak mengubah konfigurasi project.

Sandbox tidak menyediakan `adb` dan Android Emulator. Karena itu tidak ada pengujian install, launch, VPN consent, notification tap, reboot, screen-off, Doze, OEM policy, atau traffic tunnel nyata.

## 4. Tests Executed

| Aktivitas | Status | Bukti |
|---|---|---|
| `testDebugUnitTest` | PASS | 152 test, 0 failure, 0 error, 0 skipped |
| `assembleDebug` | PASS | Dua APK ABI berhasil dibuat |
| `assemblePreview` | PASS | Varian release-like dengan R8 berhasil dibuat |
| `lintDebug` | PASS secara task, 0 error / 46 warning | Laporan lint lokal berhasil dibuat |
| AAR SHA-256 verification | PASS | `app/libs/tunnel-1.0.20260102-velum1.aar: OK` |
| Notification contract script | PASS | `notification contract: PASS` |
| Documentation consistency gate | PASS | 75 berkas, 0 pelanggaran; 124 TODO, 0 pelanggaran; ADR terindeks |
| Signed release APK | NOT VERIFIED | Signing secret tidak tersedia di sandbox; preview memakai debug key |
| APK install/runtime | BLOCKED | Tidak ada `adb` atau emulator |
| GitHub Actions current HEAD | NOT VERIFIED | Workflow dapat diaudit statically, tetapi run remote baru tidak dijalankan dari sandbox |

Parallel build pertama sempat membuat unit-test resource race karena beberapa Gradle job menulis direktori incremental yang sama. Test kemudian dijalankan ulang secara serial dan lulus. Hasil serial adalah bukti yang dipakai.

## 5. Test Results

Debug APK yang dihasilkan:

- `app-arm64-v8a-debug.apk`, sekitar 8.57 MB.
- `app-armeabi-v7a-debug.apk`, sekitar 8.34 MB.

Preview APK dengan R8 yang dihasilkan:

- `app-arm64-v8a-preview.apk`, sekitar 4.73 MB.
- `app-armeabi-v7a-preview.apk`, sekitar 4.50 MB.

Lint menghasilkan **0 error dan 46 warning**. Warning tersebut tidak dapat diperlakukan sebagai “tidak penting” hanya karena task lulus, tetapi tidak ada warning yang selama audit ini dibuktikan sebagai runtime failure.

AAR lokal lulus checksum. Bytecode notification contract memuat `PendingIntent.getActivity`, `setContentIntent`, explicit `com.rollinkxx.velum.MainActivity`, overload `startForeground` untuk API lama dan typed overload untuk API 34+, serta tidak memuat `FLAG_MUTABLE` atau `setContentIntent(null)`.

## 6. Confirmed Bugs

### BUG-CI-001 — Lint failure dapat disamarkan sebagai CI success

**CATEGORY:** CONFIRMED BUG / CI QUALITY DEFECT  
**SEVERITY:** P2 / HIGH  
**PRIORITY:** Tinggi  
**STATUS:** Terbuka pada HEAD yang diaudit  
**REPRODUCIBILITY:** Always reproducible secara konfigurasi workflow

**Expected behavior:** Jika lint menghasilkan error, required CI check harus gagal sehingga perubahan tidak dapat dianggap terverifikasi.

**Actual behavior:** `app/build.gradle.kts` menetapkan `abortOnError = true`, tetapi `.github/workflows/build.yml` menjalankan lint dengan `continue-on-error: true`. Akibatnya, `lintDebug` dapat exit non-zero tanpa menggagalkan job `verify`. Step berikutnya hanya menampilkan laporan. Status branch dapat hijau meskipun lint menemukan error.

**Precondition:** GitHub Actions runner menjalankan `lintDebug` yang menghasilkan error lint.

**Steps to reproduce:**

1. Tambahkan atau picu satu violation lint yang menghasilkan error.
2. Jalankan workflow `build` pada branch.
3. Amati step `Lint debug (advisori)` pada baris 115–119 `.github/workflows/build.yml`.
4. Amati bahwa `continue-on-error: true` mempertahankan workflow agar tidak gagal.

**Affected component:** GitHub Actions quality gate.  
**Affected files:** `.github/workflows/build.yml:113–119`; `app/build.gradle.kts:87–92`.  
**Root cause:** Kebijakan CI sengaja mengubah lint menjadi advisori, walaupun konfigurasi Gradle menyatakan error harus abort. Ini menciptakan perbedaan antara hasil lint dan status required check.

**Impact:** Regresi lint, termasuk sebagian error correctness atau security lint, dapat masuk ke branch dengan status CI hijau. Unit test dan build tetap dapat lulus sehingga masalah tidak terlihat oleh gate utama.

**Regression risk:** Tinggi untuk perubahan manifest, exported component, permission, resource, dan API Android. Risiko aktual bergantung pada rule lint yang dipicu.

**Evidence:** Source workflow dan Gradle di atas; lint lokal saat ini menghasilkan 0 error, sehingga bug ini adalah defect konfigurasi CI yang terkonfirmasi, bukan bukti bahwa HEAD sekarang gagal lint.

**Recommended fix:** Hapus `continue-on-error: true` dari lint step. Jika warning memang ingin advisori, pertahankan `warningsAsErrors = false` tetapi biarkan exit non-zero untuk error lint. Bila warning ingin dicatat tanpa memblokir, pisahkan warning policy dari error policy, bukan menelan seluruh exit status.

## 7. Potential Bugs

### POT-STATE-001 — Operasi worker milik Activity lama tetap dapat berjalan setelah recreation

**CATEGORY:** POTENTIAL BUG / LIFECYCLE DESIGN RISK  
**SEVERITY:** P3 / MEDIUM  
**STATUS:** NOT VERIFIED di device/runtime  
**REPRODUCIBILITY:** Cannot test dalam sandbox

`VelumController.destroy()` menandai controller lama `dead`, melepas listener, dan memanggil `shutdown()` pada worker. Namun ia tidak menaikkan global intent generation. Operasi yang sudah berada di dalam `VelumTunnel.up`, `VelumConnectionContract.connect`, atau network probe dapat menyelesaikan pekerjaannya setelah Activity dibuat ulang. Sebagian UI callback memang diblokir oleh `dead`, tetapi operasi masih berpotensi memutasi state global `VelumTunnel` atau `Prefs` bila tidak dianggap stale oleh generation.

Ini belum diklasifikasikan sebagai confirmed bug karena source memiliki stale guards dan operasi tunnel diserialisasi, serta tidak tersedia stress test lifecycle/device. Tambahkan instrumentation test yang melakukan rotate/background/foreground atau process recreation ketika connect, endpoint probe, dan handshake berjalan; verifikasi bahwa operasi lama tidak mengubah `wasUp`, endpoint, monitor, atau status Activity baru.

### POT-NET-001 — Batas handshake kandidat fallback dapat terlalu agresif pada jaringan lambat

**CATEGORY:** POTENTIAL BUG / NETWORK RELIABILITY RISK  
**SEVERITY:** P3 / MEDIUM  
**STATUS:** NOT VERIFIED  
**REPRODUCIBILITY:** Cannot test

Fallback endpoint memakai batas sekitar 3 detik per kandidat, sedangkan koneksi awal memakai 8 detik dan retry memakai 10 detik. Pada jaringan dengan latency tinggi, radio baru aktif, atau transisi Wi-Fi/mobile, kandidat yang sebenarnya valid dapat dianggap gagal sebelum handshake terlihat. Kode membatasi total waktu dengan sengaja, sehingga ini adalah trade-off desain, bukan confirmed defect. Uji dengan network shaping dan device nyata diperlukan sebelum mengubah batas.

## 8. Regression Findings

Tidak ditemukan regression runtime yang dapat dibuktikan dari static review dan test suite saat ini. Perubahan yang paling sensitif—foreground VPN service, notification `PendingIntent`, stale handshake baseline, endpoint fallback tervalidasi, dan persistence transaction—memiliki source evidence serta unit/contract coverage yang relevan.

Regression coverage tetap tidak lengkap untuk kombinasi berikut: Activity recreation saat operasi berjalan, process death ketika VPN service aktif, permission notification ditolak, Always-on/Lockdown VPN, device reboot, Android 8–16 lintas OEM, dan network transition saat handshake sedang menunggu.

## 9. Security Findings

Tidak ada security issue baru yang terkonfirmasi dalam scope repository.

Temuan positif yang diverifikasi meliputi penyimpanan credential melalui `EncryptedSharedPreferences`/Keystore tanpa fallback plaintext aktif, backup exclusion untuk data VPN, API HTTPS dengan redirect dimatikan, bounded response reads, header Authorization yang tidak diteruskan melalui redirect, exported surface yang terbatas, checksum AAR, dan notification `PendingIntent` immutable.

Certificate pinning masih memiliki risiko operasional yang terdokumentasi: pin-set memiliki expiration 2027-03-31 dan rotasi manual wajib dilakukan sebelum tanggal tersebut. Ini **residual risk**, bukan confirmed vulnerability pada HEAD.

Security verification runtime tetap incomplete. Pin chain, Keystore failure behavior, notification permission, dan policy `systemExempted` belum diuji pada device nyata.

## 10. Performance Findings

Tidak ada performance issue terkonfirmasi. Endpoint probe menggunakan bounded executor, timeout, daemon threads, dan shutdown path. DoH dibatasi ukuran respons dan freshness window. APK memakai ABI splits.

Namun resource behavior belum diverifikasi dengan profiler/device. Belum ada bukti runtime untuk CPU/wakeups, memory retention, battery impact, atau reconnect frequency pada jaringan yang berubah-ubah. Status area ini adalah **NOT VERIFIED**, bukan PASS penuh.

## 11. Lifecycle/Concurrency Findings

Static review menemukan pola mitigasi yang baik: global intent generation, stale checks, single-thread workers, cancellation untuk pending test, cleanup yang tidak mengambil alih intent baru, serta handshake baseline yang harus lebih baru daripada sesi sebelumnya.

Coverage concurrency tetap hanya unit/static. Tidak ada stress test aktual untuk connect/disconnect berulang, tile versus Activity, boot receiver versus user action, rotation saat endpoint probe, atau timeout callback versus success callback. Area tersebut berstatus **TEST GAP**.

## 12. Network/Tunnel Findings

API request memiliki timeout, status handling, response-size bounds, redirect rejection, dan content parsing. Parser DoH hanya menerima IPv4 valid, melakukan deduplication, dan membatasi jumlah kandidat. Endpoint manual memvalidasi port 1–65535 serta IPv4/IPv6/domain input.

Endpoint RTT tidak diperlakukan sebagai bukti koneksi. Kandidat baru baru dipromosikan setelah handshake WireGuard yang fresh. Ini mengurangi risiko UI menyatakan connected ketika UDP/handshake sebenarnya gagal.

Yang belum diverifikasi adalah perilaku actual WireGuard traffic ketika jaringan berpindah, captive portal, DNS unavailable, IPv6-only network, UDP blocked, endpoint slow, dan all-candidates failure pada device.

## 13. Test Coverage Gaps

Cakupan unit test kuat untuk logika murni, tetapi tidak menggantikan Android integration/runtime testing. Blind spot utama adalah:

- fresh install, upgrade, downgrade, clear data, dan process death;
- Android 8, 13, 14, 15, dan 16 pada OEM berbeda;
- screen-off, Doze, battery optimization, Always-on VPN, dan Lockdown VPN;
- VPN approval revoked, notification permission denied, dan `systemExempted` rejection;
- Wi-Fi/mobile handoff, airplane mode, captive portal, DNS/DoH outage, TLS failure, dan UDP/2408 blocking;
- rapid connect/disconnect serta Activity recreation saat operation in-flight;
- signed release artifact dengan real release keystore dan install/upgrade signature continuity.

## 14. Blocked / Not Verified

**BLOCKED:** Device runtime. Sandbox tidak memiliki `adb` atau emulator.

**NOT VERIFIED:** Signed production release. Workflow release hanya berjalan jika repository variable `ENABLE_RELEASE_SIGNING=true` dan signing secrets tersedia; secret tersebut tidak tersedia dalam sandbox.

**NOT VERIFIED:** Current remote GitHub Actions run. Workflow static review dilakukan, tetapi audit ini tidak memalsukan status CI remote baru.

**NOT VERIFIED:** Real TLS pin validation, notification tap, OEM foreground-service behavior, and tunnel traffic.

## 15. False Positives Ruled Out

Beberapa klaim lama tidak diterima secara otomatis dan dicek ulang.

Pertama, tidak ada fallback plaintext aktif pada current `Prefs` path; kegagalan Keystore dilempar sebagai `KeystoreUnavailableException`. Kedua, status `Tunnel.State.UP` tidak dipakai sendirian sebagai bukti koneksi pada connection contract; handshake fresh diperlukan. Ketiga, AAR lokal tidak hanya dipercaya dari dokumentasi: checksum dan bytecode notification contract diverifikasi. Keempat, kegagalan unit test paralel tidak diperlakukan sebagai product failure karena rerun serial lulus dan failure awal menunjukkan resource directory race akibat concurrent Gradle writers.

## 16. Residual Risks

Risiko terbesar sebelum rilis adalah kurangnya device evidence, bukan kegagalan unit test. Risiko berikutnya adalah rotasi pin sebelum expiration, foreground-service/OEM behavior, and network transition recovery. CI lint false-green harus diperbaiki karena mengurangi confidence terhadap perubahan berikutnya.

## 17. Recommended Fix Order

1. **Perbaiki BUG-CI-001:** jadikan lint error blocking dan pertahankan warning sebagai advisori.
2. Jalankan instrumentation/device matrix untuk lifecycle, notification tap, foreground service, process death, reboot, Doze, Always-on, dan Lockdown VPN.
3. Jalankan network-failure matrix dengan Wi-Fi/mobile handoff, captive portal, DNS failure, TCP/UDP blocking, timeout, and endpoint rotation.
4. Uji lifecycle race saat Activity recreation dan rapid user actions. Jika operasi controller lama masih dapat menulis state global, tambahkan ownership generation pada destruction.
5. Jalankan signed release build dengan real keystore, verifikasi signature, install/upgrade path, dan simpan mapping artifact.
6. Jadwalkan rotasi pin sebelum 2027-03-31 serta ulangi TLS validation di device.

## 18. Final Verification Matrix

| Area | Status | Evidence | Confidence |
|---|---|---|---|
| Repository/HEAD identity | PASS | Branch `main`, HEAD `45e7284`, source/docs reviewed | HIGH |
| Static source review | PASS with residual risks | Kotlin, manifest, Gradle, ProGuard, workflows inspected | MEDIUM |
| JVM unit tests | PASS | 152 tests, 0 failures/errors/skips | HIGH |
| Debug build | PASS | `assembleDebug`, two ABI APKs | HIGH |
| R8 preview build | PASS | `assemblePreview`, two ABI APKs | HIGH |
| Lint | PASS with warnings | 0 errors, 46 warnings | HIGH |
| AAR integrity | PASS | SHA-256 check succeeded | HIGH |
| Notification bytecode contract | PASS | Contract script succeeded | HIGH |
| Documentation gate | PASS | Four consistency checks clean | HIGH |
| CI lint gate | **FAIL / confirmed defect** | `continue-on-error: true` can hide lint errors | HIGH |
| API/network static controls | PASS with runtime gap | HTTPS, bounds, timeout, redirect rejection, parser review | MEDIUM |
| Credential storage | PASS statically | Keystore/EncryptedSharedPreferences path, no plaintext fallback | MEDIUM |
| Lifecycle/concurrency | NOT VERIFIED | Guards reviewed; no device stress test | LOW |
| Runtime/device behavior | BLOCKED | No `adb` or emulator | LOW |
| Signed release | NOT VERIFIED | Release secrets unavailable | LOW |
| Security verification | INCOMPLETE | Static controls pass; device/TLS/OEM checks pending | MEDIUM |

## References

[1]: https://github.com/velum-tunnel/velum/tree/45e72840eec5019b5fe0e0a20ed2946a8628929e "Velum repository at audited HEAD"

[2]: https://developer.android.com/reference/android/net/VpnService "Android VpnService API reference"

[3]: https://developer.android.com/develop/connectivity/vpn "Android VPN developer guide"

[4]: https://developer.android.com/about/versions/14/changes/fgs-types-required "Android 14 foreground service type requirements"

[5]: https://github.com/WireGuard/wireguard-android "WireGuard Android upstream repository"

[6]: https://docs.github.com/en/actions/writing-workflows/workflow-syntax-for-github-actions "GitHub Actions workflow syntax"
