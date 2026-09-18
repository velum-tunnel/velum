# ADR 003 — Kepemilikan status koneksi & model konkurensi

- **Status:** Accepted
- **Tanggal:** 2026-09-12
- **Terkait:** ADR 002 (identitas), batasan artifact CI sandbox (jebakan 2026-09-12), TODO 71 (utang uji perangkat)

## Konteks

Velum punya **satu proses** dan **banyak pelaku** yang bisa menyalakan atau mematikan
tunnel, masing-masing dengan thread sendiri:

| Pelaku | Pemicu | Thread |
| --- | --- | --- |
| `MainActivity` + `VelumController` | tombol Sambungkan/Putuskan/Daftar ulang, auto-uji | `worker`, `testWorker` (2 executor single-thread) |
| `VelumTileService` | ubin pengaturan cepat | executor sendiri |
| `BootReceiver` | `BOOT_COMPLETED`, `MY_PACKAGE_REPLACED` | thread buatan sendiri |
| `ReconnectMonitor` | peristiwa jaringan default | executor sendiri |

Sebelum keputusan ini, keadaan koneksi tersebar: status dibaca dari backend, durasi
disimpan sebagai jam milik Activity (`connectedSinceMs`), notifikasi hanya bisa diposting
dan dibatalkan oleh Activity, dan "niat pengguna" (`Prefs.wasUp`) ditulis oleh siapa pun
yang kebetulan selesai lebih dulu.

Tiga cacat nyata yang muncul dari penyebaran itu:

1. **Layar dibuat ulang setiap rotasi** (manifest tanpa `configChanges`), dan jam milik
   Activity mulai dari nol → durasi koneksi tampil `00:00` padahal tunnel tidak pernah
   putus.
2. **Notifikasi menjadi basi atau tidak pernah ada.** Satu-satunya pemanggil `hide()` ada
   di Activity, jadi tunnel yang mati di latar meninggalkan notifikasi "Tersambung"
   selamanya; dan menyambung lewat ubin dengan aplikasi tertutup tidak memunculkan
   notifikasi sama sekali.
3. **Niat pengguna saling ditimpa.** `disconnect()` menulis `wasUp = false` lalu mengantre
   `down()`, sementara `connect()` yang masih berjalan menulis `wasUp = true` setelah
   `up()` selesai. Hasilnya tunnel mati tetapi tercatat "diniatkan UP", sehingga
   `BootReceiver` dan `ReconnectMonitor` menyambungkannya lagi — untuk aplikasi VPN,
   menghidupkan kembali tunnel yang baru diminta mati adalah kebocoran niat, bukan
   kosmetik.

Batasan yang diterima: tidak ada coroutine, tidak ada dependensi baru (batasan arsitektur proyek), dan
tidak ada emulator di lingkungan kerja agen maupun di CI, sehingga apa pun yang bergantung
pada Android framework tidak bisa diuji otomatis.

## Keputusan

**1. `VelumTunnel` adalah pemilik tunggal keadaan koneksi, seumur proses.**
Status (`state`), umur koneksi (`upSinceElapsedMs`), dan generasi niat (`intentGen`) hidup
di sana — bukan di Activity, bukan di controller. Alasannya: umur data harus sama dengan
umur pemiliknya. Durasi adalah sifat koneksi, maka disimpan bersama koneksi.

**2. Satu kunci untuk semua transisi tunnel.**
`up()`, `down()`, `restart()`, dan `refreshState()` memakai monitor object yang sama
(`@Synchronized`). Pasangan down+up **wajib** lewat `restart()`, yang membaca ulang niat
pengguna *di dalam kunci* setelah `down()` — sehingga pelaku lain yang memutus di tengah
operasi tidak diabaikan. `traffic()` sengaja **tidak** ikut dikunci: ia dipanggil berulang
dari `awaitHandshake` dan tiker UI, dan mengantre di belakang `up()` yang lambat (bisa
2 detik menunggu VpnService + sampai 10×1 detik retry DNS di backend) akan membuat
penantian handshake macet.

**3. Batas kunci itu dinyatakan eksplisit, tidak dilebihkan.**
Kunci hanya menjamin *transisi tunnel* tidak saling menyela. Ia **tidak** melindungi memo
niat (`Prefs.wasUp`) maupun hidup/matinya `ReconnectMonitor`, karena keduanya ditulis di
luar kunci. Untuk itu ada generasi niat lintas pelaku: `bumpIntent()` dipanggil oleh
setiap pelaku yang membawa niat baru (layar, ubin), `currentIntent` dibaca oleh pelaku
yang menegakkan niat yang sudah ada (`ReconnectMonitor`), dan `intentStale(gen)` diperiksa
tepat sebelum keadaan apa pun ditulis — termasuk setelah jeda panjang (proba endpoint
~6 detik, backoff sampai 60 detik).

