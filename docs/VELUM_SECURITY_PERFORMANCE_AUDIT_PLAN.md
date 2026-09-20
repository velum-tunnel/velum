# Rencana Audit Keamanan dan Performa Velum

## Ringkasan keputusan

Audit Velum akan dilakukan secara bertahap dengan memisahkan **keamanan kode**, **keamanan konfigurasi**, **performa aplikasi**, dan **validasi runtime VPN**. Setiap temuan harus memiliki bukti yang dapat diulang, tingkat keparahan, dampak, rekomendasi perbaikan, dan regression test atau prosedur verifikasi yang sesuai.

Validasi runtime VPN tidak boleh dinyatakan berhasil hanya karena emulator dapat boot atau APK dapat dipasang. Bukti minimum untuk menyatakan alur VPN tervalidasi adalah **service aktif, interface VPN terbentuk, handshake baru terjadi, dan traffic aktual melewati tunnel**. Jika perangkat nyata atau emulator yang sehat tidak tersedia, hasil harus dicatat sebagai **BLOCKED** atau **UNVERIFIED**.

Audit dilakukan pada branch `audit/phase10-first-real-device`. Branch `main` tidak boleh diubah langsung.

## Sasaran audit

Audit memiliki lima sasaran utama. Pertama, memastikan data pengguna, konfigurasi tunnel, dan material kriptografi tidak bocor atau hilang ketika terjadi error, upgrade, reinstall, atau Activity recreation. Kedua, memastikan lifecycle Activity, listener, permission, notification, dan `VpnService` tidak menimbulkan race condition atau state yang salah. Ketiga, memastikan konfigurasi Android, WireGuard GoBackend, dan network path tidak melemahkan keamanan. Keempat, menemukan penggunaan CPU, memori, disk, baterai, dan waktu startup yang tidak proporsional. Kelima, menghasilkan evidence runtime yang membedakan keberhasilan infrastruktur dari keberhasilan VPN.

## Fase pelaksanaan

### Fase 1 — Baseline dan inventarisasi

Catat commit, branch, versi Gradle, Android Gradle Plugin, Kotlin, JDK, compile SDK, target SDK, min SDK, dan konfigurasi build `debug`, `preview`, serta `release`. Inventarisasi komponen yang menyentuh storage, permission, network, notification, Activity lifecycle, foreground service, WireGuard backend, dan logging.

Gunakan **`code-reviewer`** untuk memahami struktur dan risiko arsitektur. Gunakan **`technical-writing`** untuk menjaga catatan audit tetap dapat ditelusuri. Gunakan **`push-to-github`** sebagai quality gate sebelum perubahan audit didorong ke remote.

Output fase ini adalah baseline toolchain, peta komponen, daftar asumsi, dan daftar area berisiko tinggi.

### Fase 2 — Audit keamanan kode dan storage

Periksa semua jalur penyimpanan konfigurasi tunnel, status koneksi, token, private key, dan metadata sensitif. Audit harus mencakup enkripsi saat tersimpan, pemulihan setelah key invalidation, perilaku ketika storage rusak, dan risiko kehilangan data akibat exception yang belum diklasifikasikan.

Periksa pula apakah log, exception, artifact CI, backup, screenshot, dan dump state dapat memuat private key, endpoint credential, token, atau data pengguna. Pastikan redaction dilakukan sebelum evidence dibagikan.

Periksa input dan batas kepercayaan pada intent, deep link, broadcast, tile service, boot receiver, file path, konfigurasi endpoint, serta output command-line. Gunakan prinsip OWASP sebagai baseline review, tetapi prioritaskan risiko yang spesifik terhadap aplikasi VPN.

Gunakan **`code-reviewer`** untuk temuan keamanan dan maintainability. Gunakan **`diagnosing-bugs`** jika temuan bergantung pada urutan lifecycle atau state yang sulit direproduksi. Setiap perbaikan harus memiliki regression test.

### Fase 3 — Audit permission, lifecycle, dan service

Verifikasi permission VPN, notification, foreground service, boot receiver, package replacement, Quick Settings tile, Activity recreation, background/foreground transition, force-stop, process death, dan device reboot.

Periksa kepemilikan listener agar hanya pemilik lifecycle yang aktif menerima callback. Periksa agar permintaan notification permission tidak berulang tanpa kondisi yang sah. Periksa bahwa kegagalan permission dan service menghasilkan state pemulihan yang eksplisit, bukan state connected yang palsu.

Output fase ini adalah matriks lifecycle dan permission yang mencatat kondisi awal, tindakan, state yang diharapkan, evidence, dan hasil aktual.

### Fase 4 — Audit jaringan dan VPN

Periksa konfigurasi `VpnService`, WireGuard GoBackend, routing, DNS, allowed/disallowed application, IPv4, IPv6, DNS leak, kill-switch behavior, reconnect, endpoint failure, dan perubahan network transport.

