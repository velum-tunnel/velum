# Changelog

Semua perubahan penting proyek ini dicatat di sini.
Format mengikuti [Keep a Changelog](https://keepachangelog.com/id-ID/1.1.0/) dan proyek ini memakai [Semantic Versioning](https://semver.org/lang/id/).

## [Unreleased]

### Security
- **Header registrasi disegarkan**: `CF-Client-Version` `a-6.10-2158` → `a-6.35-4471`
  dan `User-Agent` `okhttp/3.12.1` → `WARP for Android` — versi lama berisiko ditolak
  upstream (HTTP 426/403). Header kini dihimpun sebagai satu peta konstan
  (`VelumUpstream.API_HEADERS`) dan dijaga unit test.
- **Fallback penyimpanan polos dihapus total**: kunci privat tidak pernah lagi tersimpan
  tanpa enkripsi. Bila keystore gagal, aplikasi melempar `KeystoreUnavailableException`,
  menampilkan dialog "Penyimpanan aman tidak tersedia. Daftar ulang diperlukan.", lalu
  berhenti; berkas prefs rusak dipulihkan sekali dengan mengosongkannya.
- **Certificate pinning** untuk `api.cloudflareclient.com` lewat
  `network_security_config` (SPKI enam CA penerbit Cloudflare dari log CT, kedaluwarsa
  2027-03-31 sebagai rembesan anti-brick); prosedur rotasi di `SECURITY.md`.
- **Log sensitif diredam**: wrapper `VelumLog` mematikan level d/i pada build
  non-debug (gerbang `BuildConfig.DEBUG`), log yang memuat alamat IP endpoint diturunkan
  ke level itu, dan R8 ikut membuang `Log.d`/`Log.v` pada varian yang diperkecil.

### Added
- **Rotasi endpoint terverifikasi handshake**: kandidat pengganti hanya dipakai setelah
  handshake WireGuard singkat (≤3 detik per kandidat, maksimal 3) benar-benar terlihat;
  pengukuran RTT kini dilakukan sekali per rotasi (dulu diulang per percobaan).
- **Kandidat anycast disegarkan dari DNS-over-HTTPS** (`cloudflare-dns.com`), basi
  24 jam, dengan fallback ke daftar statis bila DoH gagal.
- **Endpoint manual** di layar utama: kolom `host:port` tervalidasi (IPv4/domain/IPv6
  berkurung), mengalahkan proba otomatis dan dipertahankan oleh Daftar ulang.
- **Dokumen keamanan & privasi**: `SECURITY.md` (rotasi pin) dan `PRIVACY.md` (izin,
  visibilitas paket tanpa `QUERY_ALL_PACKAGES`, aliran data); checklist uji perangkat
  menyusul (D2, H7, H8) menyesuaikan perilaku baru.
- **Baris Data menampilkan total pemakaian sesi** (↓/↑ kumulatif sejak tunnel naik)
  di samping laju — total muncul seketika karena dibaca langsung dari penghitung
  backend, dan reset otomatis tiap tunnel dibangun ulang (satu sesi).
- **Layar "Kecualikan aplikasi" mengelompokkan pilihan**: aplikasi yang dikecualikan
  dinaikkan ke atas di bawah label "Dikecualikan dari tunnel" (aksen emas, huruf tebal),
  sisanya di bawah "Aplikasi lain" — pilihan tidak lagi tenggelam di antara puluhan baris.
- **Subjudul "Selalu aktif" menampilkan keadaan sistem yang sebenarnya**: "Aktif · blokir
  tanpa VPN", "Aktif · tersambung otomatis", atau "Nonaktif", dibaca lewat
  `GoBackend.isAlwaysOn()`/`isLockdownEnabled()` (API 29+). Bila tidak terbaca (tunnel
  turun / API < 29), subjudul kembali ke teks netral — tidak menebak.

### Changed
- **Swipe Recent Apps tidak lagi memutus VPN**: penghancuran Activity hanya melepaskan UI;
  `wasUp`, tunnel, dan pemantau reconnect dipertahankan. Disconnect tetap dilakukan oleh aksi
  koneksi eksplisit yang sudah ada, bukan oleh penutupan task UI.

- **Recovery otomatis diperluas**: boot/update mendapat pemicu recovery eksplisit walaupun
  network default sudah tersedia, dan transisi tunnel ke `DOWN` dapat menjadwalkan pemulihan
  selama niat pengguna masih `wasUp=true`. Keduanya memakai guard generasi niat dan satu
  keputusan murni yang diuji JVM agar aksi Putuskan tetap menang.

- **Judul aplikasi membesar (30sp → 80sp) dan berkesan timbul 3D**: lapisan gelap sedikit
  turun di belakang lapisan bergradien emas, menempel ke atas layar. Judul dijamin **satu
  baris penuh** lewat `maxLines=1` + `singleLine=true` + auto-size (48–80sp) + letter-spacing
  dikecilkan (0.34 → 0.15), jadi tidak pernah membungkus ke dua baris; gradien mengikuti
  hurufnya. Keduanya teks statis yang digambar sekali, tanpa beban per-frame (prioritas
  kecepatan dipertahankan). Hirarki dibersihkan: judul + tagline dikelompokkan dalam satu
  kolom header rata tengah (gap 4dp), sisa isi dipusatkan vertikal di ruang yang tersisa.
- **Tagline diganti** "TUNNEL AMAN YANG RINGAN" → "PRIVAT, CEPAT, RINGAN".
- **Status "Tersambung" disusun sebaris** (titik + teks 14sp, `includeFontPadding=false`,
  `gravity="center_vertical"`, badge `layout_gravity="center_horizontal"`) tepat di tengah
  kartu, dengan padding kartu simetris (atas = bawah = 10dp, kiri = kanan = 16dp) supaya
  rapi dan presisi.
- **Layar utama menjadi tetap (tanpa menggulir)**: tinggi elemen ditekan agar muat satu
  layar. Konsekuensi diterima sadar — layar sangat pendek/skala huruf besar bisa
  terpotong.

### Fixed
- **Laju trafik lambat & tidak akurat**: sebelumnya dihitung tiap 5 detik sebagai selisih
  dua titik dan angka pertama baru muncul ±10 detik setelah tersambung; kini memakai
  jendela geser 5 detik (`VelumRate`, teruji JVM) dengan polling 1 Hz — angka pertama
  muncul ±1 detik dan halus saat trafik bursty. Deteksi tunnel basi dijaga tetap ±30 detik.

- **BootReceiver ikut menaikkan generasi niat sebelum menyambung ulang**, sehingga
  `up()` boot tidak lagi bisa mengalahkan `down()` yang diminta pengguna dari layar —
  keduanya `@Synchronized`, jadi tanpa penanda urutan keduanya sekadar berlomba
  memperoleh kunci. Pemantau sambung ulang juga tidak lagi dihidupkan kembali sesudah
  pengguna memutus, supaya baris `Pemantau` tidak terbaca "aktif" padahal ia baru
  meminta putus.
- **Pembatalan sambung ulang otomatis kini terlihat di baris `Boot`** sebagai outcome
  `dibatalkan`, terpisah dari `gagal`: tunnel tidak gagal menyambung, ia memang tidak
  boleh dinyalakan lagi.
- **Subjudul & toast "Salin diagnostik" tidak lagi mengklaim hal yang dibantah oleh
  isinya sendiri**: keduanya kini berbunyi "tanpa kunci & alamat IP Anda", sama dengan
  catatan di dalam ringkasan — baris `Endpoint` memang memuat IP PoP anycast.
- **Daftar ulang tidak lagi menghapus rekaman `Boot`**, yaitu satu-satunya bukti
  tanpa-adb untuk anggaran `goAsync()`.
- **Pemisah desimal diseragamkan**: baris `Data` kini memakai koma seperti baris `Boot`
  ("5,1 MB" alih-alih "5.1 MB") — sebelumnya keduanya tampil berdampingan pada ringkasan
  diagnostik yang sama dengan format berbeda.

### Added
- **Logo baru bergaya emblem** (arah "A" yang dipilih maintainer): cincin emas tipis
  mengelilingi monogram "V" — bukan huruf polos tanpa bingkai lagi. Cincin digambar
  sebagai dua lingkaran (`fillType="evenOdd"`) dengan gradien gading-emas terang di atas
  lalu amber gelap di bawah, sehingga terbaca sebagai logam, bukan bidang datar.
  Seluruh bentuk (cincin diameter 64,8 pada kanvas 108) berada di dalam zona aman
  72x72, jadi topeng peluncur apa pun tidak memotongnya.
- Blok `<queries>` untuk aksi pengaturan sistem (`VPN_SETTINGS`), supaya resolusi intent
  tetap berhasil pada API 30+ dengan pembatasan visibilitas paket.

- Varian build **preview**: konfigurasi rilis (R8 + shrink resources) yang ditandatangani
  kunci debug, sehingga APK kecil siap pasang bisa diuji di perangkat nyata tanpa keystore
  rilis. Dibangun di setiap push agar R8 teruji terus-menerus — bukan pertama kali saat
  rilis publik. Memakai `applicationIdSuffix = ".preview"` sehingga bisa dipasang
  berdampingan dengan varian debug.
- Aturan R8 diperluas dari 2 menjadi beberapa aturan bersasaran: field protobuf Tink
  (mencegah crash `EncryptedSharedPreferences` saat runtime — kegagalan senyap yang hanya
  muncul pada build yang diperkecil), `VelumTileService`, `BootReceiver`, serta
  `SourceFile`/`LineNumberTable` agar laporan crash tetap terbaca.
- CI mengunggah artifact `app-preview` dan `mapping-preview` (`mapping.txt` untuk
  memulihkan stack trace), dan step summary menyandingkan ukuran debug vs preview.
- Pemecahan APK per arsitektur (ABI split) untuk `arm64-v8a`, `armeabi-v7a`, dan `x86_64`,
  plus satu APK universal sebagai cadangan. Isi APK didominasi pustaka native WireGuard
  (satu `.so` per ABI) yang tidak tersentuh R8, sehingga memecah per arsitektur adalah
  satu-satunya cara menurunkan ukuran unduhan secara berarti — perangkat hanya mengambil
  arsitekturnya sendiri.
- `versionCode` otomatis per varian (`abiCode * 1000 + versionCode`), dengan APK universal
  memakai nilai terendah supaya varian spesifik arsitektur selalu lebih diutamakan.
- **Ikon aplikasi baru**: kartu gelap bergradien dengan monogram "V" berlapis emas
  (champagne → amber, kilau di tepi atas). Ikon adaptif kini punya lapisan **monokrom**
  sehingga ikut ikon tematik Android 13+, varian bulat tersedia lewat
  `android:roundIcon`, dan PNG legacy API 24–25 dibangkitkan dari kanvas yang sama
  sehingga tampil identik di semua versi.
- Ikon khusus untuk ubin pengaturan cepat dan notifikasi
  (`drawable/ic_launcher_tile.xml`): glif satu warna yang dipotong rapat, menggantikan
  ikon adaptif 108x108 yang hurufnya tampak kecil saat diseragamkan sistem.
- Ikon baris aksi (daur ulang, pengatur, kisi aplikasi, salin) digambar sendiri sebagai
  vektor sederhana — tanpa pustaka ikon pihak ketiga, demi ukuran APK dan lisensi.
  Catatan ukuran: PNG legacy baru menambah ±100 KB per APK (ikon lama 115-412 byte
  karena hanya warna datar; ikon bergradien 3-19 KB x10 berkas) — diukur pada run
  34681658613: `app-debug` 26,03 -> 26,45 MB, `app-preview`/`app-release` 11,92 -> 12,21 MB.


- Verifikasi `apksigner` di job rilis: build digagalkan bila APK ternyata tidak
  bertanda tangan atau memakai kunci debug, dan sidik jari SHA-256 tiap APK dicetak
  ke step summary. Sebelumnya kegagalan penandatanganan lolos diam-diam — Gradle
  tetap menghasilkan APK dan CI tetap hijau, cacatnya baru ketahuan saat pengguna
  gagal memasang.

### Changed
- **Notifikasi status kini mengikuti tunnel, bukan layar.** Sebelumnya `show`/`hide`
  hanya dipanggil dari callback visual layar utama, sehingga dua keadaan salah terjadi:
  tunnel yang mati di latar (pantulan menyerah, atau diputus lewat ubin) meninggalkan
  notifikasi "Tersambung" yang basi selamanya, dan menyambung lewat ubin pengaturan
  cepat saat aplikasi tertutup tidak memunculkan notifikasi sama sekali. Kini
  `VelumTunnel.onStateChange` yang mengelolanya — satu titik yang melihat setiap
  perubahan status, siapa pun pemicunya.
- **Sambung ulang otomatis setelah aplikasi diperbarui.** Pembaruan mematikan proses,
  dan proses yang mati berarti tunnel ikut mati; sebelumnya tidak ada yang
  menyambungkannya kembali sampai jaringan kebetulan berganti atau pengguna membuka
  aplikasi. `BootReceiver` kini juga menangani `MY_PACKAGE_REPLACED`, dengan guard yang
  sama seperti saat boot (terakhir UP, sudah terdaftar, persetujuan VPN masih berlaku).
- **"Daftar ulang" tidak lagi menghapus daftar pengecualian aplikasi.** `Prefs.clear()`
  mempertahankan `excludedApps` selain `wasUp`: pilihan split tunneling adalah
  preferensi pengguna, bukan data registrasi, dan dialog konfirmasi tidak pernah
  mengatakan bahwa daftar itu akan dibuang.
- **Rotasi layar tidak lagi mereset durasi koneksi atau menjalankan ulang uji.** Durasi
  disimpan di `VelumTunnel.upSinceElapsedMs` (umur proses) dan status awal controller
  dibaca dari keadaan tunnel yang sebenarnya, sehingga layar yang baru tidak menganggap
  "sudah UP sejak tadi" sebagai transisi baru.
- Diagnostik: kalimat catatan diperjelas menjadi "tanpa kunci privat, identitas
  perangkat, atau alamat IP **Anda**" (sebelumnya mengklaim tanpa alamat IP padahal
  baris Endpoint memuat IP PoP Cloudflare), dan memuat baris **Peringatan** bila
  penyimpanan jatuh ke berkas polos karena keystore perangkat gagal — keadaan yang
  selama ini hanya tercatat di logcat.
