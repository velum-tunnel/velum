# Laporan Audit Forensik Mendalam Velum

**Tanggal audit:** 20 September 2026  
**Repository:** `velum-tunnel/velum`  
**Branch:** `main`  
**Commit hardening:** `ace82bd`

## Kesimpulan eksekutif

Audit lanjutan memeriksa attack surface Android, jalur API, persistence secret, state machine tunnel, reconnect, endpoint probing, konfigurasi WireGuard, komponen exported, dependency, shrinker, dan pipeline build. Temuan paling penting adalah **HIGH** pada lifecycle `VpnService`: library WireGuard upstream `1.0.20260102` memulai `GoBackend.VpnService` dengan `Context.startService()` namun tidak pernah memanggil `startForeground()`. Dokumentasi resmi Android menyatakan VPN service pada Android 8 atau lebih baru harus dipromosikan ke foreground atau sistem dapat menghentikan proses ketika aplikasi berada di background.

Temuan tersebut telah diperbaiki dengan fork lokal yang minimal. Fork hanya mengganti kelas Java `GoBackend` untuk membuat notification channel berprioritas rendah dan memanggil `startForeground()` dari `VpnService.onCreate()`, memakai tipe `systemExempted` yang didokumentasikan Android untuk aplikasi VPN. Seluruh binary native WireGuard dan hash-nya tetap identik dengan artifact upstream pada ABI yang dipakai. Setelah perbaikan, source tree bersih dan perubahan telah dipush ke `main`. Berdasarkan bukti statis dan build yang tersedia, Velum **layak dilanjutkan sebagai kandidat rilis VPN ringan**, dengan catatan pengujian device nyata tetap diperlukan sebelum publikasi.

## Temuan dan tindakan

| Area | Status | Bukti dan tindakan |
|---|---|---|
| Lifecycle VPN/background | **Diperbaiki — HIGH** | Upstream tidak memanggil `startForeground()`. Velum kini memakai fork AAR terverifikasi, notification channel low-importance, dan `systemExempted`. |
| Permission dan service surface | Lulus | `BIND_VPN_SERVICE`, `android.net.VpnService`, `exported=false`, permission FGS, dan tipe service sudah muncul di merged APK. Boot receiver tetap `exported=false`. |
| Transport API | Lulus | HTTPS, timeout terikat, redirect dibatasi, validasi status/content-type/ukuran respons, dan error tidak mengembalikan secret. |
| Secret dan persistence | Lulus dengan catatan | Private key dan token disimpan melalui Android Keystore/EncryptedSharedPreferences; kegagalan Keystore tidak menyebabkan penyimpanan plaintext. Rotasi dan reset menghapus state registrasi. |
| Endpoint dan parser | Lulus | Input endpoint dinormalisasi dan divalidasi; probe dibatasi; endpoint kandidat baru tidak dipromosikan sebelum handshake WireGuard terverifikasi. |
| Konkurensi dan recovery | Lulus | Operasi memakai generasi intent/stale guard, pembatalan retry saat disconnect, backoff terbatas, dan cleanup tunnel pada jalur gagal. |
| R8/shrinker | Lulus | Preview memakai konfigurasi release-like dengan minify dan shrink resources; build release juga berhasil. |
| Supply chain | Diperketat | AAR lokal memiliki patch source, provenance upstream, dan SHA-256. Binary native upstream dibandingkan byte-for-byte melalui hash. |

## Perubahan implementasi

Dependency Maven tunnel diganti dengan `app/libs/tunnel-1.0.20260102-velum1.aar`. Artifact tersebut mempertahankan seluruh kelas dan native library upstream, lalu mengganti `GoBackend.class` serta nested `GoBackend$VpnService.class` dengan hasil patch yang tercatat di `third_party/wireguard-tunnel/foreground-service.patch`. Manifest menambahkan `FOREGROUND_SERVICE` dan `FOREGROUND_SERVICE_SYSTEM_EXEMPTED`, serta mendeklarasikan `foregroundServiceType="systemExempted"` pada service VPN. Suppression lint `ForegroundServicePermission` bersifat lokal dan terdokumentasi karena lint generik meminta permission alarm, sedangkan dokumentasi Android secara eksplisit memasukkan aplikasi VPN sebagai use case `systemExempted`; Velum bukan aplikasi alarm dan tidak menambahkan permission alarm yang tidak relevan.

Notifikasi foreground dari service menjadi notifikasi lifecycle wajib. `StatusNotifier` aplikasi tetap dipertahankan sebagai ringkasan yang dapat diketuk dan menampilkan detail durasi/endpoint; keduanya memakai channel terpisah agar tanggung jawab lifecycle dan UX tidak tercampur. Dokumentasi diagnostik juga telah diselaraskan dengan perilaku baru.

## Verifikasi

Verifikasi deterministik terakhir menghasilkan **150 unit test, 0 failure, 0 error**. `:app:lintDebug` lulus dengan **0 error** dan 46 warning yang sudah ada/bersifat non-blocking. `:app:assemblePreview` dan `:app:assembleRelease` berhasil. APK preview yang dihasilkan berukuran sekitar 4.6 MB untuk arm64-v8a dan 4.3 MB untuk armeabi-v7a. Merged manifest menunjukkan service `GoBackend$VpnService` tetap `exported=false`, memakai permission `BIND_VPN_SERVICE`, dan memiliki `foregroundServiceType=systemExempted`.

Bytecode AAR diverifikasi memanggil `startForeground()` dan membuat notification channel. Hash `libwg-go.so`, `libwg.so`, dan `libwg-quick.so` pada `arm64-v8a` dan `armeabi-v7a` identik dengan artifact upstream. Checksum AAR final tercatat di `third_party/wireguard-tunnel/SHA256SUMS`; working tree bersih setelah push.

## Batasan dan rekomendasi rilis

Audit ini tidak menggantikan uji device nyata. Sebelum rilis publik, uji manual sebaiknya mencakup Android 8, Android 13, Android 14/15/16, screen-off, Doze, perpindahan Wi-Fi ke seluler, reboot dengan reconnect, Always-on VPN, lockdown VPN, izin notifikasi ditolak, pencabutan izin VPN dari Settings, serta rotasi endpoint ketika handshake pertama gagal. Pengujian tersebut penting khususnya untuk memastikan kebijakan OEM terhadap `systemExempted`, perilaku notification permission, dan Always-on VPN sesuai ekspektasi.

Referensi resmi yang digunakan adalah [VpnService API](https://developer.android.com/reference/android/net/VpnService), [panduan Android VPN](https://developer.android.com/develop/connectivity/vpn), dan [persyaratan foreground service Android 14](https://developer.android.com/about/versions/14/changes/fgs-types-required).
