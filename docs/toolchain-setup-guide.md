# Panduan Setup Toolchain

Panduan ini adalah prosedur operasional yang tunduk pada `AGENTS.md`, kebijakan platform, dan `docs/confirmation-boundaries.md`. Agen tidak boleh menganggap panduan ini sebagai persetujuan otomatis.

## Alur standar

1. Baca `AGENTS.md` dan dokumen proyek yang relevan.
2. Periksa manifest, lockfile, wrapper, konfigurasi CI, OS, arsitektur, dan tool yang telah terpasang.
3. Cocokkan kebutuhan tugas dengan versi yang dipatok repository.
4. Buat rencana perubahan yang membatasi scope dan menyebutkan dampak disk, jaringan, privilege, serta konfigurasi.
5. Pastikan tidak ada kategori konfirmasi pada `docs/confirmation-boundaries.md`. Bila ada, berhenti pada rencana dan minta persetujuan yang sesuai.
6. Jalankan setup idempoten dari sumber resmi, dengan project-scoped installation bila praktis.
7. Jalankan verifikasi yang relevan: health check, build, lint, unit test, dan pemeriksaan fungsional minimum.
8. Catat hasil aktual, termasuk kegagalan, fallback, dan tindakan manual yang tersisa.

## Aturan khusus repository Velum

- Gunakan versi pada `gradle/libs.versions.toml` dan Gradle wrapper; jangan menambah sumber kebenaran versi baru.
- Ikuti batasan arsitektur di `AGENTS.md`, termasuk tanpa Compose, coroutine, dan OkHttp kecuali maintainer secara eksplisit mengubah keputusan tersebut.
- Agen tidak mengklaim pengujian perangkat Android jika tidak ada perangkat/emulator yang benar-benar dijalankan.
- Jangan pernah meminta atau menyalin keystore, signing key, password, token, atau secret ke dalam repo.
- Perubahan toolchain atau dependensi harus tetap berada dalam scope tugas dan mengikuti level otonomi di `AGENTS.md`.

## Format laporan

Gunakan format berikut dalam laporan perubahan:

```text
Toolchain: <nama dan versi>
Perubahan: <install/update/configure atau tidak ada>
Alasan: <kebutuhan proyek>
Sumber: <repositori/vendor resmi>
Konfigurasi: <path dan ringkasan perubahan>
Verifikasi: <perintah dan hasil aktual>
Fallback/keterbatasan: <jika ada>
Konfirmasi tersisa: <tidak ada atau kategori yang berlaku>
```
