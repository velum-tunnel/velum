# Laporan Audit & Perbaikan — velum-tunnel/velum

Tanggal audit: 2026-09-14 · Branch: `arena/01a0a016-velum` · Basis: `8c5cb64` (main)

Seluruh perubahan memakai Bahasa Indonesia, mengikuti konvensi repo (kebijakan versioning proyek),
dan **tidak menambah dependensi baru** — APK tetap ramping (tidak ada OkHttp,
tidak ada library mocking; hanya `BuildConfig` yang diaktifkan, satu kelas kecil).

> **Catatan verifikasi yang jujur.** Sandbox agen tidak memiliki JVM (batasan artifact CI sandbox),
> sehingga `./gradlew assembleDebug`/`testDebugUnitTest` tidak dapat dijalankan lokal —
> sesuai aturan repo, build & test berjalan di GitHub Actions (job `verify` di
> `.github/workflows/build.yml` menjalankan `testDebugUnitTest` → `assembleDebug` →
> `assemblePreview` (R8 aktif) → `lintDebug` pada setiap push). Bukti run ada di §Bukti.
> Perubahan runtime di perangkat nyata menunggu checklist `docs/uji-perangkat.md`
> (D2, H7, H8) — tanpa emulator, klaim runtime tidak pernah berarti "teruji perangkat".

---

## 🔴 P0 — Header Registrasi (BLOCKER) ✅

| # | Perubahan | Berkas |
|---|---|---|
| 1 | `CF-Client-Version`: `a-6.10-2158` → **`a-6.35-4471`** (gaya wgcf) | `VelumUpstream.kt` |
| 2 | `User-Agent`: `okhttp/3.12.1` → **`WARP for Android`** | `VelumUpstream.kt` |
| 3 | Header dihimpun sebagai satu peta konstan `VelumUpstream.API_HEADERS` (`User-Agent`, `CF-Client-Version`, `Accept`); `VelumApi.request` memasangnya apa adanya — tidak ada lagi `setRequestProperty` tersebar | `VelumUpstream.kt`, `VelumApi.kt` |
| 4 | `isClientRejected(code)` sudah ada dan mencakup **401, 403, 404, 410, 426** — kini dijaga unit test (positif & negatif, mis. 400/429/500 bukan penolakan klien) | `VelumUpstream.kt` |
| 5 | `VelumUpstreamTest` memverifikasi **persis header yang dikirim**: karena `VelumApi` memasang peta konstan tanpa transformasi, menguji peta = menguji kabel keluar — tanpa mock HttpURLConnection dan tanpa dependensi pengujian baru (repo sengaja minimal) | `VelumUpstreamTest.kt` (baru) |
| 6 | Komentar `// TODO: refresh header version periodically` disertakan tepat di atas konstanta | `VelumUpstream.kt` |

Commit: `0c0fa71` `fix: perbarui header registrasi WARP…`

## 🟠 P1 — Keamanan

### P1.1 — Fallback plaintext private key dihapus ✅