- `androidx.core` dideklarasikan eksplisit di katalog (versi **1.13.0**): `VelumInsets`
  memakai `ViewCompat`/`WindowInsetsCompat` secara langsung, tetapi pustakanya selama ini
  hanya hadir sebagai dependensi transitif. 1.13.0 sengaja dipilih karena itulah versi
  yang **sudah** terselesaikan di graph — `appcompat:1.8.0` dan `activity:1.9.3` sama-sama
  menarik `core:1.13.0` (bukti: POM keduanya di Google Maven), sementara
  `security-crypto:1.1.0` dan `com.wireguard.android:tunnel:1.0.20260102` tidak menarik
  `core` sama sekali. Jadi classpath, isi APK, dan ukuran APK tidak berubah satu bit pun;
  yang berubah hanya bahwa repo ini kini menyatakan sendiri apa yang dipakainya.
- **Pantulan jaringan lebih sabar** ([ReconnectMonitor]): backoff 3 percobaan
  (2/5/10 dtk) menjadi **5 percobaan (2/5/10/30/60 dtk)**. Jaringan yang baru berganti —
  habis pindah Wi-Fi, keluar mode pesawat, atau baru menyala setelah boot — sering butuh
  belasan detik sebelum benar-benar siap diakses; menyerah pada detik ke-17 terlalu cepat.
  Pantulan tetap dibatalkan begitu pengguna menekan Putuskan.
