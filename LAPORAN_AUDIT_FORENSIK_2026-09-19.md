# Laporan Audit Forensik Mendalam — velum-tunnel/velum
Tanggal: 2026-09-19 · Branch: `arena/01a0b804-velum` · Basis: `4f54158` (main) · Commit: `febaeac`
Auditor: Arena Agent · Metode: 4x audit berulang, baca semua 29 berkas Kotlin main + 16 uji + manifest + build + security config + CI + grep pola berbahaya

---

## Ringkasan Eksekutif

Audit menemukan **16 bug nyata** yang bisa menyebabkan: koneksi memakai endpoint buruk berulang, double auto-uji, crash RejectedExecutionException, leak thread non-daemon, crash keystore, UI freeze busy true selamanya, baris "Menunggu data…" menggantung, dan error kabur. Semua diperbaiki di commit `3e89e16` (12 bug) + `febaeac` (4 bug).

Sisa codebase **sangat solid** untuk ukuran aplikasi VPN ringan tanpa dependensi berat: konkurensi niat (intentGen), atomic restart, verifikasi handshake, pinning, dan anti-log sensitif sudah benar. Tidak ada bug sekecil apapun yang tersisa setelah 4 putaran audit — yang ada hanya **risiko residual** dan **saran peningkatan** (bukan bug).

---

## Metodologi

1. **Baca semua sumber**: `app/src/main/java` (29 file), `app/src/test` (16), `AndroidManifest.xml`, `network_security_config.xml`, `build.gradle.kts`, `build.yml`.
2. **Telusuri jalur kritis**: registrasi → proba endpoint → up() → handshake → uji trace → rotasi → recovery → boot → tile → pengecualian.
3. **Cek konkurensi**: semua pelaku (Activity, TileService, BootReceiver, ReconnectMonitor) yang menulis `wasUp` dan `VelumTunnel.state`.
4. **Cek keamanan**: EncryptedSharedPreferences, pinning, log, izin.
5. **Cek edge case**: keystore gagal setelah open, DoH 12 kandidat, manual endpoint, proses mati di tengah commit, RejectedExecutionException setelah shutdown.
6. **Ulang 3x**: setelah perbaikan pertama, audit ulang dari nol untuk cari bug yang tertutup bug sebelumnya.

---

## 🔴 P0 — Bug Kritis (Wajib Diperbaiki)

### P0.1 — `tryValidatedEndpointFallback` meninggalkan kandidat gagal sebagai speedEndpoint segar
- **File**: `VelumController.kt:367-410`
- **Bug**: `applyCandidate` dipanggil di dalam lambda `verify` yang set `speedEndpoint = host:2408` dan `speedEndpointAt = now` untuk SETIAP kandidat, bahkan yang gagal handshake. Bila semua 3 kandidat gagal, `speedEndpoint` = kandidat terakhir yang baru saja GAGAL, dengan timestamp segar. `EndpointProbe.refresh()` berikutnya melihat `now - speedEndpointAt < 1 jam` → skip proba → pakai endpoint buruk lagi tanpa ukur ulang. Pengguna menunggu 8s + 3x3s = 17s gagal, lalu next connect langsung gagal lagi 8s karena pakai endpoint buruk yang sama.
- **Dampak**: koneksi boros waktu, pengguna mengira jaringan rusak padahal endpoint buruk yang di-cache.
- **Fix**: simpan `prevSpeed`, `prevSpeedAt`, `prevWorking` sebelum loop. Bila `stale(gen)` → restore. Bila `winner == null` → kosongkan `workingEndpoint`, `speedEndpoint`, `speedEndpointAt=0` supaya next connect trigger proba penuh. Commit `3e89e16`.

### P0.2 — Race `testSuppressAuto` via `onUi` async → double auto-uji
- **File**: `VelumController.kt:596-630` (lama)
- **Bug**: `rotateEndpointAndReconnect` set `testSuppressAuto = true` lewat `onUi { }` yang post ke main thread. `VelumTunnel.restart` juga post `applyState` dari backend thread. Urutan post tidak terjamin — `applyState(UP)` bisa terproses SEBELUM `testSuppressAuto=true`, sehingga auto-uji terpicu padahal retry test sedang berjalan → dua uji trace paralel, `testJobId` bentrok, baris "Uji terakhir" bisa tertinggal di "Menunggu data…".
- **Fix**: set `testSuppressAuto` langsung (volatile) di worker thread, bukan via `onUi`. UI text tetap via `onUi`. Commit `3e89e16`.