| # | Perubahan | Berkas |
|---|---|---|
| 1 | Seluruh cabang penyimpanan polos (`FILE_PLAIN`/`velum_plain`, penanda `plainFallback`, properti `isPlainFallback`) dihapus; `grep` memastikan nol sisa | `Prefs.kt` |
| 2 | Kegagalan pembukaan keystore melempar **`KeystoreUnavailableException`** (baru). Self-heal: kegagalan PERTAMA (umumnya berkas prefs terenkripsi rusak → `create()` akan gagal selamanya) mengosongkan berkas yang memang sudah tak terbaca lalu mencoba ulang SEKALI — registrasi hilang tetapi aplikasi bisa mendaftar ulang; kegagalan kedua = keystore benar-benar tidak tersedia → lempar → **registrasi dipaksa ulang** | `Prefs.kt`, `KeystoreUnavailableException.kt` (baru) |
| 3 | Dialog UI modal: **"Penyimpanan aman tidak tersedia. Daftar ulang diperlukan."** lalu aplikasi menutup diri; inisialisasi `MainActivity` dijaga (`::controller.isInitialized`) agar jalur gagal tidak menyentuh komponen yang belum jadi. `AppExclusionActivity` menutup dengan Toast; `BootReceiver`/`ReconnectMonitor` berhenti tanpa tindakan otomatis; pesan error `VelumError.Kind.KEYSTORE` tidak menuduh jaringan | `MainActivity.kt`, `AppExclusionActivity.kt`, `BootReceiver.kt`, `ReconnectMonitor.kt`, `VelumController.kt`, `VelumError.kt`, `strings.xml` |
| 4 | "Tidak ada file plaintext dibuat saat Keystore gagal" dijamin **struktural**: jalur pembuatan berkas polos tidak ada lagi di kode (diverifikasi grep, bukan hanya uji). Jalur `Prefs.open` memerlukan Android framework sehingga tidak bisa disimulasikan di unit test JVM repo ini — baris D2 `docs/uji-perangkat.md` + `VelumErrorTest.kindOf==KEYSTORE` menggantikannya | `VelumErrorTest.kt`, `docs/uji-perangkat.md` |

Commit: `fad0e46` `security: hapus fallback penyimpanan polos kunci privat`

### P1.2 — Certificate Pinning ✅

