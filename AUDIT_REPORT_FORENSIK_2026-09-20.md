# Laporan Audit Forensik Velum

**Tanggal audit:** 20 September 2026  
**Ruang lingkup:** aplikasi Android VPN Velum, konfigurasi build, dependency, lifecycle tunnel WireGuard, transport API, penyimpanan kredensial, recovery, dan supply chain.  
**Kesimpulan:** Velum berada pada kondisi **layak sebagai kandidat aplikasi VPN ringan untuk uji perangkat dan release candidate**, tetapi belum dapat dinyatakan siap publikasi tanpa pengujian perangkat nyata pada matriks Android dan OEM yang ditetapkan.

## Ringkasan keputusan

Audit kedua tidak menemukan kerentanan kritis baru pada implementasi runtime. Beberapa area yang paling berisiko telah memiliki pengamanan yang tepat: service VPN memakai foreground lifecycle, surface manifest dibatasi, redirect API ditolak, respons jaringan dibatasi ukuran dan waktunya, kredensial disimpan melalui Keystore, dan kandidat endpoint tidak dipromosikan sebelum handshake WireGuard terbukti.

Refactor runtime besar tidak direkomendasikan. Pada aplikasi VPN, perubahan besar pada lifecycle atau konkurensi tanpa perangkat nyata berisiko memperkenalkan regresi yang lebih serius daripada manfaat optimasi mikro. Perubahan yang diterapkan adalah hardening CI supply chain: checksum AAR WireGuard diubah dari path absolut menjadi path relatif, lalu diverifikasi sebelum Gradle build berjalan. Perubahan ini tidak menambah biaya runtime dan mencegah artifact lokal yang berubah atau korup masuk ke APK.

## Temuan utama

| Area | Status | Bukti audit dan penilaian |
|---|---|---|
| Lifecycle foreground VPN | Lulus dengan catatan device test | Fork AAR memanggil `startForeground()` dari service WireGuard dan manifest mendeklarasikan permission serta `systemExempted`. Validasi OEM tetap harus dilakukan pada Android 14–16. |
| Permission dan exported surface | Lulus | Service VPN dan receiver boot tidak diekspos ke aplikasi lain. `BIND_VPN_SERVICE` tetap digunakan. Activity yang memang menjadi entry point sistem tetap diekspor sesuai kebutuhan platform. |
| Transport API | Lulus | API memakai HTTPS, timeout terikat, redirect dimatikan, header terpusat, `Connection: close`, validasi status HTTP, dan batas ukuran respons. Token tidak dimasukkan ke pesan error. |
| TLS pinning | Lulus bersyarat | Pin CA API Cloudflare didefinisikan dengan masa berlaku sampai 2027-03-31. Pin harus dirotasi sebelum kedaluwarsa dan perlu diuji pada perangkat karena pemeriksaan dari sandbox tidak menerima rantai sertifikat yang dapat divalidasi. |
| Secret storage | Lulus | Private key dan token memakai `EncryptedSharedPreferences` berbasis Android Keystore tanpa fallback plaintext. Kegagalan Keystore berhenti secara fail-safe. |
| Parser dan endpoint | Lulus | Endpoint dinormalisasi, port dibatasi 1–65535, literal IPv4/IPv6 divalidasi, dan kandidat DoH hanya menerima IPv4 yang disanitasi serta dibatasi jumlahnya. |
| Konkurensi dan intent ownership | Lulus berdasarkan static analysis dan unit test | Transisi tunnel diserialisasi, intent memakai generasi atomik, operasi stale dihentikan, dan cleanup tidak mengambil alih intent baru. Pengujian race pada perangkat tetap disarankan. |
| Recovery dan boot | Lulus dengan risiko terukur | `goAsync()` dipakai dan hasil boot ditulis durable sebelum callback selesai. Risiko endpoint domain yang lambat saat boot sudah didokumentasikan dan memerlukan pengukuran perangkat. |
| Resource dan performa | Lulus | Probe memakai bounded executor, timeout, daemon worker, dan `shutdownNow()`. Aplikasi memakai dependency minimal dan APK dipisah berdasarkan ABI arm64-v8a serta armeabi-v7a. |
| R8 dan native artifact | Lulus | Preview memakai konfigurasi release-like dengan minify dan shrink resources. AAR lokal memiliki provenance, patch source, dan checksum. |
| Supply chain CI | **Diperbaiki pada audit ini** | `SHA256SUMS` kini portable dan diverifikasi sebagai langkah wajib sebelum build. |

