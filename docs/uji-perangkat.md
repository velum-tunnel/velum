# Uji Perangkat Velum — tanpa adb

Checklist pengujian di perangkat Android nyata. **Dibutuhkan** karena repo ini tidak punya
emulator — tidak di lingkungan agen, tidak di CI (AGENTS.md §11.2, §12).

Disusun untuk keadaan maintainer yang sebenarnya: **Android 14, tanpa adb, tanpa komputer**
(dinyatakan 2026-09-13). Karena itu **setiap uji di sini bisa dilakukan dari layar
perangkat saja**. Tidak ada perintah `adb`, tidak ada logcat, tidak ada `dumpsys`.

- **Alat bukti utama Anda: layar Diagnostik.** Buka aplikasi → panel Diagnostik → salin.
  Isinya kini mencakup keadaan internal yang dulu hanya ada di logcat:

  ```
  Velum 0.1.0
  Status      : Tersambung
  Endpoint    : 162.159.192.1:2408
  Handshake   : 42 detik lalu
  Durasi      : 01:23
  Trafik      : turun 1,2 MB · naik 240,3 KB
  Uji terakhir: Aktif · DC SIN · 15:25
  Dikecualikan: tidak ada
  Niat        : Hidup · aksi ke-7
  Pemantau    : aktif
  Proses      : hidup 01:23
  Boot        : 3,1 detik · berhasil · 2 jam lalu
  Catatan     : tanpa kunci privat, identitas perangkat, atau alamat IP Anda
  ```

  Empat baris terakhir adalah yang baru. Cara membacanya ada di bagian
  [Cara membaca baris diagnostik](#cara-membaca-baris-diagnostik).

- **Cara melapor paling berguna:** salin **seluruh** isi diagnostik (tombol salin di panel
  itu) dan tempel apa adanya, ditambah satu kalimat apa yang Anda lihat di layar. Jangan
  dirangkum menjadi "berhasil" atau "lancar" — ringkasan tidak bisa dipakai memutuskan apa
  pun (AGENTS.md §12 butir 3).

---

## Sebelum memasang

Semua fakta di bawah dibaca dari `app/build.gradle.kts` dan `.github/workflows/build.yml`,
bukan diperkirakan.

**1. Versi tidak membedakan build.** `versionName` masih `0.1.0` dan `versionCode` dasar
`1` — §4 menetapkan bump versi hanya atas permintaan maintainer, jadi **angka versi tidak
akan berubah** antar-build selama semua perbaikan ini. Akibat praktis: Anda tidak bisa
memastikan "ini build baru" dari versi.

**2. Cara memastikan yang terpasang adalah build baru:** buka Diagnostik dan periksa ada
tidaknya empat baris baru — `Niat`, `Pemantau`, `Proses`, `Boot`. Bila baris itu tidak ada,
yang terpasang adalah build lama; jangan lanjut menguji, karena hasilnya akan menggambarkan
kode yang sudah tidak ada.

**3. Pilih berkas APK yang tepat.** Artifact `app-release` memuat **empat** APK (tiga
pemecahan arsitektur + satu universal, karena `isUniversalApk = true`), dan tiap
arsitektur punya `versionCode` sendiri (rumus `abiCode * 1000 + versionCode dasar`):

| Berkas | versionCode | Untuk |
|---|---|---|
| `app-arm64-v8a-release.apk` | 3001 | **Hampir semua ponsel Android 14 — pilih ini** |
| `app-armeabi-v7a-release.apk` | 1001 | Perangkat 32-bit lama |
| `app-x86_64-release.apk` | 2001 | Emulator / Chromebook |
| `app-universal-release.apk` | 1 | Semua arsitektur, ukuran terbesar |

**Jangan berpindah arsitektur setelah terpasang.** APK universal sengaja diberi
`versionCode` paling rendah (1), jadi memasang universal di atas arm64-v8a (3001) akan
ditolak sebagai *downgrade*. Pakai `arm64-v8a` terus.

**4. Bila pemasangan ditolak** ("aplikasi tidak dipasang" / tanda tangan tidak cocok):
itu berarti build lama di perangkat ditandatangani kunci yang berbeda. Satu-satunya jalan
adalah **hapus pemasangan dulu** — dan itu menghapus registrasi serta daftar pengecualian,
jadi Anda harus daftar ulang. `Niat` dan pengecualian memang dipertahankan oleh
`Prefs.clear()`, tetapi penghapusan pemasangan dari sistem menghapus seluruh penyimpanan.

**5. Yang diperiksa pertama kali setelah terpasang: aplikasi terbuka NORMAL, tanpa dialog
keystore.** R8 aktif pada release (`isMinifyEnabled` + `isShrinkResources`), dan R8 baru
benar-benar teruji saat runtime — ia bisa membuang kode yang ternyata dipakai (misalnya
Tink di balik `EncryptedSharedPreferences`). Sejak 2026-09-14 tidak ada lagi fallback
polos: bila penyimpanan terenkripsi gagal dibuka, aplikasi menampilkan dialog modal
**"Penyimpanan aman tidak tersedia. Daftar ulang diperlukan."** lalu menutup diri.
Bila dialog itu muncul padahal perangkat Anda normal, itu tanda R8 merusak jalur kripto,
bukan keystore perangkat yang rusak. Laporkan segera; itu alasan varian `preview` ada.

**6. Alternatif bila release bermasalah:** artifact `app-preview` isinya **sama** dengan
release (R8 + shrink, `isDebuggable = false`, `initWith(release)`) tetapi ditandatangani
kunci debug dan punya `applicationIdSuffix .preview` — jadi bisa dipasang berdampingan
tanpa bentrok tanda tangan, dan `mapping.txt`-nya diunggah (release **tidak** mengunggah
mapping, sehingga crash pada release tidak bisa diurai). Konsekuensinya: aplikasi terpisah,
registrasi terpisah, dan tunnel-nya sendiri.

**7. Tempat mengunduh:** halaman run CI
`https://github.com/rollinkxx/velum/actions/runs/34725480643` → bagian **Artifacts**.
Agen tidak bisa mengunduh artifact dari sandbox (sudah diverifikasi gagal dua kali, EOF ke
blob storage), jadi pengunduhan hanya bisa dari browser Anda.

---

## Cara membaca baris diagnostik

| Baris | Artinya | Tanda ada masalah |
|---|---|---|
| **Niat** | Apakah tunnel *diharapkan* hidup (`Hidup`/`Mati`) dan berapa kali aksi sambung/putus terjadi (`aksi ke-N`) | `Status: Terputus` tapi `Niat: Hidup` → tunnel akan menyambung sendiri tanpa Anda minta. **Itu bug.** |
| **Pemantau** | Apakah pemantau sambung-ulang otomatis sedang aktif | `Pemantau: aktif` padahal Anda baru saja memutus manual → pemantau tidak dimatikan |
| **Proses** | Sudah berapa lama **proses aplikasi** hidup (bukan berapa lama tunnel tersambung) | Angka ini jauh lebih kecil dari lamanya Anda membiarkan aplikasi di latar → proses sempat mati dan lahir lagi, tunnel ikut mati bersamanya |
| **Boot** | Percobaan menyambung otomatis terakhir (setelah perangkat menyala atau aplikasi diperbarui): lama, hasil, dan kapan | `GAGAL`, atau durasi mendekati/melebihi `10,0 detik`, atau `belum ada percobaan` padahal Anda baru saja memulai ulang perangkat |

Angka `aksi ke-N` tidak berarti apa-apa sendirian. Yang berarti: **naik berapa kali** setelah
satu tindakan Anda. Tekan Putuskan sekali → angka naik 1. Naik 2 atau lebih = ada pelaku lain
(layar, ubin, pemantau) yang ikut bertindak.

---

## Kelompok A — status & durasi (semua V1)

| # | Tingkat | Langkah | Yang diharapkan di layar | Bila berbeda |
|---|---|---|---|---|
| A1 | V1 | Sambungkan. Putar layar 2×. Kunci layar, buka lagi. | `Durasi` terus bertambah, tidak kembali `00:00`. `Trafik` **langsung** menampilkan angka masuk akal, bukan `0 B/s` selama ~5 detik | Durasi reset = umur tunnel tidak dibaca dari proses. `0 B/s` sesaat = dasar hitungan laju tidak direset |
| A2 | V1 | Putuskan tunnel. Tekan **ubin** di panel cepat untuk menyambung, **tanpa membuka aplikasi**. Tunggu, lalu buka panel notifikasi. | Notifikasi "Tersambung" muncul walau aplikasi tidak pernah dibuka | Notifikasi hanya diposting oleh layar |
| A3 | V1 | Sambungkan. Matikan VPN dari **Pengaturan sistem → Jaringan → VPN** (bukan dari aplikasi). Buka aplikasi. | `Status: Terputus`, notifikasi hilang, `Durasi` kosong. **`Niat: Mati`** | Notifikasi menetap = status basi. `Niat: Hidup` = niat bocor |
| A4 | V1 | Sambungkan. Biarkan **30 menit** dengan layar mati, jangan buka aplikasi. Buka lagi, lihat diagnostik. | `Status: Tersambung` dan **`Proses: hidup 30:xx`** (kira-kira selama Anda meninggalkannya) | `Proses` hanya beberapa menit = proses mati di latar dan tunnel sempat putus. Ini risiko yang sengaja diambil saat deklarasi foreground service dihapus — **laporkan angkanya apa adanya** |
| A5 | V1 | Sambungkan, biarkan 5 menit, lalu buka diagnostik dua kali berjarak 1 menit. | `Proses` dan `Durasi` bertambah keduanya, selisihnya konsisten | `Durasi` bertambah tapi `Proses` reset = proses lahir ulang diam-diam |

## Kelompok B — niat pengguna lintas pelaku (V1 lewat baris Niat)

Yang diuji di sini **gejalanya**, bukan mekanismenya. Anda tidak perlu presisi milidetik:
lakukan secepat yang wajar, lalu baca baris `Niat`.

| # | Tingkat | Langkah | Yang diharapkan | Bila berbeda |
|---|---|---|---|---|
| B1 | V1 | Tekan **Putuskan** di aplikasi, lalu **segera** tekan ubin untuk menyambung. Buka diagnostik. | Keadaan akhir **konsisten**: `Status` dan `Niat` sepadan (sama-sama hidup atau sama-sama mati) | `Status: Terputus` + `Niat: Hidup` = tunnel akan menyambung sendiri → **bug, laporkan** |
| B2 | V1 | Tekan ubin untuk menyambung, lalu **secepatnya** buka aplikasi dan tekan Putuskan sebelum selesai. Tunggu 15 detik, buka diagnostik. | `Status: Terputus`, **`Niat: Mati`**, `Pemantau: mati`. Tunnel **tidak** menyambung sendiri setelah itu | Tunnel hidup lagi sendiri = aksi ubin menimpa setelah Putuskan |
| B3 | V1 | Sambungkan. Matikan Wi-Fi **dan** data, tunggu 10 detik, hidupkan lagi. Ulangi 3× cepat. Buka diagnostik tiap kali. | `Status: Tersambung` kembali. `aksi ke-N` tidak melonjak liar (naik wajar, tidak belasan) | Niat melonjak banyak = pemantau memantul berulang tanpa kendali |
| B4 | V1 | Putuskan **secara manual** dari aplikasi. Lalu matikan-hidupkan jaringan. Buka diagnostik. | Tunnel **tetap mati**: `Status: Terputus`, `Niat: Mati`, `Pemantau: mati` | `Pemantau: aktif` atau tunnel hidup lagi = niat "putus" tidak dihormati |
| B5 | V1 | Sambungkan. Matikan jaringan, biarkan **2 menit**, hidupkan lagi. Tunggu 1 menit, buka diagnostik. | `Status: Tersambung` pulih sendiri tanpa Anda menyentuh aplikasi | Tidak pulih = pemantau menyerah terlalu cepat |
| B6 | V1 | Sambungkan, lalu swipe Velum dari **Recent Apps** tanpa menekan Putuskan. Tunggu 1 menit dan buka lagi dari launcher. | VPN tetap tersambung atau pulih otomatis; `Niat: Hidup`, `Pemantau: aktif`. Swipe hanya menutup UI | `Niat: Mati`, `Pemantau: mati`, atau tunnel tidak pulih = swipe masih diperlakukan sebagai disconnect |

## Kelompok C — pengecualian aplikasi (V1, lewat situs pemeriksa IP)

| # | Tingkat | Langkah | Yang diharapkan | Bila berbeda |
|---|---|---|---|---|
| C1 | V1 | Dalam keadaan **tersambung**: buka Pengecualian, centang **browser** Anda, Simpan. | Toast berbunyi "…menyambungkan ulang…". Tunnel putus-sambung **sekali** (~1–3 detik), lalu tersambung lagi | Tidak ada restart = perubahan tidak diterapkan. Restart berulang = ada yang memantul |
| C2 | V1 | Bukti C1 **tanpa adb**: sebelum mencentang, buka `cloudflare.com/cdn-cgi/trace` di browser dan catat baris `ip=` dan `loc=`. Sesudah mencentang + tersambung lagi, buka halaman yang sama. | `ip=` dan `loc=` **berubah** menjadi IP/lokasi asli Anda (bukan Cloudflare) — artinya browser keluar dari tunnel. Aplikasi lain yang tidak dicentang tetap lewat tunnel | Tidak berubah = pengecualian tidak sampai ke sistem |
| C3 | V1 | Buka Pengecualian, **jangan ubah apa pun**, tekan Simpan. | Toast "Daftar pengecualian disimpan." **tanpa** "menyambungkan ulang…", dan tunnel tidak putus | Ikut restart = membuang waktu tiap kali Simpan |
| C4 | V1 | Dalam keadaan **putus**: ubah daftar, Simpan, buka diagnostik. | Hanya tersimpan. `Status: Terputus`, `Niat: Mati` — aplikasi tidak menyalakan tunnel sendiri | Tunnel menyambung = niat "sedang putus" diabaikan |

## Kelompok D — penyimpanan & registrasi (V1)

| # | Tingkat | Langkah | Yang diharapkan | Bila berbeda |
|---|---|---|---|---|
| D1 | V1 | Susun daftar pengecualian (2 aplikasi). Sambungkan. Lalu **Daftar ulang**. Setelah selesai, buka Pengecualian lagi. | Kedua aplikasi **masih tercentang**. Tunnel diputus selama pendaftaran ulang | Daftar kosong = `clear()` menghapus pilihan Anda |
| D2 | V1 | Buka aplikasi seperti biasa. | Aplikasi terbuka normal **tanpa** dialog "Penyimpanan aman tidak tersedia. Daftar ulang diperlukan." | Dialog itu muncul = keystore perangkat (atau jalur kripto hasil R8) gagal. Sejak 2026-09-14 kunci privat TIDAK pernah disimpan tanpa enkripsi — aplikasi menolak bekerja dalam keadaan itu. **Laporkan segera** bila muncul di perangkat normal |
| D3 | V1 | Paksa aplikasi berhenti (Pengaturan → Aplikasi → Velum → **Paksa berhenti**) **tepat saat** menekan Sambungkan/Daftar ulang. Buka lagi. | Aplikasi tetap bisa dipakai: atau tersambung penuh, atau kembali ke keadaan sebelum itu — **tidak campuran** (mis. terdaftar tapi tidak bisa menyambung) | Keadaan campuran = penulisan penyimpanan tidak atomik |

## Kelompok E — rotasi endpoint (V1)

| # | Tingkat | Langkah | Yang diharapkan | Bila berbeda |
|---|---|---|---|---|
| E1 | V1 | Dalam keadaan tersambung, putar endpoint dari layar utama. | Notifikasi hilang-muncul sebentar, `Durasi` kembali `00:00`, `Endpoint` berubah, lalu tersambung lagi | Endpoint tidak berubah tapi aplikasi mengklaim berpindah = laporan palsu |
| E2 | V1 | Putar endpoint **5× berturut-turut**, masing-masing tunggu selesai. | Semua selesai, aplikasi tetap responsif, tidak ada dialog "tidak merespons" | Macet/ANR = kunci tunnel menahan terlalu lama |
| E3 | V1 | Putar endpoint saat tunnel **putus**. Buka diagnostik. | `Status: Terputus`, dan tidak ada klaim berhasil berpindah | Aplikasi melaporkan berpindah padahal tidak = temuan B4 belum tertutup |

## Kelompok F — boot & pembaruan (V1 lewat baris Boot)

Ini kelompok yang paling penting untuk keputusan TODO 77, dan sekarang **tidak butuh
logcat sama sekali** — angkanya ada di baris `Boot`.

| # | Tingkat | Langkah | Yang diharapkan | Bila berbeda |
|---|---|---|---|---|
| F1 | V1 | Dalam keadaan tersambung, **mulai ulang perangkat**. Setelah menyala, **jangan buka aplikasi** selama 2 menit. Lalu buka diagnostik. | `Status: Tersambung`, dan baris `Boot` berisi durasi + `berhasil` + umur beberapa menit | `Boot: … GAGAL` atau `belum ada percobaan` padahal Anda baru memulai ulang = sambung ulang boot tidak jalan |
| F2 | V1 | **Catat angka durasi pada baris `Boot` dari F1.** Inilah angka keputusan itu. | Kurang dari `10,0 detik` | **`10,0 detik` atau lebih = anggaran receiver terlampaui.** Laporkan angkanya persis (mis. `14,2 detik`); dari situ TODO 77 diputuskan |
| F3 | V1 | Dalam keadaan **putus**, mulai ulang perangkat, tunggu 2 menit, buka diagnostik. | `Status: Terputus` (tunnel tidak menyambung sendiri), `Niat: Mati`, baris `Boot` **tidak** berubah menjadi percobaan baru | Tunnel menyambung sendiri = niat "putus" tidak dihormati saat boot |
| F4 | V1 | Perbarui aplikasi (pasang APK baru menimpa yang lama) saat tunnel tersambung. Tunggu 2 menit, buka diagnostik. | `Status: Tersambung` kembali tanpa Anda buka aplikasi; baris `Boot` terisi percobaan baru | Tetap putus = `MY_PACKAGE_REPLACED` tidak bekerja |
| F5 | V1 | Setelah F1/F4, perhatikan layar selama 1 menit: adakah jeda panjang, layar "Aplikasi tidak merespons", atau notifikasi sistem soal aplikasi yang menguras baterai? | Tidak ada | Ada → catat **kapan** dan **apa bunyinya** persis. Ini pengganti pemeriksaan `broadcast timeout` yang tadinya butuh adb |

## Kelompok G — izin & kegagalan (V1)

| # | Tingkat | Langkah | Yang diharapkan | Bila berbeda |
|---|---|---|---|---|
| G1 | V1 | Cabut izin notifikasi (Pengaturan → Aplikasi → Velum → Izin → Notifikasi: mati). Lalu sambungkan. | Tunnel tetap tersambung, aplikasi tidak menutup sendiri | Crash = izin dianggap wajib |
| G2 | V1 | Hapus data aplikasi (atau pasang ulang) lalu tekan Sambungkan, dan **tolak** dialog persetujuan VPN. | Pesan jelas, `Status: Terputus`, tidak ada notifikasi "Tersambung" palsu | Notifikasi/klaim tersambung padahal ditolak |
| G3 | V1 | Nyalakan **mode pesawat**, lalu tekan Sambungkan. | Kegagalan dijelaskan dengan kalimat yang bisa dimengerti, tombol tidak terkunci permanen | Tombol macet di "Menyambungkan…" selamanya |
| G4 | V1 | Masih dalam mode pesawat, tunggu 30 detik, lalu matikan mode pesawat. Jangan sentuh aplikasi. Tunggu 1 menit, buka diagnostik. | `Status: Tersambung` pulih sendiri | Tidak pulih = pemantau tidak bekerja setelah jaringan kembali |

---

## Kelompok H — trafik, pengecualian, selalu aktif & tampilan (semua V1)

| # | Tingkat | Langkah | Yang diharapkan | Bila berbeda |
|---|---|---|---|---|
| H1 | V1 | Sambungkan, lihat baris `Data`. | **Total** (mis. `Total ↓ 24 KB · ↑ 12 KB`) tampil segera; laju (baris pertama) muncul dalam ±2 detik, bukan belasan detik | Laju baru muncul setelah 5+ detik = polling tidak 1 Hz; total kosong = total tidak dibaca dari penghitung kumulatif |
| H2 | V1 | Sambil tersambung, unduh berkas besar (atau streaming video) dan perhatikan baris `Data`. | Laju naik dan turun secara **halus**, tanpa melompat liar tiap beberapa detik | Angka melompat jauh = laju dihitung selisih dua titik, jendela geser tidak jalan |
| H3 | V1 | Centang 2 aplikasi di "Kecualikan aplikasi", Simpan, buka lagi layar itu. | Kedua aplikasi berada **di atas**, di bawah label **"Dikecualikan dari tunnel"** (emas); sisanya di bawah **"Aplikasi lain"** | Aplikasi tercentang tersebar di tengah daftar = pengelompokan tidak jalan |
| H4 | V1 | Dalam keadaan **tersambung**: buka Pengaturan sistem → aktifkan **Always-on VPN** untuk Velum (lalu "Blokir koneksi tanpa VPN" juga), kembali ke aplikasi. | Subjudul baris "Selalu aktif" berubah menjadi "Aktif · blokir tanpa VPN" (atau "Aktif · tersambung otomatis" bila tanpa blokir). Matikan always-on → subjudul "Nonaktif" | Subjudul tetap "Pengaturan VPN sistem" saat tersambung = pembacaan gagal |
| H5 | V1 | Buka aplikasi; periksa judul besar + tagline baru, dan pastikan seluruh isi (sampai "Salin diagnostik") terlihat **tanpa menggulir** pada ponsel biasa. | Judul "Velum" besar berkesan timbul; tagline "PRIVAT, CEPAT, RINGAN"; status satu baris; layar **tidak bisa digulir** dan tidak ada yang terpotong | Ada isi terpotong/tertutup bilah bawah = layar tidak muat; layar bisa digulir = ScrollView tidak diganti |
| H6a | V1 | Prasyarat: perangkat sudah terdaftar **dan** pernah tersambung. Hidupkan ulang ponsel — atau pasang pembaruan aplikasi di atas yang lama, karena itu memicu receiver yang sama — lalu buka aplikasi dan salin diagnostik. | Baris `Boot` terisi, mis. `Boot : 0,8 detik · berhasil · 9 menit lalu`; labelnya salah satu dari `berhasil`, `GAGAL`, `dilewati (izin VPN tidak ada)`, atau `dibatalkan (niat pengguna lebih baru)` | Baris tetap `belum ada percobaan` padahal prasyaratnya terpenuhi = receiver tidak menulis rekaman. Bila prasyaratnya belum terpenuhi, baris itu wajar kosong dan uji **belum dapat dinilai** — bukan kegagalan |
| H6b | V1 | Catat baris `Boot` lebih dulu, lalu tekan **Daftar ulang**, tunggu pesan "Registrasi dihapus. Tekan Sambungkan untuk mendaftar ulang.", dan salin diagnostik lagi. | Baris `Boot` **masih ada dengan durasi dan outcome yang identik** — hanya bagian umurnya yang bertambah | Baris berubah menjadi `belum ada percobaan` = `prefs.clear()` menghapus rekaman boot, regresi atas perbaikan `13e75bd` |
| H7 | V1 | Buka baris **Endpoint manual**. (a) Isi `162.159.193.1:2408`, Simpan: subjudul baris menampilkan nilai itu dan baris `Endpoint` ikut berubah. (b) Buka lagi, isi `999.1.1.1:abc`, Simpan: kolom menolak dengan pesan format, dialog **tidak tertutup**. (c) Sambungkan - aplikasi menyambung dengan endpoint itu. (d) Hapus lewat tombol Hapus: subjudul kembali ke teks otomatis. | (a) nilai tersimpan & tampil, (b) ditolak tanpa menutup dialog, (c) tersambung memakai endpoint manual, (d) kembali otomatis | Isi salah diterima/diarahkan diam-diam ke endpoint lain = validasi bocor; subjudul tidak berubah = tampilan basi |
| H8 | V1 | **Hapus data aplikasi** (atau pasang ulang), lalu Sambungkan sampai `Status: Tersambung`. | Registrasi berhasil dan tunnel naik — ini sekaligus membuktikan (i) header klien baru (`CF-Client-Version: a-6.35-4471`, `User-Agent: WARP for Android`) diterima upstream, dan (ii) pin sertifikat `api.cloudflareclient.com` tidak menolak sertifikat asli Cloudflare | Gagal `HTTP 403/426` = header perlu disegarkan lagi; gagal TLS/`SSLHandshakeException` = pin usang, rotasi mengikuti `SECURITY.md` |

## Yang TIDAK bisa Anda uji — dan jangan dicoba

Berikut ini dulu tertulis sebagai tugas Anda. Semuanya **ditarik kembali**: tanpa adb tidak
ada cara menjalankannya, dan memintanya berarti memindahkan beban yang seharusnya dipikul
agen (AGENTS.md §12, "Kewajiban mengubah V3 menjadi V1").

| Dulu diminta | Kenapa tidak lagi | Penggantinya |
|---|---|---|
| `adb shell dumpsys package … \| grep foregroundServiceType` | Butuh adb | Sudah dipastikan dari sumber: manifest tidak lagi mendeklarasikannya, dan library upstream tidak memanggil `startForeground()` sama sekali. Yang tersisa adalah **akibatnya** di runtime → diuji lewat A4/A5 (baris `Proses`) |
| `adb logcat` per-tag untuk semua kelompok | Butuh adb | Baris diagnostik `Niat`/`Pemantau`/`Proses`/`Boot` |
| `adb shell run-as … ls shared_prefs/` (memeriksa berkas penyimpanan) | Butuh adb + build debug | Dialog "Penyimpanan aman tidak tersedia" (D2) — dan tidak ada lagi berkas polos yang perlu dicari: fallback polos sudah dihapus 2026-09-14 |
| `adb reboot`, `adb install -r` | Butuh adb | Mulai ulang perangkat lewat menu sistem (F1), pasang APK menimpa lewat pengelola berkas (F4) |
| Mengukur jendela race ~1 detik antar-thread | Bukan pengamatan manusia | Gejalanya yang diuji (B1/B2) lewat baris `Niat` |
| `grep 'broadcast timeout'` di logcat | Butuh adb | Pengamatan langsung (F5) + durasi di baris `Boot` (F2) |

**Yang tetap tidak terverifikasi oleh siapa pun** (status permanen `hanya nalar`, tercatat di
ledger): apakah kunci `@Synchronized` benar-benar menserialisasi transisi tunnel di bawah
tekanan nyata, dan apakah ada interleaving langka yang hanya muncul pada beban tertentu.
Keduanya tidak punya gejala layar yang bisa dipancing dengan sengaja. Risikonya dicatat apa
adanya di `docs/verifikasi-perangkat.md`, bukan disembunyikan di balik kata "sudah diuji".

## Cara melaporkan

Tempel untuk tiap uji: **nomor uji**, **apa yang Anda lihat** (salinan baris diagnostik +
satu kalimat), dan **vonis Anda bila mau** (`LULUS` / `GAGAL` / `tidak sesuai harapan`).
Bila sebuah uji tidak dijalankan, tulis `tidak diuji` — jangan dikosongkan, dan jangan
dianggap lulus.

Agen yang merapikannya ke format ledger; Anda tidak perlu menulis ulang apa pun.


## Kelompok I — Android 15: task Recent Apps dan ikon VPN

Status: **belum diuji pada perangkat fisik oleh agen**. Jalankan pada Android 15 dengan
notifikasi Velum diizinkan dan tunnel tersambung.

| # | Langkah | Yang diharapkan | Bila berbeda |
|---|---|---|---|
| I1 | Buka Velum, sambungkan VPN, pastikan notifikasi/ikon VPN terlihat, lalu buka Recent Apps. | Tunnel dan notifikasi berada pada keadaan tersambung sebelum task dihapus. | Catat keadaan awal. |
| I2 | Swipe kartu Velum dari Recent Apps. Jangan membuka aplikasi lain yang mengubah VPN. Tunggu 5 detik. | UI/task hilang, tetapi tunnel, notifikasi, dan ikon VPN tetap aktif. | Jika tunnel atau monitor mati, catat waktu dan screenshot. |
| I3 | Buka Pengaturan → VPN. | Velum masih tercatat sebagai VPN aktif; always-on yang sengaja dikonfigurasi tidak boleh diubah oleh fix ini. | Catat apakah always-on aktif. |
| I4 | Buka Velum kembali. | Aplikasi terbuka tanpa auto-connect yang tidak diinginkan; status `Niat` tetap Hidup dan `Pemantau` tetap aktif. | `Niat: Mati` atau `Pemantau: mati` setelah swipe adalah bug. |
| I5 | Tekan tombol **Putuskan** di Velum. | Tunnel berhenti, notifikasi hilang, `Niat: Mati`, dan `Pemantau: mati`. | Tunnel hidup lagi atau monitor tetap aktif = aksi Putuskan tidak dihormati. |
| I6 | Ulangi I2 dua kali dan lakukan rotasi/background biasa pada sesi lain. | Swipe, rotasi, dan background tidak memutus tunnel; tidak ada crash atau reconnect ganda. | Pisahkan hasil swipe dari uji rotasi agar diagnosis tidak rancu. |

Bukti minimum: screenshot sebelum/sesudah, status Pengaturan → VPN, dan waktu relatif setiap tindakan.
Jangan menyebut I1–I6 berhasil sebelum dijalankan pada perangkat fisik Android 15.