- Ikon aplikasi: monogram tanpa bingkai diganti emblem cincin + monogram; palet ikon
  ikut menyesuaikan (`icon_ring_start`/`icon_ring_end`, `icon_monogram_rim` dihapus karena
  tidak lagi dipakai). PNG legacy (10 berkas) dibangkitkan ulang dari kanvas yang sama,
  dan justru **lebih ringan** dari versi sebelumnya (1,9-10,7 KB vs 3,4-19 KB per berkas).
- Glif ubin pengaturan cepat & ikon notifikasi memakai emblem yang dipotong rapat,
  menyamakan bahasa visual ikon di layar peluncur, ubin, dan notifikasi.

- Footer "Hanya tunnel Velum. Tanpa iklan, tanpa pelacakan." dihapus dari layar utama
  beserta string-nya: memakan ruang tanpa menambah informasi, dan justru membuat tata
  letak melebihi satu layar. Pesan itu tetap hidup di sini (CHANGELOG) dan di ADR.
- **`targetSdk` 35 → 36 (Android 16)** atas izin maintainer. Konsekuensi yang ikut
  ditangani dalam perubahan yang sama, bukan ditunda:
  - **Edge-to-edge dipaksakan** — isi jendela kini berada di bawah bilah status dan
    bilah navigasi. Kedua layar (utama & pengecualian aplikasi) menerapkan insets
    bilah sistem sebagai padding akar lewat `VelumInsets.kt`; pada perangkat lama
    insets bernilai nol sehingga tidak menggandakan jarak.
  - **Predictive back dipaksakan** — pintasan "Kembali" yang baru memakai
    `onBackPressedDispatcher`, bukan `onBackPressed()` yang tidak lagi dipanggil.
  - **Izin layanan latar depan diperketat.** Bila Android menolak/menutup layanan VPN,
    `VelumError.Kind.SERVICE_BLOCKED` mengenali pola pesannya dan menampilkan langkah
    pemulihan (periksa Always-on VPN & blokir koneksi tanpa VPN) alih-alih
    "Kesalahan jaringan" yang menyesatkan. Klasifikasi ini heuristik dan teruji unit.
- **Baris aksi di layar utama dirancang ulang**: sebelumnya tombol teks polos berwarna
  pudar, kini kartu berisi empat baris dengan ikon beraksen, judul, subjudul yang
  menjelaskan akibat tiap aksi, dan penanda panah. Label "Selalu aktif (pengaturan
  sistem)" dipersingkat menjadi "Selalu aktif" — konteks sistem kini ada di subjudul.
- **Teks hasil uji memakai istilah "Aktif"** ("Aktif · DC …"), menggantikan
  "Velum aktif: lalu lintas lewat Velum" yang mengulang nama aplikasi.
- `docs/rilis-github.md` tidak berubah; panduan tetap berlaku untuk empat berkas APK.