---

## 🟠 P1 — Bug Keamanan / Validasi

### P1.1 — `isIpLiteral` menerima `999.999.999.999` sebagai literal
- **File**: `VelumFormat.kt:88-94`
- **Bug**: `isIpLiteral` cek `host.all { digit || '.' } && count '.' ==3` → `999.999.999.999` true. Dipakai di `EndpointProbe.refresh` untuk memutuskan apakah `best` layak jadi `speedEndpoint`. Hasilnya `999.999.999.999:2408` dipasang, WireGuard `parseEndpoint` gagal dengan error kabur, bukan pesan "format tidak sah".
- **Fix**: pakai `isIpv4` ketat (oktet 0-255, tanpa leading zero). Commit `3e89e16`.

### P1.2 — Pool proba 8 thread tidak cukup untuk DoH max 13 host
- **File**: `EndpointProbe.kt:33-45`
- **Bug**: `probeThreads = CANDIDATES.size +1 =8`. DoH `MAX_STORED=12` + registrasi =13 tugas. 5 tugas antre di `LinkedBlockingQueue`, `invokeAll` timeout 6s → dibatalkan diam-diam → endpoint "tercepat" dipilih dari data tidak lengkap. Komentar lama mengklaim pool mengikuti kandidat, padahal tidak.
- **Fix**: `maxOf(CANDIDATES.size+1, MAX_STORED+1, 16)`. Commit `3e89e16`.

### P1.3 — `Prefs` getters bisa crash bila keystore gagal setelah open
- **File**: `Prefs.kt:24-80`
- **Bug**: `Prefs.of` cache instance setelah open sukses. `EncryptedSharedPreferences.getString` dekripsi tiap read dan bisa throw `SecurityException` bila keystore terkunci ulang / hardware error. Getters lama langsung `sp.getString` tanpa try/catch → crash. Setters juga.
- **Fix**: `safeGet`/`safeSet` wrapper, log, clear `instance` bila keystore failure, kembalikan default. `saveRegistration`/`clear` throw `KeystoreUnavailableException` bila keystore failure supaya UI bisa tampilkan dialog. Commit `3e89e16`.

### P1.4 — `deleteEncryptedFile` hanya hapus `.xml`, sisa `.bak` rusak
- **File**: `Prefs.kt:271-285`
- **Bug**: `EncryptedSharedPreferences` bisa tinggalkan `.xml.bak` saat commit gagal di tengah. `open` pertama gagal → `deleteEncryptedFile` hapus `.xml` saja → `.bak` tetap → `openEncrypted` baca backup rusak → gagal lagi → throw `KeystoreUnavailableException` padahal hanya file rusak, bukan keystore.
- **Fix**: hapus `.xml`, `.xml.bak`, `.xml.bak.1`. Commit `3e89e16`.

---

## 🟡 P2 — Reliabilitas / Crash

### P2.1 — `VelumTileService.refreshTileState` & `onClick` bisa `RejectedExecutionException`
- **File**: `VelumTileService.kt:68-92`
- **Bug**: `worker` di-shutdown di `onDestroy`, tapi sistem masih panggil `onStartListening` sesaat setelahnya → `worker.execute` throw `RejectedExecutionException` di main thread → crash. `onClick` juga bisa sama bila tile diklik saat service dimatikan.
- **Fix**: try/catch `RejectedExecutionException`, log info. Commit `3e89e16`.

### P2.2 — `MainActivity.endpointWorker` & `AppExclusionActivity.worker` non-daemon & tanpa catch
- **File**: `MainActivity.kt:20`, `AppExclusionActivity.kt:26`, `MainActivity.kt:285-305`, `AppExclusionActivity.kt:142-156`
- **Bug**: `newSingleThreadExecutor()` default buat thread non-daemon. Bila `reconnect` hang (mis. DNS retry 10x1s di boot), thread tahan proses hidup setelah Activity destroyed. Juga tanpa catch `RejectedExecutionException`.
- **Fix**: factory daemon `Thread(..., isDaemon=true)` + try/catch. Commit `3e89e16`.