Core smoke chain yang wajib dijalankan pada perangkat valid adalah:

1. preflight tepat satu perangkat ADB sehat;
2. build dan install APK yang benar;
3. launch Activity;
4. grant atau konfirmasi VPN permission;
5. observasi service VPN;
6. observasi interface dan route sebelum/sesudah connect;
7. kumpulkan handshake baru setelah koneksi;
8. lakukan HTTPS dan DNS request aktual;
9. disconnect dan verifikasi interface/route kembali normal;
10. reconnect dan ulangi handshake serta traffic.

Evidence tidak boleh disintesis. `adb devices` dengan status `offline`, emulator yang belum boot, atau log aplikasi tanpa bukti device-side bukan evidence runtime yang valid.

Gunakan **`ci-cd-and-automation`** untuk workflow emulator dan self-hosted runner. Gunakan **`persistent-computing`** jika dibutuhkan host persisten dengan physical device, nested virtualization, atau device farm. Gunakan **`debugging-and-error-recovery`** untuk membedakan kegagalan aplikasi dari kegagalan infrastruktur.

### Fase 5 — Audit performa

Ukur cold start dan warm start Activity, waktu sampai UI siap, waktu sampai VPN service aktif, waktu sampai handshake pertama, waktu disconnect, dan waktu reconnect. Ukur penggunaan CPU dan memori ketika idle, ketika tunnel aktif, saat traffic berlangsung, dan selama reconnect berulang.

Periksa kebocoran coroutine, thread, listener, wakelock, notification update, broadcast receiver, dan proses native. Periksa apakah polling atau retry berjalan tanpa backoff, terus aktif setelah disconnect, atau tetap hidup setelah Activity dihancurkan.

Ukur ukuran APK dan native library untuk varian `debug`, `preview`, dan `release`. Periksa dampak R8, resource shrinking, ABI split, startup initialization, serta penggunaan disk untuk log atau cache.

Performa harus dibandingkan pada kondisi yang sama. Catat model perangkat, API level, ABI, build variant, network condition, jumlah iterasi, median, p95, dan outlier. Satu pengukuran tidak cukup untuk menyatakan regresi.

Gunakan **`data-analysis`** bila hasil pengukuran sudah cukup banyak untuk dianalisis secara statistik. Gunakan **`diagnosing-bugs`** untuk mengisolasi sumber bottleneck sebelum mengubah kode.

### Fase 6 — Audit pengujian dan quality gate

Pastikan unit test mencakup state transition, storage recovery, permission policy, listener ownership, reconnect, dan error classification. Pastikan test menguji perilaku, bukan hanya implementasi internal.

Pertahankan quality gate berikut:

```text
unit test → assembleDebug → assemblePreview/R8 → lint → static review → emulator smoke
```

Workflow `android-emulator.yml` hanya boleh menyatakan emulator boot, ADB, install APK, dan launch Activity. Workflow tersebut tidak boleh digunakan sebagai pengganti real-device VPN validation.

Workflow `velum-real-device-runtime.yml` tetap manual-only dan hanya boleh dijalankan pada self-hosted runner yang memiliki perangkat Android berwenang. Artifact runtime harus diunggah meskipun job gagal, dengan retention dan redaction yang sesuai.

Gunakan **`ci-cd-and-automation`**, **`automation-and-scheduling`**, dan **`push-to-github`** untuk menjaga pipeline konsisten. Jika terjadi kegagalan, gunakan aturan stop-the-line dari **`debugging-and-error-recovery`**: hentikan perubahan baru, simpan bukti, reproduksi, lokalisasi, perbaiki akar masalah, tambahkan guard, lalu verifikasi end-to-end.

### Fase 7 — Triage dan remediasi

Klasifikasikan temuan sebagai berikut:

| Tingkat | Kriteria | Keputusan |
|---|---|---|
| Critical | Kebocoran private key/token, bypass VPN yang nyata, data loss, atau service tidak aman pada jalur utama | Perbaiki sebelum merge atau release |
| High | VPN state salah, DNS/traffic leak, crash pada lifecycle utama, atau reconnect gagal secara konsisten | Perbaiki sebelum release |
| Medium | Degradasi performa nyata, recovery lemah, observability kurang, atau edge case yang dapat mengganggu penggunaan | Perbaiki sebelum release berikutnya atau dokumentasikan pengecualian |
| Low | Masalah maintainability, logging, dokumentasi, atau optimisasi kecil tanpa dampak keamanan langsung | Masukkan ke backlog terukur |

Setiap temuan harus mencatat lokasi, prasyarat, langkah reproduksi, actual result, expected result, dampak, bukti, pemilik perbaikan, dan regression test. Hindari perubahan tidak terkait dalam commit perbaikan.

