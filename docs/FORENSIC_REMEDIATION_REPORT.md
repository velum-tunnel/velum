# Laporan Remediasi Forensik Velum

**Tanggal:** 18 September 2026
**Branch:** `fix/forensic-hardening-2026-09-18`
**Status:** perubahan lokal belum dipush dan belum dipublikasikan.

## Perubahan yang diterapkan

Validasi handshake pada `VelumConnectionContract` kini membawa batas minimum `latestHandshakeEpochMillis` yang diambil sebelum `up()` atau `restart()`. Handshake lama tidak lagi cukup untuk menyatakan endpoint baru berhasil. Regression test ditambahkan untuk timestamp stale, timestamp nol, dan timestamp fresh.

`Prefs` tidak lagi menghapus encrypted preferences ketika pembukaan gagal. Exception generik kini diperlakukan sebagai kegagalan storage/Keystore yang harus dipulihkan secara eksplisit. Hasil Boolean dari `commit()` pada `saveRegistration()` dan `clear()` diperiksa; kegagalan persistence menghasilkan `PersistenceException`. Error tersebut diklasifikasikan sebagai `STORAGE` agar tidak masuk retry network dan ditampilkan dengan pesan yang sesuai.

Respons registrasi upstream kini divalidasi setelah normalisasi endpoint. Host domain/IPv4, bracketed IPv6, dan port 1–65535 didukung; port nonnumerik, port di luar rentang, dan format host malformed ditolak sebelum data dipersist. Test untuk port invalid dan IPv6 valid ditambahkan.

Migrasi legacy kini hanya menyalin `Set` bila seluruh elemennya bertipe `String`. Set campuran atau malformed diabaikan tanpa menggagalkan migrasi nilai lain.

## File berubah

- `app/src/main/java/com/rollinkxx/velum/VelumConnectionContract.kt`
- `app/src/main/java/com/rollinkxx/velum/Prefs.kt`
- `app/src/main/java/com/rollinkxx/velum/PersistenceException.kt`
- `app/src/main/java/com/rollinkxx/velum/VelumError.kt`
- `app/src/main/java/com/rollinkxx/velum/VelumController.kt`
- `app/src/main/java/com/rollinkxx/velum/VelumRegistration.kt`
- `app/src/main/java/com/rollinkxx/velum/VelumMigration.kt`
- `app/src/main/res/values/strings.xml`
- Test terkait handshake, error classification, migrasi, dan registration parser.
- `docs/FORENSIC_REMEDIATION_PLAN.md`

## Verifikasi

Pemeriksaan `git diff --check` berhasil. Pemeriksaan deterministik source berhasil untuk memastikan tidak ada `deleteEncryptedFile`, hasil commit diperiksa, freshness handshake terhubung ke jalur connect/reconnect, validasi endpoint dipanggil, dan persistence failure diklasifikasikan sebagai `STORAGE`. Pemeriksaan pola secret pada baris tambahan tidak menemukan private key atau token hardcoded baru.

Perintah Gradle berikut dicoba:

```text
./gradlew --no-daemon testDebugUnitTest lintDebug assemblePreview --stacktrace
```

Perintah berhenti sebelum kompilasi dengan `SDK location not found` karena sandbox tidak memiliki `ANDROID_HOME`, Android SDK, atau `local.properties`. `kotlinc` juga tidak tersedia. Oleh karena itu **tidak ada klaim bahwa unit test, lint, atau APK build telah lulus**. Verifikasi wajib dilanjutkan di CI atau environment yang memiliki Android SDK.

## Risiko tersisa

Temuan lifecycle, boot recovery deadline, listener ownership, notification reconciliation, redirect policy, certificate pin expiry, CI action SHA pinning, dependency verification, branch protection, release provenance, dan dynamic Android/GoBackend behavior belum diperbaiki dalam perubahan ini. Temuan tersebut tetap tercantum pada laporan audit utama dan memerlukan fase terpisah agar scope tetap dapat direview.

## Kesimpulan

Patch ini menutup empat jalur correctness/security utama yang dapat diperbaiki tanpa perubahan arsitektur atau dependency baru. Status keseluruhan belum dapat dinyatakan production-ready sampai CI menjalankan test/build/lint dan pengujian Android instrumentation memverifikasi freshness handshake serta error storage pada perangkat nyata.