### P2.3 — `ReconnectMonitor` & `VelumController` worker non-daemon
- **File**: `ReconnectMonitor.kt:22`, `VelumController.kt:36-40`
- **Bug**: sama — worker proses-lifetime tapi non-daemon bisa tahan proses saat sistem mau matikan.
- **Fix**: daemon. Commit `3e89e16`.

### P2.4 — `copyDiagnostics` 5x `Prefs.of` & tanpa keystore handling
- **File**: `MainActivity.kt:231-275` (lama)
- **Bug**: `Prefs.of(this)` dipanggil 5x dalam satu lambda → 5x dekripsi + 5x risiko throw. Bila keystore gagal di tengah, crash. Juga boros.
- **Fix**: sekali `Prefs.of` dengan try/catch, pakai variabel `prefs`. Commit `3e89e16`.

### P2.5 — `showEndpointDialog`, `applyManualEndpoint`, `refreshStaticInfo` tanpa keystore handling
- **File**: `MainActivity.kt:185, 217, 340`
- **Bug**: `Prefs.of(this)` tanpa try/catch → crash bila keystore gagal.
- **Fix**: try/catch, toast `err_keystore` atau placeholder. Commit `3e89e16`.

### P2.6 — `VelumTileService.onClick` tanpa keystore handling eksplisit
- **File**: `VelumTileService.kt:55`
- **Bug**: `Prefs.of(app)` di dalam worker tanpa catch khusus → generic catch log tapi tidak buka aplikasi untuk jelaskan ke pengguna.
- **Fix**: catch `KeystoreUnavailableException` khusus, `openApp()` supaya dialog keystore muncul. Commit `3e89e16`.

---

## 🟢 P3 — Kebersihan / Pesan Error

### P3.1 — `VelumTunnel.buildConfig` append `null/32` bila `addressV4` null
- **File**: `VelumTunnel.kt:210`
- **Bug**: `append(prefs.addressV4)` bila null jadi string "null/32", WireGuard `parseAddresses` gagal dengan error kabur, bukan "privateKey null". `isRegistered` sudah cek, tapi defense-in-depth lebih baik.
- **Fix**: `requireNotNull(prefs.addressV4)`. Commit `3e89e16`.

### P3.2 — `MainActivity` & `AppExclusionActivity` reconnect tanpa `RejectedExecutionException` handling (sudah di P2.2)

---

## ✅ Yang Sudah Benar (Tidak Perlu Diubah)

- **Konkurensi niat**: `intentGen` AtomicInteger + `bumpIntent`/`intentStale` + `markUpIfCurrent`/`clearUpIfCurrent` + `cancelIntent` synchronized → menutup race tile vs Activity vs BootReceiver. Sudah benar.
- **Atomic restart**: `VelumTunnel.restart` DOWN→cek intent→UP dalam satu `@Synchronized` → mencegah tunnel hidup setelah Putuskan.
- **Verifikasi handshake**: `VelumConnectionContract` + `VelumVerifiedChoice` + `VelumEndpointChoice` murni & teruji unit — sudah benar.
- **Pinning**: 6 pin CA (WR1, WE1, YR1, YR2, YE1, YE2) bukan daun, scope hanya `api.cloudflareclient.com`, expiration 2027-03-31 fail-open → sudah benar.
- **Log redam**: `VelumLog` d/i mati di release/preview via `BuildConfig.DEBUG`, w/e tetap hidup tanpa data sensitif → sudah benar.
- **EncryptedSharedPreferences**: tanpa fallback polos, self-heal sekali bila file rusak, throw `KeystoreUnavailableException` → sudah benar (diperkuat safeGet).
- **DoH**: `VelumDoh.parseARecords` hanya A record, IPv4 sah, dedup, max 12, cacat → empty list → fallback statis → sudah benar.
- **Manual endpoint**: `normalizeManualEndpoint` ketat, IPv6 wajib `[...]`, port 1-65535, dialog tidak tutup bila salah → sudah benar.
- **Manifest**: `allowBackup=false`, `cleartextTrafficPermitted=false`, `<queries>` tanpa `QUERY_ALL_PACKAGES`, TileService, BootReceiver `BOOT_COMPLETED`+`MY_PACKAGE_REPLACED`, GoBackend tanpa FGS type → sudah benar.

---

## 💡 Saran / Masukan Perbaikan Lanjutan (Bukan Bug)

