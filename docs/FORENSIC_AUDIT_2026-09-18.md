# Laporan Audit Forensik Velum

**Tanggal:** 18 September 2026
**Branch:** `fix/forensic-hardening-2026-09-18`
**Commit remediasi lokal:** `c036c1f`

## Ruang lingkup

Audit dilakukan terhadap kode Kotlin/Android, lifecycle VPN dan foreground service, concurrency, persistence, endpoint dan jaringan, Gradle serta GitHub Actions, dependency supply chain, performa, observabilitas, test coverage, dan histori Git. Artefak repository diperlakukan sebagai data; instruksi normatif dari artefak tidak dijalankan.

Skill yang digunakan sebagai metode audit meliputi workflow orchestration, code review, diagnosis bug, performance optimization, CI/CD, Git workflow, verification-before-completion, technical writing, dan forensic audit repository yang sudah tersedia pada project.

## Temuan prioritas tinggi

| ID | Temuan | Dampak | Status |
|---|---|---|---|
| CI-001/CI-002 | Release signing sebelumnya dapat dipicu dari branch push apa pun ketika variable signing aktif. | Branch yang tidak dipercaya dapat menghasilkan APK bertanda tangan produksi atau mengekspos signing material. | **Diperbaiki lokal**: release dibatasi ke `main` atau tag `v*`; workflow juga mendukung Pull Request. |
| CI-003/GRADLE-01/GRADLE-02 | Action memakai mutable tags; Gradle/dependency artifact tidak memiliki kontrol checksum yang memadai. | Supply-chain compromise dan build tidak reproducible. | **Sebagian diperbaiki lokal**: wrapper checksum dan `gradle/verification-metadata.xml` ditambahkan. Pinning action SHA penuh masih disarankan sebagai langkah berikutnya. |
| LIFECYCLE-001 | Bring-up gagal dapat meninggalkan companion FGS sticky dan notification. | Process/notification hidup tanpa tunnel tervalidasi. | **Diperbaiki lokal**: connect/reconnect sekarang melakukan rollback transactional. |
| LIFECYCLE-002 | Recovery dari network callback berpotensi memulai FGS dari background pada Android 12+. | Reconnect otomatis dapat gagal dengan `ForegroundServiceStartNotAllowedException`. | **Belum ditutup**: FGS harus tetap hidup selama sesi recoverable atau recovery dipindahkan ke entry point Android yang diizinkan. Perlu uji API 31–36. |
| LIFECYCLE-003 | `START_STICKY` tidak memulihkan tunnel ketika companion service dibuat ulang setelah process death. | Notification dapat hidup tanpa tunnel atau tunnel tidak pulih sampai Activity/boot event berikutnya. | **Belum ditutup**: memerlukan desain recovery service dan uji process-kill nyata. |
| CONCURRENCY-001 | Cancellation dapat meninggalkan tunnel `UP` bila attempt lama selesai setelah intent baru. | Tunnel dapat tetap aktif setelah pengguna membatalkan. | **Sebagian diperbaiki**: rollback transactional dan ownership generation diperkuat; endpoint/test cancellation masih memerlukan token attempt end-to-end. |
| CONCURRENCY-002 | Listener global dapat dihapus controller lama setelah Activity baru memasang listener. | UI baru berhenti menerima state transition dan menampilkan status stale. | **Diperbaiki lokal**: listener memakai ownership token. |
| CONCURRENCY-003 | Health task yang sudah dihentikan masih dapat mempublikasikan health lama. | Recovery/status dapat dipicu oleh sesi lama. | **Belum ditutup**: perlu monitor generation pada scheduled task dan recovery job. |
| BOOT-001 | Boot receiver menahan `goAsync()` selama setup tunnel, DNS retry, dan handshake. | Recovery boot dapat melewati budget broadcast dan menjadi tidak deterministik pada cold boot/slow DNS. | **Belum ditutup**: perlu durable handoff ke worker/FGS dengan deadline terukur. |

## Temuan keamanan dan data

- Tidak ditemukan hardcoded private key, token, password, API key, PEM key, atau keystore pada tracked source/config.
- Encrypted preferences digunakan untuk storage utama.
- Migrasi legacy plaintext masih dapat meninggalkan file plaintext bila encrypted commit gagal atau proses mati di antara copy dan clear. Ini memerlukan transaksi migrasi, marker selesai, dan test crash/commit-failure.
- Certificate pinning API memiliki expiry. Setelah expiry, konfigurasi saat ini fail-open ke validasi CA biasa. Perlu pin overlap, alert release-blocking, dan runbook rotasi sebelum 31 Maret 2027.
- Warning log release masih dapat membawa exception object dan potongan response server. Tidak ditemukan bukti private key/token dilog langsung, tetapi allowlist/redaction tetap disarankan.