- **AGP 8.7.3 → 9.4.0** (PR #5 Dependabot, diterapkan di branch sesi). Bukan
  sekadar ganti nomor versi — AGP 9 menghapus beberapa API yang dipakai repo ini:
  - Plugin `org.jetbrains.kotlin.android` **dihapus** dari kedua berkas build.
    AGP 9 punya Kotlin bawaan; menerapkan plugin lama justru menggagalkan build.
    Versi Kotlin ikut dihapus dari katalog karena AGP kini membawa KGP-nya sendiri
    (≥ 2.2.10) — menyimpan versi terpisah hanya menciptakan sumber kebenaran kedua.
  - `android.kotlinOptions.jvmTarget` dihapus; dengan Kotlin bawaan nilainya
    mengikuti `compileOptions.targetCompatibility` yang sudah disetel ke 17.
  - `defaultConfig.resourceConfigurations` → `androidResources.localeFilters`.
  - `compileSdk` 35 → 36 (AGP 9 mensyaratkan SDK Build Tools 36).
  - `targetSdk` sengaja **tetap 35**: menaikkannya mengubah perilaku runtime
    (izin, foreground service, VPN) dan itu keputusan produk, bukan efek samping
    pembaruan alat bangun.

#### Audit ulang (paket kedua)
- **Pengecualian aplikasi langsung berlaku, tanpa menyuruh pengguna memutus manual.**
  Sebelumnya daftar pengecualian dibaca sekali saat `Tunnel.getConfiguration()` dipakai
  untuk membangun tunnel, jadi perubahan baru terasa setelah pengguna memutus dan
  menyambung lagi sendiri — dan dua string di layar itu memang mengatakan begitu
  ("Putuskan lalu Sambungkan agar berlaku"). Itu bukan solusi, itu memindahkan pekerjaan
  aplikasi ke pengguna. Kini `save()` memanggil `VelumTunnel.restart()` bila tunnel sedang
  UP, di executor latar (bukan main thread), dan toast-nya jujur: "menyambungkan ulang…"
  saat restart memang terjadi, bukan saat hanya disimpan. Menyimpan tanpa mengubah apa pun
  tidak lagi memicu restart. Teks bantuan layar ikut diperbarui — instruksi manualnya
  dihapus karena sudah tidak benar.
- **Niat pengguna (sambung/putus) kini milik proses, bukan milik satu layar.**
  Generasi niat pindah dari field privat `VelumController` ke `VelumTunnel`
  (`bumpIntent`/`intentStale`/`currentIntent`), dan ubin pengaturan cepat serta
  `ReconnectMonitor` ikut memeriksanya. Sebelumnya ketiga pelaku itu menulis `Prefs.wasUp`
  dan menghidup-matikan pemantau tanpa penanda urutan, sehingga ada interleaving nyata:
  pengguna memutus lewat ubin, ekor `connect()` milik layar menulis `wasUp = true` dan
  menyalakan pemantau lagi, lalu tunnel yang baru dimatikan membangkitkan dirinya sendiri
  pada peristiwa jaringan berikutnya. Untuk aplikasi VPN itu kebocoran niat, bukan kosmetik.
  Yang **tidak** dijamin juga dicatat jujur di KDoc: `@Synchronized` hanya melindungi
  transisi tunnel, bukan memo niat maupun hidup/matinya pemantau.
- **Manifest tidak lagi mendeklarasikan foreground service yang tidak pernah dimulai.**
  `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE`,
  `android:foregroundServiceType="specialUse"`, dan properti subtype-nya dihapus. Bukti,
  bukan dugaan: tidak ada satu pun pemanggilan `startForeground()` — nol di aplikasi ini,
  nol di `GoBackend.java` pada sumber upstream tag 1.0.20260102 (library memulai layanan
  dengan `Context.startService` lalu menunggu 2 detik), dan manifest library sendiri tidak
  mendeklarasikan tipe apa pun. Sistem hanya memeriksa tipe saat `startForeground()`
  dipanggil, jadi deklarasi itu inert — dan menyatakan `specialUse` yang tidak dipakai
  justru menuntut pembenaran di Play Console untuk sesuatu yang tidak ada. Yang menahan
  proses tetap hidup selama tunnel UP adalah VPN yang aktif. Bila kelak memang dibutuhkan,
  tipe yang benar untuk aplikasi VPN adalah `systemExempted`, dan itu tertulis di manifest.
- **Laju trafik pada sampel pertama tidak lagi dihitung terhadap uptime perangkat.**
  Sejak rotasi layar tidak lagi dianggap transisi (perbaikan di atas), layar hasil rotasi
  tidak menerima `onConnectedVisual()` — sehingga dasar hitungan tetap kosong dan
  `lastPollMs` tetap `0`. Sampel pertama lalu membagi selisih byte dengan
  `elapsedRealtime / 1000`, hasilnya ≈ `0 B/s` selama satu siklus (~5 detik) sebelum benar
  sendiri. Kembali dari latar punya cacat yang sama dengan angka berbeda. Kini
  `resetTrafficBaseline()` dipanggil dari `onStart` setiap layar terlihat dengan tunnel UP.
- **Rotasi endpoint tidak lagi menulis preferensi di jalur gagal**, dan keputusannya
  dikeluarkan dari kelas Android menjadi objek murni `VelumEndpointChoice` dengan 9 uji
  regresi (JVM, tanpa perangkat). Sebelumnya `rotate()` menyimpan kandidat ke preferensi
  *sebelum* memeriksa apakah pemenangnya ternyata endpoint yang sedang gagal; pada jalur
  itu preferensi sudah berubah tetapi tunnel tidak diapa-apakan, dan log lama mengklaim
  "berpindah" padahal tidak ada perpindahan. Uji mencakup kasus regresinya secara
  eksplisit: pemenang bukan-IP yang sama dengan endpoint gagal → `changed = false`.
- **Penyimpanan diperbaiki di dua tempat.** `Prefs.clear()` kini satu transaksi
  (`clear()` + penulisan ulang `wasUp`/`excludedApps` + `commit()`), bukan tiga tulisan
  terpisah: proses yang mati di antaranya dulu menghapus niat dan pengecualian pengguna —
  persis kelas kegagalan yang `saveRegistration` sudah tutup. Dan fallback polos (dipakai
  bila keystore perangkat gagal) kini memakai berkas `velum_plain`, bukan berbagi nama
  `velum` dengan store terenkripsi. Berbagi nama rusak dua arah: `EncryptedSharedPreferences`
  mengenkripsi *nama* kunci juga, jadi pembacaan polos atas berkas terenkripsi melihat
  ciphertext dan menyimpulkan "belum terdaftar"; sebaliknya tulisan polos membuat berkas
  itu tidak bisa dibuka lagi sebagai store terenkripsi bila keystore pulih. **Konsekuensi
  yang diterima sadar:** perangkat yang sudah terlanjur jatuh ke fallback sebelum perubahan
  ini perlu daftar ulang sekali. Migrasi heuristik dari berkas lama ditolak karena
  membedakan "berkas polos era lama" dari "berkas terenkripsi" berarti menebak, dan tebakan
  yang salah di sini merusak data yang sebenarnya masih bisa dibaca.
- **Tiga tempat yang melebihkan kenyataan dikoreksi**, dan dua keputusan arsitektur yang
  kemarin dibuat tanpa catatan kini punya ADR 003 (kepemilikan status koneksi & model
  konkurensi, termasuk alternatif yang ditolak dan alasannya). Ditambah
  `docs/uji-perangkat.md`: checklist 30 uji dalam 7 kelompok, lengkap dengan perintah
  logcat, hasil yang diharapkan, dan kolom "bila berbeda" — karena repo ini tidak punya
  emulator di CI maupun di lingkungan agen, dan "sudah diverifikasi" tanpa perangkat adalah
  klaim yang tidak bisa dipertanggungjawabkan.
- **Risiko `BootReceiver` didokumentasikan, bukan diubah.** `goAsync()` menahan proses
  tetap hidup selama `up()`, yang bisa memakan 2 detik (menunggu VpnService) ditambah
  sampai 10×1 detik retry resolusi DNS saat boot. Menggantinya dengan "serahkan ke
  `ReconnectMonitor` lalu selesai" tidak menghapus risiko, hanya memindahkannya: tanpa
  `goAsync()` proses yang baru lahir untuk broadcast bisa dibunuh sebelum tunnel naik.
  Memilih di antara dua risiko itu butuh pengukuran di perangkat (uji F1/F2/F5), bukan
  penalaran dari sandbox.
- **Layar diagnostik kini menampilkan keadaan internal, permanen di semua varian build**
  (persetujuan maintainer 2026-09-13). Empat baris baru: `Niat` (apakah tunnel diharapkan
  hidup + berapa kali aksi sambung/putus terjadi), `Pemantau` (aktif/mati), `Proses` (umur
  proses aplikasi), dan `Boot` (berapa lama + hasil percobaan menyambung otomatis setelah
  perangkat dinyalakan atau aplikasi diperbarui).
  Alasannya bukan kosmetik: maintainer menguji di perangkat **tanpa adb**, sehingga keadaan
  yang menentukan benar/tidaknya perilaku konkurensi dan daya tahan proses sebelumnya
  **tidak bisa diperiksa sama sekali** — semuanya hanya ada di logcat. Dengan baris-baris
  ini, uji yang tadinya mustahil menjadi cukup dilihat: "tunnel menyambung sendiri setelah
  diputus" terbaca sebagai `Status: Terputus` sementara `Niat: Hidup`; "apakah penyambungan
  saat boot melewati anggaran receiver" terbaca sebagai angka pada `Boot` (mis. `14,2 detik`).
  Baris `Proses` memakai `Process.getStartElapsedRealtime()` (API 24, sama dengan `minSdk`,
  jadi tanpa guard versi) dan **melampaui daftar empat baris yang ditawarkan** — ditambahkan
  karena tanpa itu uji daya tahan proses di latar tidak punya padanan layar, padahal risiko
  itu justru muncul dari penghapusan deklarasi foreground service. Dilaporkan terbuka
  (TODO 89), hapus bila tidak dikehendaki.
  Batasnya tetap: hanya boolean, angka generasi, dan durasi — tanpa kunci privat, token,
  identitas perangkat, atau IP pengguna, dan ada uji yang menjaganya tetap begitu.
  `Prefs.bootRecord` ditulis dengan `commit()` (bukan `apply()`) karena penulisannya terjadi
  tepat sebelum `PendingResult.finish()`, sesudah itu proses boleh dibunuh kapan saja.
- - **Komentar build tidak lagi memuat angka baris yang sudah usang** (klaim "±1.900 baris"
  saat kenyataannya sudah jauh di atas itu), diganti penjelasan kenapa angkanya memang
  tidak perlu ditulis: ia berubah setiap rilis, dan komentar berisi angka usang lebih
  menyesatkan daripada komentar tanpa angka.

### Changed
- CI mengunggah seluruh varian APK (`*.apk`) alih-alih satu berkas bernama tetap, dan step
  summary kini menampilkan tabel ukuran tiap APK sehingga dampak pemecahan terlihat tanpa
  perlu mengunduh artifact.
- `docs/rilis-github.md`: panduan verifikasi & penerbitan disesuaikan untuk empat berkas APK,
  lengkap dengan tabel "pilih berkas sesuai perangkat" untuk catatan rilis.
- CI dikonsolidasikan: `assembleDebug`, `unitTest`, dan `lint` yang sebelumnya tiga job
  terpisah kini menjadi satu job `verifikasi (build, tes, lint)`. Toolchain
  (JDK + Android SDK + Gradle) disiapkan sekali, bukan tiga kali, dan cache Gradle dipakai
  ulang antar tugas — memangkas ±60% waktu runner per push. Lint tetap advisori lewat
  `continue-on-error` di level step. Urutan sengaja tes → build → lint agar kegagalan
  termurah muncul lebih dulu, dan semua tahap tetap berjalan (`if: always()`) supaya satu
  run melaporkan seluruh masalah sekaligus.
- AGENTS.md §1 kini **portabel**: nama branch sesi dan SHA pangkal tidak lagi ditulis di
  dalam aturan, melainkan ditemukan saat runtime lewat ritual pra-tugas 5 langkah; kronologi
  insiden dipindah ke §5. Ditambah larangan menyentuh branch sesi lama & `dependabot/*`.
- AGENTS.md §5 disinkronkan dengan keadaan repo setelah PR #3 ter-merge: 18 berkas Kotlin
  (termasuk `VelumController`, `VelumUpstream`, `VelumError`, `VelumRegistration`,
  `VelumMigration`, `VelumDiagnostics`, `VelumTileService`, `AppExclusionActivity`),
  CI 4 job (`assembleDebug`, `unitTest`, `lint` advisori, `release`), skrip anotasi,
  Dependabot, daftar run hijau, ukuran artifact, dan 9 PR Dependabot terbuka.
- Identitas visual & teks: seluruh teks yang terlihat pengguna kini mengikuti nama
  aplikasi. `notif_connected` dan `test_on` tidak lagi menyebut pihak ketiga, dan
  `desc_footer` menjadi "Hanya tunnel Velum. Tanpa iklan, tanpa pelacakan.".
- Judul aplikasi di bagian atas layar utama tidak lagi tulisan polos: gradien
  gading→emas (`title_start`→`title_end`) dengan pendar hangat, tagline kapital
  "Tunnel aman yang ringan", dan garis tipis emas memudar di bawahnya.

### Added
- `drawable/divider_gold.xml` (garis aksen emas memudar) dan warna `title_start`,
  `title_end`, `title_glow`.
- `MainActivity.polishAppTitle()`: gradien diterapkan pada `onPreDraw` pertama, layer
  software agar pendar tampil identik di semua perangkat.


### Added
- Dokumen fondasi proyek: CHANGELOG, TODO, ADR 001 (identitas aplikasi), `.gitignore` Android.
- Skeleton proyek Gradle (AGP 8.7.3, Kotlin 2.0.21, Gradle 8.9 wrapper, katalog `libs.versions.toml`, modul `app` minSdk 24).
- `Prefs.kt` (penyimpanan registrasi) dan `WarpApi.kt` (registrasi/hapus registrasi WARP, uji `cdn-cgi/trace`) tanpa dependensi HTTP tambahan.
- `WarpTunnel.kt`: tunnel WireGuard via `GoBackend` (MTU 1280, DNS 1.1.1.1, keepalive 25) dan `AndroidManifest.xml` (VpnService library, foregroundServiceType specialUse).
- UI satu layar Bahasa Indonesia (`MainActivity`, layout XML, tema AppCompat, ikon adaptif): Sambungkan/Putuskan, status, Uji koneksi, Daftar ulang.
- CI GitHub Actions `build.yml`: `assembleDebug`, artifact `app-debug`, step summary sebagai fallback log.
- Panel status interaktif di kartu utama: titik status berdenyut halus saat tersambung
  (animasi alpha ringan, berhenti otomatis saat terputus), durasi tersambung (tiker 1 detik,
  hanya selama UP), endpoint tunnel, dan hasil uji terakhir lengkap dengan data center
  (`colo`) dan jam cek dari `cdn-cgi/trace`; uji koneksi otomatis berjalan sekali setiap
  kali tersambung.
- Job CI opsional `assembleRelease` bertanda tangan: aktif hanya bila variable
  `ENABLE_RELEASE_SIGNING=true` dan Secrets keystore (`SIGNING_KEYSTORE_BASE64`,
  `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`) disiapkan maintainer; penandatanganan
  dibaca dari environment — tidak ada materi rahasia di repo.
- Penyembuhan otomatis (auto-heal) akun era lama tanpa flag WARP: saat menyambung, aplikasi
  memeriksa `GET /reg/{id}` sekali (memo `warpEnabled`); bila akun terbukti tanpa flag,
  registrasi lama dihapus dan perangkat didaftarkan ulang secara transparan. Bersifat
  fail-safe — keraguan/kegagalan jaringan tidak menyentuh akun dan tidak menghalangi
  penyambungan.
- Pintasan "Selalu aktif (pengaturan sistem)" menuju pengaturan VPN bawaan Android
  (always-on + blokir koneksi tanpa VPN dikelola sistem).
- Sambung ulang otomatis setelah boot (`BootReceiver` + izin `RECEIVE_BOOT_COMPLETED`)
  bila terakhir kali tunnel memang tersambung dan persetujuan VPN masih berlaku.
- Notifikasi persisten status koneksi (kanal `status`, IMPORTANCE_LOW, ketuk untuk membuka
  aplikasi) dengan permintaan izin `POST_NOTIFICATIONS` pada Android 13+.
- Sambung ulang otomatis saat konektivitas berubah (`ReconnectMonitor` lingkup aplikasi:
  pantulan tunnel dengan backoff 2/5/10 detik + debounce, pemulihan sesi saat proses
  lahir ulang, penjagaan sesi dari `BootReceiver` bila boot tanpa jaringan).
- Penyimpanan registrasi terenkripsi (`EncryptedSharedPreferences`, AES256-GCM) dengan
  migrasi sekali dari file era polos dan fallback aman bila keystore perangkat gagal.
- Baris statistik trafik di kartu status (byte naik/turun tiap 5 detik dari backend
  WireGuard) plus deteksi tunnel basi: peringatan bila 30 detik tanpa lalu lintas dan
  handshake kedaluwarsa.
- Baris Data kini menampilkan laju kecepatan (KB/s naik/turun) dari delta statistik
  backend per 5 detik.
- Proba endpoint tercepat saat menyambung (`EndpointProbe`): mengukur RTT paralel ke
  endpoint registrasi + kandidat anycast (batas ±6 detik), memakai pemenang selama
  1 jam; selalu fail-safe ke endpoint registrasi.
- `VelumUpstream`: konstanta yang mengikuti layanan upstream (basis API, versi klien,
  User-Agent, endpoint cadangan, kandidat anycast) kini dikumpulkan di satu berkas, dan
  `VelumError` mengklasifikasi kegagalan: penolakan klien (HTTP 401/403/404/410/426)
  berpesan bahwa versi Velum perlu diperbarui, kegagalan jaringan berpesan jaringan,
  sisanya memakai pesan bawaan. Teruji unit.
- Registrasi perangkat kini diulang **satu kali** setelah jeda 1,5 detik khusus untuk
  kegagalan jaringan (jaringan yang baru bangun sering gagal di percobaan pertama);
  penolakan dari server tidak pernah diulang karena percuma.
- **Kegagalan CI kini terbaca dari mana pun**: dua skrip baru
  (`.github/scripts/anotasikan-log.py` & `anotasikan-tes.py`) mengubah error kompilasi dan
  kegagalan pengujian menjadi anotasi GitHub. Sebelumnya run yang merah praktis tidak bisa
  didiagnosis dari sandbox karena log dan artifact tidak bisa diunduh (EOF).
- Dependensi `org.json` ditambahkan **khusus pengujian**: `org.json` bawaan `android.jar`
  bisa berupa rintisan di unit test JVM, sehingga parse JSON butuh implementasi nyata —
  tetap tidak ikut ke APK.

- **Validasi respons registrasi & rencana migrasi kini teruji**: `VelumRegistration`
  mem-parse sekaligus memvalidasi balasan `POST /reg` (field wajib hilang/kosong →
  `BadResponse` berpesan jelas, endpoint tanpa port dilengkapi `:2408`, host kosong →
  endpoint cadangan), dan `VelumMigration` memindahkan data era polos berdasar tipe
  dengan tipe tak dikenal yang diabaikan. Keduanya murni & teruji unit — sebelumnya
  kedua lapisan ini bisa gagal diam-diam dan berujung pada aplikasi yang tidak bisa
  menyambung tanpa sebab yang terlihat.

- Pengujian unit murni JVM untuk logika yang rawan salah (`VelumFormat`: parse
  `cdn-cgi/trace`, pemformatan byte/durasi/jam, pemisahan host:port, deteksi IPv4) beserta
  job CI `unitTest` (`./gradlew testDebugUnitTest`) sebagai **pemblokir** — regresi logika
  kini tertahan sebelum merge, bukan hanya kegagalan kompilasi.

### Changed
- AGENTS.md §3/§5 disinkronkan dengan stack, CI, dan temuan run pertama.
- Pembantu murni (`formatBytes`, `formatDuration`, parse `cdn-cgi/trace`, `hostPart`,
  `isIpLiteral`) dipindahkan dari `MainActivity`/`VelumApi`/`EndpointProbe` ke `VelumFormat`
  agar dapat diuji unit tanpa Android framework; `VelumApi.fetchTrace()` kini mengembalikan
  `VelumFormat.TraceInfo` dan deteksi WARP memakai `VelumFormat.isWarpActive()`.
- Job lint CI kini menulis laporan teks (`lint { textReport = true }`) dan mencetaknya ke
  log/ringkasan: artifact laporan tidak bisa diunduh dari sandbox, sehingga tanpa ini
  temuan lint praktis tidak terbaca.

- **Pengecualian aplikasi (split tunneling)**: aplikasi yang dipilih dilewati dari tunnel
  dan memakai jalur internet langsung, sisanya tetap lewat Velum. Daftar disimpan
  terenkripsi di `Prefs.excludedApps` dan diterapkan lewat `excludeApplications()` pada
  konfigurasi WireGuard. Daftar aplikasi dibatasi oleh `<queries>` di manifest (aplikasi
  peluncur) sehingga tidak perlu izin `QUERY_ALL_PACKAGES` yang dibatasi; Velum sendiri
  tidak bisa dikecualikan agar pemeriksaan statusnya tetap bermakna.
- **Ubin pengaturan cepat** (`VelumTileService`): menyambung/memutus dari panel cepat tanpa
  membuka aplikasi; bila persetujuan VPN belum ada, ubin membuka aplikasi.

- Tombol **Salin diagnostik**: menyalin ringkasan keadaan (versi, status, endpoint, umur
  handshake, durasi, trafik) ke clipboard untuk dilampirkan pada laporan gangguan. Isinya
  sengaja ramah privasi — tanpa kunci privat, identitas perangkat, token, atau alamat IP —
  dan aturan itu dijaga oleh pengujian unit.
- `.github/dependabot.yml`: pembaruan versi katalog Gradle (mingguan) dan GitHub Actions
  (bulanan) kini diajukan otomatis sebagai PR berlabel `dependencies`.
- Job CI `lint (advisori)` menjalankan `lintDebug` dengan `continue-on-error` — memberi
  sinyal tanpa memblokir merge, laporannya tersedia sebagai artifact.

- **Daftar ulang kini meminta konfirmasi** lewat dialog (aksi ini menghapus registrasi
  perangkat di server dan memutus niat sambung-ulang saat boot).
- Proba endpoint dijalankan juga saat `ReconnectMonitor` memantulkan tunnel, bukan hanya
  saat pengguna menekan Sambungkan — berpindah jaringan jauh kini bisa memperbaiki
  endpoint terpilih (`refresh()` tetap mengabaikan hasil yang berumur kurang dari 1 jam).
- Aksesibilitas: baris nilai (durasi/endpoint/uji/data) tidak lagi dipotong menjadi satu
  baris (`maxLines="2"`), tombol memakai `minHeight` sehingga teks tetap terbaca saat
  ukuran font sistem diperbesar, dan titik status punya `contentDescription` yang berubah
  mengikuti status.
- Izin VPN dan notifikasi diminta lewat Activity Result API — `startActivityForResult`
  dan `requestPermissions` yang sudah usang dihapus.

- Orkestrasi koneksi & uji dipisahkan dari `MainActivity` ke `VelumController`: Activity
  kini hanya merender (`VelumController.Ui`), sementara keputusan — termasuk kapan hasil
  uji boleh dipercaya — hidup di controller dan tidak ikut mati saat Activity dibuat ulang.
  Aturan keputusan uji diekstrak ke `VelumTestDecision` (murni, teruji unit).
- Tampilan dipoles menjadi tema gelap elegan: latar gradasi charcoal, kartu status rounded
  dengan titik indikator, tombol utama amber ber-ripple + tombol sekunder outline, tipografi
  `sans-serif-light/medium`, status bar selaras tema; warna ikon adaptif disamakan dengan
  aksen. Seluruhnya murni resource XML bawaan — tanpa dependensi/font eksternal, tanpa
  memengaruhi performa maupun ukuran APK secara berarti.
- Optimasi hemat daya & startup (tanpa mengubah perilaku yang terlihat, kecuali laju trafik
  yang kini baru tampil pada sampel kedua):
  - Tiker durasi 1 Hz, pemantau trafik 5 detik, dan animasi denyut titik status kini
    **berhenti saat UI tak terlihat**. Sebelumnya ketiganya terus berjalan di latar
    belakang karena proses ditahan hidup oleh VpnService selama tunnel UP.
  - `Prefs` dibuka sekali per proses (`Prefs.of()`) dan pengecekan migrasi data era lama
    memakai cek keberadaan berkas, bukan membaca + mendekripsi seluruh nilai. Mengurangi
    kerja I/O di main thread saat aplikasi dibuka dan saat pemantulan tunnel.
  - Izin `POST_NOTIFICATIONS` hanya diminta bila belum diberikan.
  - `EndpointProbe` memakai pool thread daemon bersama (menganggur → mati sendiri)
    daripada membuat dan membuang sampai 8 thread setiap kali menyambung.
  - Uji trace berjalan di executor sendiri agar tidak menahan Sambungkan/Putuskan.
  - Efek samping status (tiker, notifikasi, pembatalan uji) kini tetap dijalankan saat
    ada aksi berlangsung, sehingga tak ada status yang tertinggal bila tunnel berubah
    di tengah aksi.
- Identitas aplikasi diganti dari WARP Lite menjadi **Velum**: `applicationId`/
  namespace/package `com.rollinkxx.velum`, nama tampil, tema, string status
  ("Velum aktif…"), nama sesi VPN, file preferensi, dan class internal (`VelumApi`,
  `VelumTunnel`). Alasan: WARP® adalah merek terdaftar Cloudflare untuk kategori
  software VPN dan panduan mereknya melarang pemakaian di nama aplikasi pihak ketiga
  (lihat ADR 002). Penyebutan WARP yang tersisa hanya referensial (endpoint/protokol).

### Removed
- **Popup tawaran Always-on VPN & bebas optimasi baterai dihapus** atas permintaan
  maintainer (2026-09-12): tawaran yang muncul sendiri setelah tersambung dinilai
  mengganggu. Berkas `VelumSetup.kt` + `VelumSetupTest.kt`, memo `setupPostponed`, enam
  string tawaran, dan `<queries>` `IGNORE_BATTERY_OPTIMIZATION_SETTINGS` ikut dihapus.
  Pintasan "Selalu aktif" di baris aksi tetap ada sebagai jalan manual ke pengaturan
  sistem (kueri `VPN_SETTINGS` dipertahankan).

### Fixed
- **UI tidak lagi disentuh dari thread latar.** Jalur ulangan uji menjadwalkan
  `runTraceTest` lewat `testWorker`, dan dua baris di dalamnya menulis `TextView` tanpa
  `main.post` — padahal baris lain di berkas yang sama sudah dibungkus dengan benar,
  jadi ini kelalaian. Tidak crash hanya karena kebetulan: kedua view target berukuran
  tetap (`0dp`+weight dan `match_parent`), sehingga `checkForRelayout` mengambil jalur
  `invalidate()` dan tidak memanggil `checkThread()`. Begitu lebarnya diubah jadi
  `wrap_content`, itu `CalledFromWrongThreadException`. Kini seluruh `ui.*` lewat satu
  helper `onUi{}` yang menjalankan segera di main thread dan mengantre bila tidak.
- **Dua executor tidak lagi memperebutkan satu tunnel.** `worker` (Sambung/Putus/Daftar
  ulang) dan `testWorker` (putar endpoint) sama-sama memanggil `down`/`up`, dan guard
  `wasUp` di awal rotasi bersifat TOCTOU: pengguna yang menekan Putuskan di antaranya
  bisa berakhir dengan tunnel **hidup kembali** setelah diminta mati. `up`/`down`/
  `refreshState`/`restart` kini memakai satu kunci pada `VelumTunnel`, pasangan down+up
  saat memutar endpoint menjadi `restart()` yang atomik dan membaca ulang niat pengguna
  di dalam kunci, dan `connect()` hanya menulis `wasUp=true` bila niat itu belum
  digantikan aksi yang lebih baru.
- **Rotasi layar tidak lagi menginterupsi pembangunan tunnel.** `onDestroy` memakai
  `shutdownNow()` yang memotong thread di tengah `VelumTunnel.up()`; diganti `shutdown()`
  agar operasi VPN yang sudah dimulai selesai. Ditutup pula jalur crash nyata: rotasi
  saat `refreshStateAsync` berjalan membuat `onDone` memanggil `connect()` pada executor
  yang sudah mati → `RejectedExecutionException` di main thread.
- **Executor ubin pengaturan cepat tidak lagi bocor.** `VelumTileService` membuat satu
  executor per instance tanpa pernah mematikannya, dan thread bawaan `Executors` bersifat
  non-daemon; karena sistem membuat-membuang TileService berulang kali, threadnya
  menumpuk dan menahan proses tetap hidup.
- **Registrasi perangkat ditulis dalam satu transaksi.** Tujuh bidang yang ditulis
  terpisah lewat `apply()` meninggalkan celah: proses yang mati di tengah penulisan
  menghasilkan kunci privat baru bercampur endpoint/peer lama sementara `isRegistered`
  tetap `true`, sehingga handshake gagal terus tanpa pesan yang menunjuk penyebabnya.
- **Uji trace tidak lagi melaporkan "Belum aktif" untuk portal tawanan.** Portal dan
  proxy menjawab HTTP 200 berisi HTML, yang diparse menjadi trace kosong lalu dilaporkan
  seolah tunnelnya tidak bekerja — menuduh tunnel padahal jaringannya yang meminta
  login. Respons kini diperiksa kode HTTP-nya dan ditolak bila tidak berbentuk trace,
  sehingga host cadangan dicoba dan pengguna melihat alasan yang benar.
- `EndpointProbe.rotate()` tidak lagi mengklaim berhasil padahal tidak mengganti apa
  pun: bila kandidat terpilih bukan literal IPv4, endpoint efektif bisa jatuh kembali
  ke host yang barusan gagal handshake, dan uji ulang mengulang kegagalan yang sama.
- Ukuran thread pool proba kini **mengikuti jumlah kandidat** (`CANDIDATES.size + 1`),
  bukan angka `8` tulisan tangan yang pas-pasan. Menambah satu kandidat saja sebelumnya
  membuat tugas terakhir mengantre, tak terukur dalam anggaran 6 detik, lalu dibatalkan
  diam-diam — "endpoint tercepat" dipilih dari data yang tidak lengkap.
- Penolakan layanan latar depan yang sampai terbungkus `IOException` kini diklasifikasi
  `SERVICE_BLOCKED`, bukan `NETWORK` — sebelumnya pengguna disuruh memeriksa jaringan
  yang sehat.
- Padding daftar "Kecualikan aplikasi" memakai **dp**, bukan piksel mentah: `setPadding(0,
  12, 0, 12)` berarti 12px, yang di layar 3x hanya 4dp sehingga jarak antarbaris nyaris
  hilang di ponsel padat piksel.
- **Baris "Uji terakhir" tidak lagi berhenti di "Menunggu data…" atau menuduh jaringan
  pengguna.** Bukti perangkat 2026-09-12: status "Tersambung", Data ↓ 0 B/s, endpoint
  162.159.193.1:2408, dan pesan `Kesalahan jaringan: Unable to resolve host
  "www.cloudflare.com"` — handshake WireGuard ternyata tidak pernah terjadi, sehingga
  permintaan uji tidak keluar lewat tunnel dan galat DNS itu hanya gejala. Dua akar yang
  diperbaiki: (1) `awaitHandshake` menyatakan "siap" hanya karena antarmuka TUN UP,
  padahal handshake belum tentu ada; (2) hasil uji yang dibatalkan tidak pernah
  ditampilkan, sehingga teks sementara bisa tertinggal selamanya. Kini handshake menjadi
  syarat (handshake teramati / belum / tunnel turun), keadaan **belum ada data**
  dipisahkan dari kegagalan jaringan (`VelumTestResult.Kind.NO_DATA`, disertai saran
  tindakan: putus-sambung atau ganti jaringan), dan hasil disimpan (`Prefs.lastTest`)
  sehingga baris itu tetap benar setelah layar dibuat ulang atau aplikasi dijalankan
  kembali — pembatalan uji kini mengembalikan hasil sah terakhir, bukan menggantung.
- **Endpoint diputar otomatis saat handshake tidak pernah terjadi.** Sebelumnya uji hanya
  mengulang permintaan ke endpoint yang sama, sehingga jaringan yang memblokir endpoint
  WARP tertentu selalu berakhir "gagal". Kini bila handshake tidak terjadi dalam batas
  tunggu, `EndpointProbe.rotate` memilih kandidat **berbeda** dari yang sedang dipakai
  (refresh biasa tidak cukup — pemenang RTT-nya sama), tunnel disambung ulang, dan uji
  diulang sekali. Endpoint yang terbukti menghasilkan handshake dicatat
  (`Prefs.workingEndpoint`) dan diutamakan pada sambungan berikutnya: bukti nyata
  mengalahkan perkiraan RTT.
- Uji trace memakai host cadangan `one.one.one.one` bila `www.cloudflare.com` tidak
  terjangkau (anggaran total 14 detik, jadi tidak memperpanjang tunggu tanpa batas), teks
  sementara dibedakan ("Menunggu data…" -> "Menguji…" -> "Mencari endpoint lain…"),
  dan "Salin diagnostik" memuat baris "Uji terakhir" agar laporan gangguan membawa
  alasan, bukan hanya keadaan saat itu.

### Fixed
- **Layar utama tidak lagi terpotong.** Judul "Velum" hilang sebagian di perangkat
  pengguna: isi layar lebih tinggi dari layar, dan `android:layout_gravity="center_vertical"`
  pada anak ScrollView menggeser seluruh isi ke atas lalu memotong bagian atas secara
  permanen (tidak bisa dicapai dengan menggulir). Diperbaiki dengan `fillViewport="true"`
  + `android:gravity="center_vertical"` **di dalam** LinearLayout isi, dan seluruh tata
  letak dipadatkan: estimasi tinggi isi turun 808 -> 656 dp (ponsel 873 dp kini lega
  ~150 dp, tinggi itu diukur dengan `tools/est_layout.py` di sandbox karena Android SDK
  tidak tersedia). ScrollView tetap ada sebagai cadangan untuk layar sangat pendek atau
  skala huruf besar — kini tanpa risiko pemotongan.

- Lint dibersihkan agar tidak ada temuan tingkat *error*: `android:tint` diganti
  `app:tint` pada ikon baris aksi & tombol kembali (wajib di proyek berbasis AppCompat),
  warna ikon yang ternyata tidak terpakai dihapus, dan struktur layar pengecualian
  diratakan supaya tidak ada `layout_weight` bersarang (boros pengukuran ganda).
  Sisa peringatan advisori: usulan KTX `SharedPreferences.edit` di `Prefs.kt` —
  sengaja tidak diambil karena menambah `androidx.core:core-ktx` ke APK.
- Layar "Kecualikan aplikasi" kini punya bilah atas dengan tombol **Kembali** di kiri:
  sebelumnya satu-satunya jalan keluar adalah tombol sistem, sehingga pengguna yang
  membuka layar ini terasa terjebak. Layar juga menampilkan keterangan bila daftar
  aplikasi kosong, bukan area yang menggantung.

- Registrasi perangkat kini menyertakan flag `warp_enabled: true` agar akun terdaftar dengan
  WARP penuh (paritas klien resmi). Gejala sebelumnya: `one.one.one.one/help` menampilkan
  "Using DNS over WARP: No" meski tunnel tersambung. Perangkat yang terlanjur terdaftar
  tanpa flag perlu satu kali **Daftar ulang** dari dalam aplikasi.
- `Prefs.clear()` tidak lagi menghapus memo `wasUp`, sehingga penyembuhan akun otomatis
  tidak mematikan niat sambung-ulang saat boot; aksi Daftar ulang manual kini eksplisit
  menandai `wasUp=false` seperti Putuskan.
- Baris "Uji terakhir" tidak lagi menampilkan "Belum lewat Velum" palsu sesaat setelah
  tersambung, meski status sudah "Tersambung". Dua penyebabnya: (1) uji `cdn-cgi/trace`
  ditembakkan seketika saat `State.UP`, padahal saat itu hanya antarmuka TUN yang baru
  dibuat — handshake WireGuard belum tentu selesai; (2) `HttpURLConnection` bisa memakai
  ulang soket keep-alive dari sebelum VPN aktif, dan Android tidak memindahkan soket yang
  sudah terbuka ke VPN sehingga permintaannya keluar langsung ke internet (`warp=off`).
  Perbaikan: keep-alive dimatikan (`Connection: close` + `http.keepAlive=false`), uji
  menunggu handshake yang nyata (batas 6 detik, berhenti lebih awal bila tunnel turun),
  diulang satu kali dengan soket baru bila hasilnya negatif padahal tunnel masih UP, dan
  dibatalkan bila tunnel putus di tengah uji.