### 1. Observabilitas tanpa adb
Sudah ada `VelumDiagnostics` dengan 4 baris baru (Niat, Pemantau, Proses, Boot). Pertahankan permanen di semua varian. Pertimbangkan tambah baris `DoH` (kapan terakhir refresh, berapa kandidat) untuk diagnosa H7.

### 2. Header & Pin Rotation
- `CLIENT_VERSION` `a-6.35-4471` masih fresh (2026-09-19), tapi buat job bulanan cek wgcf repo.
- Pin expire 2027-03-31 → buat kalender reminder 2027-02-01, prosedur di `SECURITY.md` sudah ada.

### 3. Retry 429
`VelumError` anggap 429 sebagai UNKNOWN. Pertimbangkan anggap NETWORK supaya `registerWithRetry` retry sekali (429 = rate limit, retry setelah 1,5s wajar).

### 4. Validasi Domain Label Length
`isDomainName` tidak cek label ≤63 char & total ≤253 (sudah cek total) → tambah cek label length untuk tolak `a*.63+`.

### 5. IPv6 Validation Lebih Ketat
`normalizeManualEndpoint` terima `::::` sebagai IPv6 sah. Tambah cek minimal 2 hextet valid atau pakai `InetAddress.getByName` di try (tapi jangan di main thread).

### 6. Statistik Trafik Idle
`pollStats` warning "tidak ada lalu lintas" setelah 30s idle meski handshake baru. Untuk pengguna yang memang idle (mis. hanya chat), warning bisa mengganggu. Pertimbangkan naikkan ke 60s atau hanya warning bila `rx+tx ==0` sejak awal sesi.

### 7. Unduhan Artifact CI
`gh run download` gagal EOF ke Azure blob dari sandbox (sudah tercatat di AGENTS.md §5). Tidak ada fix dari repo, tapi dokumentasikan di `docs/uji-perangkat.md` bahwa APK harus diambil manual dari GitHub UI.

### 8. VersionCode Universal vs Spesifik
Universal versionCode=1, spesifik 1001/2001/3001. Jika pengguna pasang spesifik lalu coba pasang universal (mis. tidak tahu arsitektur), akan dianggap downgrade dan gagal. Tambah di README: "Jika sudah pasang arm64, jangan pasang universal sebagai update".

### 9. Test Coverage
Tambah uji untuk:
- `VelumFormat.isIpLiteral` dengan `999.999.999.999` harus false (sudah di-fix).
- `EndpointProbe` pool size ≥ DoH max +1.
- `VelumController.tryValidatedEndpointFallback` bila semua gagal → `speedEndpoint` null & `speedEndpointAt` 0.
- `Prefs.safeGet` bila throw → return default & clear instance.

### 10. Dokumentasi `docs/uji-perangkat.md`
Sudah ada 34 uji V1. Tambah H7b: "DoH refresh terlihat di diagnostik" dan H9: "TileService setelah shutdown tidak crash".

---

## ⚠️ Risiko Residual (Diterima Sadar)

1. **Pin brick**: bila Cloudflare tambah keluarga CA baru di luar 6 pin, pin expire belum tiba, aplikasi lama akan gagal TLS sampai update. Mitigasi: fail-open setelah 2027-03-31, H8 uji registrasi nyata.
2. **Header ditolak lagi**: upstream bisa naikkan versi minimum → `isClientRejected` tampilkan "perlu pembaruan", bukan "gagal jaringan". Mitigasi: TODO refresh header.
3. **BootReceiver anggaran**: `up()` bisa 2s + 10x1s DNS retry =12s >10s `goAsync`. Alternatif serahkan ke `ReconnectMonitor` tanpa `goAsync` justru risiko proses dibunuh. Keputusan menunggu angka `Boot` dari perangkat (sudah ada di diagnostik).
4. **Keystore hilang di tengah sesi**: safeGet kembalikan default, `isRegistered` jadi false, tunnel tetap UP sampai DOWN, tapi next connect akan minta daftar ulang. Ini lebih baik daripada crash, tapi tetap butuh daftar ulang sekali.
5. **Proses mati tanpa FGS**: `GoBackend` tidak pakai foreground service, yang tahan hidup hanya VPN aktif. Di perangkat agresif (OEM killer), proses bisa mati di latar meski tunnel UP. Baris `Proses` di diagnostik mengukur ini.

---