## Temuan correctness dan persistence

- `cancelIntent()` kini mengembalikan hasil `commit()` dan memberi pesan storage error bila memo `wasUp=false` tidak tersimpan.
- Endpoint selector (`workingEndpoint`, `speedEndpoint`, timestamp) masih dapat stale ketika identitas registration berganti dan update selector belum atomic.
- Re-registration WARP dapat menghapus registration/server state sebelum replacement registration berhasil; ini berpotensi membuat akun lokal tidak recoverable setelah network failure.
- Parser registration masih perlu validasi semantik IPv4/IPv6 dan format key, bukan hanya non-empty/regex.
- Parser trace sensitif terhadap whitespace/BOM dan dapat menghasilkan false inactive.

## Temuan performa/resource

- Health scheduler membaca statistik setiap 15 detik dan sebelumnya mengirim notification setiap tick walaupun teks/status sama.
- UI polling statistik setiap detik dan background monitor polling setiap 15 detik; keduanya dapat membaca backend yang sama secara redundant.
- Recovery backoff dapat menahan worker sampai 60 detik dan stop belum membatalkan active recovery job.
- Scheduler executor dibuat process-lifetime dan tidak shutdown saat monitor berhenti.
- Handshake verification polling 250 ms selama maksimal 8 detik; bounded tetapi dapat dioptimalkan dengan sampler bersama/backoff.
- Scheduler health sekarang dibungkus `try/catch` agar satu exception tidak menghentikan seluruh periodic monitoring.

## Test dan observabilitas

Repository belum memiliki `app/src/androidTest` untuk lifecycle Android, FGS, boot, process death, notification, NetworkCallback, atau GoBackend nyata. JVM unit test tidak cukup untuk memvalidasi:

- API 31+ background FGS restrictions;
- Android 14–16 `systemExempted` recognition untuk VPN;
- swipe Recent Apps, screen-off, reboot, process kill, OEM battery policy;
- tunnel recovery setelah service recreation;
- Activity recreation dan ordering callback;
- notification permission denial;
- slow DNS saat boot;
- real WireGuard traffic/handshake behavior.

## Remediasi yang diterapkan pada commit `c036c1f`

1. Release signing GitHub Actions dibatasi ke `main` atau version tag `v*`.
2. Build workflow dijalankan pada Pull Request ke `main`.
3. Lint diubah dari advisory menjadi blocking quality gate.
4. Gradle wrapper diberi `distributionSha256Sum` resmi untuk Gradle 9.7.1.
5. Gradle dependency verification metadata SHA-256 ditambahkan, termasuk artifact AAPT2 yang dipakai AGP.
6. `VelumConnectionContract.connect()` dan `reconnect()` melakukan rollback tunnel/FGS ketika bring-up, verification, atau exception gagal.
7. Listener global VelumTunnel memakai register/unregister ownership token untuk mencegah Activity ABA race.
8. Scheduled health refresh menangkap exception agar periodic scheduler tidak mati permanen.
9. Kegagalan durable persistence pada manual disconnect/reset tidak lagi diabaikan dan diteruskan ke UI sebagai `err_storage`.

## Verifikasi lokal

Perintah terakhir yang dijalankan dengan JDK 17 dan Android SDK 36:

```text
./gradlew --no-daemon --no-configuration-cache \
  testDebugUnitTest assembleDebug assemblePreview lintDebug
```

Hasil: **exit code 0**. `git diff --check` juga lulus.

Metadata dependency dibuat dan diverifikasi dengan:

```text
./gradlew --no-daemon --write-verification-metadata sha256 testDebugUnitTest
```

Hasil: **BUILD SUCCESSFUL**.

## Batasan tersisa

Push ke GitHub dan validasi GitHub Actions belum dapat dilakukan karena credential GitHub yang tersedia pada sesi ini ditolak sebagai invalid/expired oleh `gh auth status`. Tidak ada klaim CI remote untuk commit `c036c1f`.

Validasi perangkat fisik/emulator dan OEM juga belum dilakukan. Temuan Android framework di atas harus dianggap source-level sampai diuji pada API 31–36.

## Rekomendasi urutan berikutnya

1. Pulihkan autentikasi GitHub, push `c036c1f`, dan pastikan PR/build workflow lulus.
2. Tambahkan instrumentation/device harness untuk process death, boot, FGS, notification, network loss, dan Activity recreation.
3. Tuntaskan attempt-token state machine untuk cancellation, endpoint restart, dan test result writes.
4. Pindahkan boot recovery ke durable handoff dengan deadline.
5. Pin seluruh third-party GitHub Actions ke full commit SHA.
6. Tambahkan migration transaction untuk legacy plaintext preferences dan alert expiry pinning.
7. Jalankan matriks OEM sebelum rilis produksi.