## Perubahan yang diterapkan

Berkas `third_party/wireguard-tunnel/SHA256SUMS` sebelumnya memuat path absolut lingkungan sandbox. Format tersebut berhasil pada mesin pembuat artifact, tetapi gagal digunakan pada runner lain. Path kini menjadi `app/libs/tunnel-1.0.20260102-velum1.aar` sehingga `sha256sum --check` dapat dijalankan dari root repository.

Workflow `.github/workflows/build.yml` kini memverifikasi checksum tersebut segera setelah checkout dan sebelum setup Gradle serta Android SDK. Dengan urutan ini, artifact yang berubah, rusak, atau tidak sesuai provenance akan menggagalkan pipeline sebelum kompilasi APK.

## Verifikasi yang dilakukan

Pemeriksaan repository menunjukkan working tree awal bersih dan branch berada pada `main` yang mengikuti `origin/main`. Checksum AAR lokal cocok dengan nilai yang tercatat sebelum perubahan. Riwayat GitHub Actions terakhir yang tersedia untuk commit kode sebelumnya menunjukkan job `build`, `CodeQL`, dan dokumentasi berhasil.

Build lokal `testDebugUnitTest lintDebug assemblePreview` tidak dapat dijadikan bukti kegagalan kode karena sandbox ini tidak memiliki Android SDK atau `ANDROID_HOME` yang valid. Gradle berhenti pada resolusi `android.jar`. Ini adalah keterbatasan lingkungan eksekusi, bukan error kompilasi sumber. Verifikasi build Android harus dilakukan oleh GitHub Actions atau mesin yang memiliki platform `android-37.2` dan build tools `36.0.0`.

## Risiko tersisa sebelum rilis

Pertama, uji perangkat nyata wajib mencakup Android 8, Android 13, Android 14/15/16, screen-off, Doze, perpindahan Wi-Fi ke seluler, reboot, pembaruan aplikasi, Always-on VPN, Lockdown VPN, penolakan izin notifikasi, pencabutan persetujuan VPN dari Settings, dan pemutusan service oleh sistem.

Kedua, uji harus mengukur durasi `BootReceiver` dari broadcast sampai `pending.finish()`. Endpoint berupa nama domain dapat memerlukan retry resolusi DNS ketika perangkat baru selesai boot. Jika pengukuran melewati anggaran broadcast pada OEM tertentu, recovery sebaiknya dipindahkan ke mekanisme lifecycle yang lebih sesuai tanpa menjalankan pekerjaan jaringan panjang di receiver.

Ketiga, rotasi pin TLS harus dilakukan sebelum 2027-03-31. Setiap rotasi wajib diverifikasi dengan rantai sertifikat aktual, perangkat uji, dan fallback release yang sudah dipersiapkan.

Keempat, pengujian tidak boleh hanya mengandalkan state `UP`. Verifikasi handshake baru harus tetap menjadi syarat keberhasilan, khususnya saat endpoint berputar atau jaringan berpindah.

## Rekomendasi operasional

Pertahankan dependency tunnel sebagai artifact lokal hanya selama proses pembaruan selalu mengulang inspeksi bytecode, perbandingan native library, patch foreground service, checksum, unit test, lint, dan build preview. Jangan mengganti AAR dengan dependency dinamis tanpa mengulang audit lifecycle.

Jangan menambahkan logging verbose pada release. Jika investigasi produksi diperlukan, gunakan diagnostik yang sudah tidak memuat private key, token, identitas perangkat, atau alamat sensitif, lalu hapus data diagnostik setelah periode retensi yang ditentukan.

Sebelum tag release, jalankan pipeline penuh pada commit yang sama, simpan `mapping.txt`, tinjau hasil lint, pasang APK preview pada minimal satu perangkat arm64 dan satu perangkat armeabi-v7a, lalu lakukan seluruh skenario reconnect dan recovery.

## Referensi

[1]: https://developer.android.com/reference/android/net/VpnService "Android VpnService API reference"

[2]: https://developer.android.com/develop/connectivity/vpn "Android VPN developer guide"

[3]: https://developer.android.com/about/versions/14/changes/fgs-types-required "Android 14 foreground service type requirements"

[4]: https://github.com/WireGuard/wireguard-android "WireGuard Android upstream repository"
