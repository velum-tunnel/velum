# Velum WireGuard tunnel fork

Velum mem-vendor `com.wireguard.android:tunnel:1.0.20260102` sebagai AAR lokal karena kelas `GoBackend.VpnService` pada upstream yang dipakai memanggil `Context.startService()` tetapi tidak memanggil `startForeground()`. Dokumentasi resmi Android menyatakan VPN service pada Android 8+ harus dipromosikan ke foreground agar tidak dihentikan sistem ketika aplikasi berada di background.

Fork ini **tidak mengubah implementasi WireGuard native**. AAR hanya mengganti kelas Java `com.wireguard.android.backend.GoBackend` dan nested `GoBackend$VpnService` untuk:

1. membuat notification channel berprioritas rendah;
2. memanggil `startForeground()` pada `onCreate()` service;
3. menggunakan `FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED` pada Android 10+.

`systemExempted` dipilih karena dokumentasi Android menyebut aplikasi VPN yang dikonfigurasi melalui Settings sebagai use case yang memenuhi syarat. Manifest aplikasi juga mendeklarasikan `FOREGROUND_SERVICE` dan `FOREGROUND_SERVICE_SYSTEM_EXEMPTED`.

## Provenance

- Upstream: `https://github.com/WireGuard/wireguard-android`
- Upstream tag: `1.0.20260102`
- Patch source: [`foreground-service.patch`](foreground-service.patch)
- Artifact: `app/libs/tunnel-1.0.20260102-velum1.aar`
- Checksum: [`SHA256SUMS`](SHA256SUMS)

Setiap pembaruan dependency wajib mengulang inspeksi bytecode/source `GoBackend`, menerapkan patch secara eksplisit, membangun ulang AAR, memperbarui checksum, lalu menjalankan `testDebugUnitTest`, `lintDebug`, dan `assemblePreview`.