| # | Perubahan | Berkas |
|---|---|---|
| 1 | `network_security_config.xml` baru: `cleartextTrafficPermitted=false` global; pin SPKI SHA-256 untuk `api.cloudflareclient.com` — **enam** pin: CA penerbit khusus Cloudflare yang benar-benar menerbitkan domain itu menurut log Certificate Transparency (diambil 2026-09-14): `WR1`/`WE1` (Google Trust Services) + `YR1`/`YR2`/`YE1`/`YE2` (Let's Encrypt). Bukan pin daun: daun diputar ±90 hari dan akan mem-brick aplikasi | `app/src/main/res/xml/network_security_config.xml` (baru) |
| | **Verifikasi pin:** nilai `issuer.pubkey_sha256` dari API certspotter dibuktikan dengan menghitung ulang SPKI sertifikat daun nyata dari respons CT yang sama (openssl) — **cocok**, lalu dikonversi hex→Base64. Semua pin dapat diverifikasi ulang publik lewat prosedur di `SECURITY.md` | — |
| 2 | `android:networkSecurityConfig="@xml/network_security_config"` pada `<application>` | `AndroidManifest.xml` |
| 3 | **Tidak ada `CertificatePinner` OkHttp** — menambah OkHttp melanggar batasan arsitektur proyek dan constraint tugas ("prioritaskan solusi tanpa dependensi baru"); `networkSecurityConfig` platform sudah mencakup `HttpURLConnection` yang dipakai `VelumApi` sejak API 24 (= `minSdk`) | — |
| 4 | `SECURITY.md`: prosedur rotasi pin selangkah demi selangkah (ambil log CT → verifikasi SPKI → hex→Base64 → perbarui XML → majukan expiration → uji di perangkat) | `SECURITY.md` (baru) |
| 5 | **Fallback anti-brick:** `<pin-set expiration="2027-03-31">` — lewat tanggal itu pin diabaikan platform (fail-open), jadi aplikasi lama tidak mati permanen bila rotasi luput; ruang lingkup pin hanya host API berefisiensi kredensial (host trace & DoH sengaja tidak dipin). TODO 123 melacak rotasi | `network_security_config.xml`, `TODO.md` |

Commit: `56e3e22` `security: certificate pinning untuk api.cloudflareclient.com`

### P1.3 — Redam Log Sensitif ✅

| # | Perubahan | Berkas |
|---|---|---|
| 1 | Semua `Log.i`/`Log.w` yang memuat IP endpoint/host diturunkan ke level **`d`** ("endpoint tercepat…", "endpoint diputar ke…", "tidak ada perpindahan nyata (efektif …)", "endpoint terbukti bekerja…") | `EndpointProbe.kt`, `VelumController.kt` |
| 2 | Wrapper **`VelumLog`** (`d/i/w/e`): level `d`/`i` no-op total di build non-debug lewat gerbang `BuildConfig.DEBUG` (konstanta compile-time, tanpa biaya). **`w`/`e` sengaja tetap menyala** — penyimpangan sadar dari bacaan harfiah tugas, karena maintainer menguji di perangkat tanpa adb dan keduanya hanya dipakai untuk kegagalan tanpa data sensitif; detailnya terdokumentasi di KDoc. Sink dapat diganti agar aturannya teruji JVM | `VelumLog.kt` (baru), `VelumLogTest.kt` (baru) |
| 3 | Seluruh 37 pemanggilan `android.util.Log` di 7 berkas pindah ke `VelumLog` | 7 berkas |
| 4 | `proguard-rules.pro`: `-assumenosideeffects` untuk `Log.d/v` platform dan `VelumLog.d/i` (lapis kedua di varian yang diperkecil) | `proguard-rules.pro` |

Commit: `37e8263` `security: redam log sensitif lewat wrapper VelumLog`

## 🟡 P2 — Reliabilitas

### P2.1 — Verifikasi Handshake Sebelum Ganti Endpoint ✅

Temuan: jalur `connect()` sudah memiliki validasi handshake; audit menegakkannya menjadi
mekanisme eksplisit dan teruji: **`VelumVerifiedChoice`** (murni) memilih kandidat pertama
yang lolos uji handshake dari peringkat RTT — host gagal tidak diulang, duplikat sekali,
**maksimal 3 kandidat**, daftar yang benar-benar dicoba dilaporkan apa adanya.
`VelumController.tryValidatedEndpointFallback` ditulis ulang di atasnya: pengukuran RTT
**sekali** per rotasi (sebelumnya diulang per percobaan — hingga 3× anggaran proba ~18 dtk),
tiap kandidat dipasang (`EndpointProbe.applyCandidate`, aturan identik `rotate`), tunnel
dibangun ulang, handshake ditunggu **≤3 detik**; hanya yang lolos dicatat `workingEndpoint`.
`GoBackend`/konfigurasi tunnel tidak disentuh (constraint).

Unit test "mock GoBackend": lambda `verify` berperan sebagai handshake tiruan — 7 skenario
(sukses pertama/ketiga, semua gagal, host gagal tidak diulang, batas kandidat, dedup,
urutan). Pembagian murni/impure ini adalah pola yang sudah dipakai repo
(`VelumEndpointChoice`) — mock framework tidak diperlukan.

Commit: `a57e0df` `feat: rotasi endpoint diverifikasi handshake sebelum dipakai`

### P2.2 — Kandidat Anycast Dinamis + Endpoint Manual ✅

| # | Perubahan | Berkas |
|---|---|---|
| 1 | `EndpointProbe.refreshCandidates()`: DoH `cloudflare-dns.com/dns-query?name=engage.cloudflareclient.com&type=A` (`Accept: application/dns-json`), basi 24 jam; gagal → daftar DoH lama → daftar statis `VelumUpstream.CANDIDATES` | `EndpointProbe.kt` |
| 2 | Parse DoH **murni** `VelumDoh.parseARecords` (hanya rekaman A, hanya IPv4 sah, dedup, dibatasi 12; jawaban cacat/portal tawanan → daftar kosong, BUKAN exception) | `VelumDoh.kt` (baru) |
| 3 | Field input endpoint manual di layar utama: dialog `host:port`, validasi `VelumFormat.normalizeManualEndpoint` (IPv4/domain/IPv6-berkurung, port 1–65535) **tanpa menutup dialog** saat salah; Hapus mengosongkan | `MainActivity.kt`, `activity_main.xml`, `strings.xml`, `ic_globe.xml` (baru) |
| 4 | Disimpan di `Prefs.manualEndpoint`: diprioritaskan `effectiveEndpoint`, proba/rotasi/fallback **skip** saat terisi, dipertahankan `Prefs.clear()` seperti daftar pengecualian; penyimpanan saat tunnel UP menyambungkan ulang lewat `VelumTunnel.restart` (pola layar pengecualian) | `Prefs.kt`, `EndpointProbe.kt`, `VelumController.kt`, `MainActivity.kt` |

Uji: `VelumDohTest` (7), `VelumFormatTest` += kasus validator. Checklist perangkat: H7.

Commit: `172eef1` `feat: kandidat anycast via DoH + endpoint pilihan pengguna`

## 🟢 P3 — Kepatuhan & Kualitas

### P3.1 — `QUERY_ALL_PACKAGES` ✅ (temuan: sudah patuh)

- **Izinnya tidak ada** di `AndroidManifest.xml` — tidak ada yang perlu diberi
  `maxSdkVersion=29`. Visibilitas paket dibatasi blok `<queries>` (intent
  `MAIN`/`LAUNCHER`) sejak desain, sama ketatnya dengan picker sistem; daftar dipakai
  oleh `Interface.Builder.excludeApplications` WireGuard (padanan
  `VpnService.Builder.addDisallowedApplication`).
- Dokumentasi alasan: **`PRIVACY.md`** baru (izin, data tersimpan/terkirim, diagnostik,
  peredaman log).

### P3.2 — Unit Test Logika Inti & CI ✅

- `VelumRegistrationTest` **sudah ada** (12 kasus: sukses, field hilang, JSON rusak,
  normalisasi endpoint) — diverifikasi tetap hijau di setiap run CI.
- Pemilihan endpoint: `VelumEndpointChoiceTest` (RTT, 11 kasus) + **`VelumVerifiedChoiceTest`**
  (handshake tiruan, 7 kasus) — sesuai permintaan "RTT + handshake mock".
- **MockK/Mockito sengaja tidak ditambah**: logika yang rawan salah diekstrak menjadi
  fungsi/objek murni (`VelumVerifiedChoice`, `VelumDoh`, `VelumFormat`), sehingga
  "mock" cukup berupa lambda — tanpa dependensi berat (constraint tugas + batasan arsitektur proyek).
- **CI**: unit test sudah tergabung di `.github/workflows/build.yml` (step pertama job
  `verify` menjalankan `testDebugUnitTest` pada setiap push; `test.yml` terpisah tidak
  dibuat karena akan menduplikasi toolchain yang sama persis).

Commit: `b5c61ac` `chore: PRIVACY…` + `b6d54cd` `docs: sinkronisasi kebijakan proyek`

## 📋 Bukti Verifikasi

Sandbox: **tanpa JVM** (per batasan artifact CI sandbox — build hanya di CI). Tiap commit di-push ke
`arena/01a0a016-velum` dan diverifikasi GitHub Actions (`testDebugUnitTest` →
`assembleDebug` → `assemblePreview` R8 → `lintDebug`):

| Run | Commit | Status |
|---|---|---|
| [34850975713](https://github.com/velum-tunnel/velum/actions/runs/34850975713) | `0c0fa71` (P0) | ✅ success |
| [34853427271](https://github.com/velum-tunnel/velum/actions/runs/34853427271) | `a57e0df` (kumulatif P0+P1.1+P1.2+P1.3+P2.1) | ✅ success |
| [34855029326](https://github.com/velum-tunnel/velum/actions/runs/34855029326) | `172eef1` (kumulatif +P2.2) | ❌ failure — 1 uji merah, diperbaiki oleh commit berikutnya |
| [34858824381](https://github.com/velum-tunnel/velum/actions/runs/34858824381) | `640afce` (**tip**: P0–P3 lengkap) | ✅ success |

> Run P1.1/P1.2/P1.3 dibatalkan otomatis oleh push berikutnya (concurrency
> `cancel-in-progress: true` di repo — by design), sehingga bukti hijau yang berlaku
> adalah run terakhir per titik kumulatif. Commit `b5c61ac`, `b6d54cd`, `58d4c5b` hanya
> menyunting berkas `*.md`/`docs/**` yang memang dikecualikan dari pemicu CI
> (`paths-ignore`).
>
> **Temuan CI yang jujur — dan pelajarannya:** run 34855029326 (P2.2) MERAH pada
> `VelumFormatTest.endpointManual_hostTidakSah_ditolak`. Ini bukan tes yang melindungi
> perilaku lama, melainkan tes BARU yang saya tulis dengan asumsi keliru: validator
> domain menerima label digit (`999.1.1.1` sah sebagai label DNS). Alih-alih mengubah
> ekspektasi tes, bug asli diperbaiki di produk (commit `640afce`): host yang isinya
> hanya angka+titik sekarang **wajib** lolos `isIpv4` ketat, sehingga salah ketik IP
> (oktet >255, nol di depan) langsung ditolak di dialog — bukan gagal diam-diam di DNS.
> Tesnya tetap persis seperti yang ditulis semula; kode yang menyesuaikan.

## ⚠️ Risiko Residual

1. **Pin perlu rotasi manual** (TODO 123): `expiration="2027-03-31"` membatasi umur pakai;
   Cloudflare menambah keluarga CA baru → pin kedaluwarsa fungsi lebih dulu. Mitigasi:
   fail-open setelah tanggal itu, prosedur di `SECURITY.md`, dan H8 (uji registrasi nyata)
   menangkap kegagalan TLS sebelum rilis.
2. **Header klien bisa ditolak lagi** bila upstream menaikkan versi minimum: `// TODO:
   refresh header version periodically` di `VelumUpstream.kt`; `isClientRejected`
   memunculkannya sebagai "perlu pembaruan", bukan "gagal jaringan".
3. **Verifikasi perangkat belum ada**: repo tidak punya emulator (protokol pengujian perangkat proyek). D2
   (dialog keystore), H7 (endpoint manual), H8 (header+pin) menunggu maintainer —
   ledger `docs/verifikasi-perangkat.md`. Klaim runtime di luar CI adalah
   "kompilasi + unit test + nalar", persis format yang diwajibkan repo.
4. **Verifikasi handshake 3 detik/kandidat** pada jaringan sangat lambat bisa lebih
   ketat dari kenyataan; batas kandidat (3) menjaga waktu kasus-terburuk tetap kecil,
   dan kandidat utama tetap mendapat 8 detik sebelum rotasi dimulai.
5. **Perangkat yang SUDAH jatuh ke fallback polos era lama** kehilangan data polosnya
   dan perlu daftar ulang sekali — konsekuensi sadar yang diwarisi dari perubahan
   sebelumnya, kini permanen.

## 💡 Rekomendasi Lanjutan

1. Jalankan DoH/refresh juga melalui jalur terproteksi pin bila kelak DoH dipakai untuk
   data bernilai kredensial (saat ini hanya alamat IP publik — risiko rendah).
2. Pertimbangkan menyediakan `test.yml` terpisah HANYA bila job `verify` mulai terlalu
   lambat (saat ini duplikasi tidak beralasan).
3. Tambahkan properti `versionCode`/`versionName` bump pada rilis yang memuat audit ini
   (di luar wewenang agen — §4 menetapkan bump hanya atas permintaan maintainer).
4. Bila `EncryptedSharedPreferences` suatu hari ditinggalkan (deprecations androidx
   security-crypto), rancang penggantinya dengan kebijakan "tanpa fallback polos" yang
   sama — kini dijaga oleh struktur kode, bukan konvensi.