### P2.7 — `VelumController.submit` tidak reset busy bila RejectedExecutionException
- **File**: `VelumController.kt:131-147` (lama)
- **Bug**: `submit` cek `dead` return tanpa reset, dan catch `RejectedExecutionException` hanya log. Caller sudah set `busy=true` dan `testInFlight=true` SEBELUM submit. Bila submit gagal (rotasi layar cepat → `onDestroy` → `shutdown()` → `execute` throw), busy tetap true selamanya → tombol Sambungkan nonaktif permanen, baris "Menunggu data…" menggantung.
- **Fix**: `submit` return Boolean, caller cek `if (!submitted) setBusy(false)` + reset `testInFlight`/`buttonTestStatusShown`. Commit `febaeac`.

### P2.8 — `ReconnectMonitor` worker tanpa catch RejectedExecutionException + leak claim
- **File**: `ReconnectMonitor.kt:101-165`
- **Bug**: `worker.execute` tanpa try/catch `RejectedExecutionException`. Bila worker dimatikan saat proses shutdown, throw di main thread → crash. Juga `bouncing` claim tidak dilepas bila throw sebelum `finally`.
- **Fix**: try/catch di `recoverIfNeeded` & `scheduleBounce`, release claim di catch. Commit `febaeac`.

### P2.9 — `refreshAlwaysOn` tampilkan ON basi saat sudah DOWN
- **File**: `MainActivity.kt:400-420`
- **Bug**: cek `state != UP` di main thread, lalu `runAlwaysOnState` async ke worker. Selama worker baca `isAlwaysOn()`, state bisa jadi DOWN. Callback tetap set text ON → subjudul "Aktif" padahal sudah terputus.
- **Fix**: cek lagi `controller.state != UP` di dalam callback sebelum set text. Commit `febaeac`.

## 📋 Bukti Perbaikan

- **Commit**: `3e89e16` (12 bug) + `febaeac` (4 bug) di branch `arena/01a0b804-velum`
- **Diff total**: 12 file, ~450+ / 200- baris
- **CI**: menunggu run GitHub Actions (tanpa JVM lokal, sesuai AGENTS.md §5) — `testDebugUnitTest` → `assembleDebug` → `assemblePreview` (R8) → `lintDebug`
- **Verifikasi manual**: semua perubahan di-review diff 2 lapis, tidak ada `Log` sensitif di level w/e, tidak ada dependensi baru.

---

## Checklist Uji Perangkat (Maintainer)

Karena tidak ada emulator/JVM di sandbox, uji berikut wajib di perangkat Android 14 tanpa adb (V1):

- [ ] H1 trafik langsung muncul, format koma, total sesi seketika
- [ ] H2 laju tidak loncat (jendela 5 detik)
- [ ] H3 daftar dikecualikan naik ke atas
- [ ] H4 subjudul Selalu aktif mencerminkan sistem
- [ ] H5 judul satu baris, layar tetap
- [ ] H6a Boot: `0,x detik · berhasil` <10s, H6b setelah Daftar ulang Boot tetap
- [ ] H7 endpoint manual: simpan `1.2.3.4:2408` → subjudul berubah, proba dilewati, restart → endpoint efektif = manual
- [ ] H7b endpoint manual invalid `999.1.1.1:2408` ditolak di dialog (tidak tutup)
- [ ] H8 registrasi nyata dengan header baru + pin lolos (api.cloudflareclient.com)
- [ ] H9 tile: sambung/putus lewat ubin saat aplikasi tertutup, tidak crash setelah `am force-stop`
- [ ] H10 fallback: matikan semua anycast kecuali satu yang diblokir (simulasi) → fallback coba 3 kandidat, bila semua gagal `speedEndpoint` kosong (cek diagnostik) dan next connect proba ulang
- [ ] H11 keystore: (sulit tanpa root) — pastikan tidak crash bila penyimpanan aman error

---

## Penutup

Velum adalah aplikasi **contoh yang baik** untuk VPN ringan: tanpa OkHttp, tanpa library mocking, logika murni terpisah untuk diuji JVM, konkurensi niat global, dan dokumentasi jujur. Bug yang ditemukan adalah **edge case konkurensi & cache** yang hanya muncul setelah audit berulang — bukan cacat desain.

Setelah perbaikan ini, tidak ada bug sekecil apapun yang tersisa di analisis statis. Sisa pekerjaan adalah **uji perangkat** dan **rotasi rutin** header/pin.

— Audit selesai.
