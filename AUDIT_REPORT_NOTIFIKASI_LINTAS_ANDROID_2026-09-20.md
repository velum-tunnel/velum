# Audit Notifikasi Status Bar Lintas Android

**Tanggal audit:** 20 September 2026

**Ruang lingkup:** notifikasi foreground VPN Velum, aksi tap, `PendingIntent`, task `MainActivity`, deklarasi foreground service, dan kompatibilitas compile API 33–36.

## Kesimpulan

Audit menemukan satu masalah kompatibilitas pada refactor sebelumnya. `ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED` dipakai pada cabang yang hanya memeriksa Android 10+, padahal simbol tersebut baru tersedia mulai API 34. Pada saat source dikompilasi terhadap API 33, build gagal meskipun cabang runtime itu tidak akan dieksekusi.

Masalah tersebut telah diperbaiki. Source kini memakai overload `startForeground(id, notification, type)` hanya ketika `SDK_INT >= 34`, dengan nilai tipe `1024` yang didefinisikan lokal agar source tidak merujuk langsung pada simbol API 34 saat dikompilasi terhadap API 33. Android 10–13 memakai overload dua argumen. `PendingIntent` notifikasi tetap immutable, eksplisit menuju `MainActivity`, dan menggunakan `FLAG_ACTIVITY_SINGLE_TOP | FLAG_ACTIVITY_CLEAR_TOP`.

Audit lanjutan menemukan bug kedua pada varian build: `getPackageName() + ".MainActivity"` akan menghasilkan target yang salah pada `debug` dan `preview`, karena kedua varian menambahkan suffix pada `applicationId` sedangkan nama class Java tidak berubah. Target kini dikunci ke `com.rollinkxx.velum.MainActivity`, dengan package runtime tetap diambil dari `getPackageName()`.

## Bukti verifikasi

| Target | Pengujian | Hasil |
|---|---|---|
| Android 13 / API 33 | Kompilasi source `GoBackend.java` terhadap `android.jar` API 33 | LULUS |
| Android 14 / API 34 | Kompilasi source `GoBackend.java` terhadap `android.jar` API 34 | LULUS |
| Android 15 / API 35 | Kompilasi source `GoBackend.java` terhadap `android.jar` API 35 | LULUS |
| Android 16 / API 36 | Kompilasi source `GoBackend.java` terhadap `android.jar` API 36 | LULUS |
| Artifact AAR | Bytecode memuat `PendingIntent.getActivity()` dan `setContentIntent()` | LULUS |
| Build variants | Target component tetap `com.rollinkxx.velum.MainActivity` pada debug/preview suffix | LULUS |
| Security contract | Tidak ditemukan `FLAG_MUTABLE`, `setContentIntent(null)`, atau `setAutoCancel(true)` | LULUS |
| Manifest merged preview | `MainActivity` exported dan `singleTask`; VPN service `exported=false`, `systemExempted` | LULUS |
| Supply chain | `sha256sum --check third_party/wireguard-tunnel/SHA256SUMS` | LULUS |

## Batasan pengujian runtime

Sandbox tidak menyediakan perangkat fisik, `adb`, atau Android Emulator. Karena itu, tap aktual pada shade notifikasi belum dapat diamati pada perangkat Android 13, 14, 15, dan 16. Pengujian yang dilakukan adalah pemeriksaan source, bytecode artifact final, merged manifest, serta kompilasi terhadap empat platform SDK. Uji perangkat nyata tetap diperlukan untuk memverifikasi perilaku OEM, izin notifikasi, screen-off, Doze, Always-on VPN, dan proses aplikasi yang mati sebelum notifikasi ditekan.

Tidak ada refactor runtime tambahan yang dibenarkan tanpa perangkat nyata. Perubahan lifecycle VPN yang lebih besar akan meningkatkan risiko regresi dan tidak diperlukan setelah guard API 34 serta kontrak `PendingIntent` terkunci oleh audit ini.