**4. Notifikasi status milik `VelumTunnel.onStateChange`.**
Itu satu-satunya titik yang melihat setiap perubahan status siapa pun pemicunya.
`StatusNotifier.show/hide` tidak boleh dipanggil dari callback visual layar.

**5. Semua sentuhan UI lewat satu jalur (`VelumController.onUi{}`).**
Menjalankan segera bila pemanggil sudah di main thread, mengantre bila tidak. Sebelumnya
jalur ulangan uji menulis `TextView` dari `testWorker`; tidak crash hanya karena kebetulan
kedua view target berukuran tetap sehingga `View.checkForRelayout` mengambil jalur
`invalidate()` dan tidak memanggil `checkThread()`.

**6. Keputusan yang bisa salah dikeluarkan dari kelas Android menjadi fungsi murni.**
Contoh: `VelumEndpointChoice` (memilih endpoint pengganti dan memutuskan apakah
perpindahannya nyata) dipisah dari `EndpointProbe` (yang mengukur lewat soket). Karena
murni, ia teruji di JVM tanpa perangkat — satu-satunya cara menguji logika ini di repo
yang tidak punya emulator.

## Konsekuensi

**Yang membaik**

- Rotasi layar tidak lagi mereset durasi maupun memicu auto-uji ulang (`prevState` di
  controller diawali dari status tunnel yang sebenarnya).
- Notifikasi selalu mengikuti kenyataan, termasuk tunnel yang disambungkan lewat ubin atau
  boot tanpa Activity sama sekali.
- Pelaku yang lebih baru selalu menang; yang lebih tua berhenti tanpa menulis apa pun.
- Keputusan pemilihan endpoint punya uji regresi (9 kasus) yang berjalan di CI dalam
  hitungan detik.

**Yang harus dibayar**

- `restart()` membuat notifikasi hilang-muncul dan durasi kembali `00:00` setiap kali
  endpoint diputar. Itu konsekuensi jujur dari "durasi = umur tunnel ini", bukan "sejak
  pengguna menekan Sambungkan". Bila nanti dianggap mengganggu, yang diubah adalah arti
  `upSinceElapsedMs`, dan ADR ini harus direvisi.
- Layar hasil rotasi **tidak** menerima `onConnectedVisual()` karena tidak ada transisi.
  Semua yang dulu bergantung pada callback itu harus dipanggil eksplisit dari `onStart`
  (`startTicker`, `startPulse`, `resetTrafficBaseline`). Ini sudah menyebabkan satu regresi
  yang ditemukan saat audit ulang: laju trafik pertama dihitung terhadap uptime perangkat.
- `VelumTunnel.listener` adalah **satu slot**, bukan daftar. Aman selama hanya ada satu
  layar yang membuat controller dan Android menghancurkan Activity lama sebelum membuat
  yang baru. Bila kelak ada layar kedua yang butuh callback status, slot ini harus menjadi
  daftar — dan itu keputusan baru, bukan perluasan ADR ini.
- Model ini **tidak bisa diverifikasi otomatis**. Tidak ada emulator di CI, dan logika
  konkurensinya hidup di kelas yang bergantung Android. Bukti yang ada baru: kompilasi tiga
  varian (debug/preview R8/release bertanda tangan) hijau di run 34707253187, dan unit test
  untuk bagian yang murni. Perilakunya di perangkat adalah utang — lihat
  `docs/uji-perangkat.md` dan TODO 71.

**Yang ditolak**

- *Menulis `wasUp` di dalam `up()`/`down()`* — tampak lebih sederhana, tetapi
  menghancurkan `restart()`: `down()` akan menulis `wasUp = false`, lalu pemeriksaan niat
  di tengah `restart()` selalu menyimpulkan "pengguna memutus" dan tidak pernah menaikkan
  tunnel kembali.
- *Coroutine/`Mutex`* — melanggar §0 (tanpa dependensi baru) dan tidak menyelesaikan
  masalahnya: yang dibutuhkan adalah urutan niat lintas pelaku, bukan primitif sinkronisasi
  yang lebih modern.
- *Menambahkan `configChanges` di manifest agar layar tidak dibuat ulang* — menghilangkan
  gejala (1) dengan mengorbankan kebenaran: keadaan memang harus hidup di luar Activity,
  dan menyangkal pembuatan ulang membuat bug serupa muncul lagi di jalur lain (proses mati,
  split screen, dark mode).