Gunakan **`code-reviewer`** untuk laporan dengan bagian Summary, Critical, Major, Minor, Positive Feedback, Questions, dan Verdict. Gunakan **`diagnosing-bugs`** untuk memastikan klasifikasi tidak hanya berdasarkan gejala.

## Kriteria selesai

Audit dapat dinyatakan selesai jika semua kondisi berikut terpenuhi:

- tidak ada temuan Critical atau High yang terbuka tanpa keputusan tertulis;
- unit test, build, lint, dan R8 berhasil;
- perubahan keamanan memiliki regression test;
- emulator smoke test berhasil atau kegagalannya terdokumentasi sebagai blocker infrastruktur;
- real-device VPN smoke chain menghasilkan evidence Service, Interface, Handshake, dan Traffic;
- evidence tidak mengandung credential atau private key;
- hasil performa memiliki baseline dan metode pengukuran yang dapat diulang;
- branch audit bersih, commit dapat ditelusuri, dan `main` tidak disentuh;
- laporan akhir menyebutkan dengan jelas bagian yang PASS, BLOCKED, dan UNVERIFIED.

Jika perangkat nyata atau emulator sehat belum tersedia, status akhir harus tetap:

```text
RUNTIME INFRASTRUCTURE: BLOCKED
VPN RUNTIME QUALITY: UNVERIFIED
```

## Pemetaan skill Manus

| Skill | Peran dalam audit |
|---|---|
| `code-reviewer` | Review keamanan, correctness, arsitektur, performa, dan coverage test. |
| `diagnosing-bugs` | Isolasi akar masalah pada lifecycle, storage, runtime, dan regresi. |
| `debugging-and-error-recovery` | Triage kegagalan dengan preservation of evidence dan stop-the-line. |
| `ci-cd-and-automation` | Workflow build, lint, test, emulator, artifact, dan quality gate. |
| `automation-and-scheduling` | Desain eksekusi otomatis yang aman dan tidak menyamarkan blocker. |
| `persistent-computing` | Evaluasi host persisten, KVM, physical device, device farm, dan runner. |
| `data-analysis` | Analisis benchmark, distribusi latency, CPU, memory, dan regresi. |
| `deep-research` | Riset Android VPN, WireGuard, API behavior, dan praktik keamanan resmi. |
| `technical-writing` | Laporan audit, evidence index, runbook, dan handoff antaragen. |
| `push-to-github` | Pemeriksaan akhir, commit, dan push branch audit. |
| `agent-development` | Pembuatan agen khusus untuk review keamanan, performa, atau runtime. |
| `dispatching-parallel-agents` | Pembagian audit independen tanpa berbagi state yang berisiko. |
| `workflow-composer` | Orkestrasi multiagen untuk audit besar dan penggabungan hasil terstruktur. |
| `prompt-engineer` | Penyusunan instruksi agen yang tegas terhadap evidence palsu dan scope creep. |
| `memory-recall` | Pengambilan keputusan dan konteks audit terdahulu jika memori tersedia. |

## Deliverables

Deliverable utama adalah laporan audit keamanan dan performa dengan daftar temuan yang diprioritaskan. Deliverable pendukung terdiri atas regression test, benchmark result, runtime evidence bundle, CI workflow, remediation commit, dan handoff document untuk agen berikutnya.

## Status implementasi rencana

Static security gate telah diimplementasikan melalui `scripts/audit/static_security_check.sh` dan ditambahkan ke workflow build. Pemeriksaan tersebut memverifikasi backup Android dinonaktifkan, permission VPN tetap dilindungi, cleartext traffic dilarang, logging sensitif melewati `VelumLog`, dan tidak ada material private key PEM di source atau script.

Benchmark startup dan memori telah diimplementasikan melalui `scripts/performance/measure_startup.sh`. Benchmark membutuhkan tepat satu target ADB sehat, menghasilkan data mentah per iterasi, serta merangkum rata-rata, minimum, maksimum, median, dan p95. Benchmark dihubungkan ke workflow real-device setelah launch smoke test.

Build debug, build preview dengan R8, unit test, lint, dan static security gate telah berhasil dijalankan secara lokal menggunakan JDK 17 dan Android SDK lokal. Validasi service VPN, interface, handshake, traffic, disconnect, reconnect, dan benchmark pada perangkat nyata masih **BLOCKED** sampai tersedia target Android sehat. Emulator boot atau APK launch saja tidak mengubah status tersebut menjadi PASS.

## Referensi

[1]: https://developer.android.com/reference/android/net/VpnService "Android VpnService reference"

[2]: https://developer.android.com/privacy-and-security/security-best-practices "Android security best practices"

[3]: https://developer.android.com/topic/performance/vitals "Android performance vitals"

[4]: https://owasp.org/www-project-mobile-top-10/ "OWASP Mobile Top 10"

[5]: https://www.wireguard.com/ "WireGuard official project documentation"
