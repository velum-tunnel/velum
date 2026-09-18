# Rilis GitHub (APK bertanda tangan)

Distribusi Velum saat ini hanya via GitHub Releases (tanpa Play Store).
Job CI `assembleRelease (bertanda tangan)` membangun APK rilis bila Secrets dan
variable di bawah disiapkan. Tanpa itu job di-skip (bukan gagal).

> ⚠️ **Keystore dan sandinya tidak boleh dibagikan kepada siapa pun** — termasuk
> kepada agen/asisten yang membantu repo ini. Semua langkah di bawah dijalankan
> sendiri oleh maintainer; agen tidak pernah perlu melihat isinya.

## 0. Sebelum mulai: apakah ini memang perlu sekarang?

Tidak mendesak. Untuk **menguji aplikasi di perangkat sendiri**, pakai artifact
`app-preview` dari CI — sudah diperkecil R8 seperti rilis, tetapi bertanda tangan
kunci debug sehingga tidak butuh keystore sama sekali.

Keystore baru diperlukan ketika APK akan **dibagikan ke orang lain lewat GitHub
Releases**, karena identitas penandatangan itulah yang memungkinkan pembaruan
dipasang menimpa versi lama.

## 1. Buat keystore sekali saja

### Di komputer (Linux/macOS/Windows, butuh JDK)

```sh
keytool -genkeypair -v -keystore velum-release.keystore \
  -alias velum -keyalg RSA -keysize 2048 -validity 10000
base64 -w0 velum-release.keystore > velum-release.keystore.b64
```

### Di Android lewat Termux (tanpa komputer)

```sh
pkg install openjdk-17 openssl-tool
keytool -genkeypair -v -keystore velum-release.keystore \
  -alias velum -keyalg RSA -keysize 2048 -validity 10000
base64 -w0 velum-release.keystore > velum-release.keystore.b64
```

`keytool` akan menanyakan berurutan:

| Pertanyaan | Isi |
|---|---|
| Enter keystore password | sandi keystore — **catat**, dipakai di langkah 2 |
| Re-enter new password | ulangi sandi yang sama |
| What is your first and last name? | bebas, mis. `Velum` (ini CN, bukan nama asli wajib) |
| Organizational unit / Organization / City / State | boleh dikosongkan (Enter) |
| Two-letter country code | `ID` |
| Is CN=..., correct? | ketik `yes` |
| Enter key password for `<velum>` | tekan Enter agar sama dengan sandi keystore |

`-validity 10000` ≈ 27 tahun. Jangan dibuat pendek: APK yang ditandatangani
kunci kedaluwarsa tidak bisa diperbarui lagi.

> Linux/Termux memakai `base64 -w0`; di macOS pakai `base64 -i`.
> **Cadangkan `velum-release.keystore` + sandi di tempat aman** (mis. pengelola
> sandi, bukan di dalam repo dan bukan di chat mana pun).
> Keystore hilang = aplikasi tidak bisa di-update (harus publish ulang sebagai app baru).

## 2. Set Secrets + variable repo

### Lewat `gh` (butuh login `gh auth login`)

```sh
gh secret set SIGNING_KEYSTORE_BASE64 < velum-release.keystore.b64
gh secret set KEYSTORE_PASSWORD   # sandi keystore
gh secret set KEY_ALIAS           # isi: velum
gh secret set KEY_PASSWORD        # sandi key (biasanya sama)
gh variable set ENABLE_RELEASE_SIGNING --body true
```

### Lewat browser

`github.com/rollinkxx/velum` → **Settings** → **Secrets and variables** →
**Actions**. Tab **Secrets** untuk empat yang pertama (**New repository secret**),
tab **Variables** untuk yang terakhir (**New repository variable**).

| Nama | Tempat | Isi |
|---|---|---|
| `SIGNING_KEYSTORE_BASE64` | Secrets | seluruh isi `velum-release.keystore.b64` |
| `KEYSTORE_PASSWORD` | Secrets | sandi keystore |
| `KEY_ALIAS` | Secrets | `velum` |
| `KEY_PASSWORD` | Secrets | sandi key |
| `ENABLE_RELEASE_SIGNING` | **Variables** | `true` |

Yang terakhir sengaja Variable, bukan Secret: nilainya bukan rahasia dan job
`release` membacanya lewat `if: vars.ENABLE_RELEASE_SIGNING == 'true'` —
ekspresi `if` sebuah job tidak bisa membaca Secret.

Isi `.b64` harus disalin **utuh tanpa baris baru** (itu guna `-w0`). Setelah
diunggah, hapus berkasnya: `rm velum-release.keystore.b64`. Jangan pernah
commit keystore maupun `.b64` ke repo.

## 3. Picu build rilis

Mengisi Secret/variable **tidak** memicu workflow. Picu dengan mendorong commit
kode apa pun, atau secara manual:

```sh
gh workflow run build --ref main
gh run watch --exit-status
```

Job `assembleRelease (bertanda tangan)` berjalan setelah job verifikasi hijau.
Bila job itu tampil abu-abu (*skipped*), berarti `ENABLE_RELEASE_SIGNING` belum
terbaca — lihat tabel di bagian "Bila terjadi kesalahan".

## 4. Ambil & verifikasi APK

CI sudah memverifikasi tanda tangannya sendiri: job rilis menggagalkan build bila
APK ternyata tidak bertanda tangan atau memakai kunci debug, dan mencetak sidik
jari SHA-256 tiap APK ke step summary. Periksa tabel itu lebih dulu — sering kali
tidak perlu mengunduh apa pun.

Untuk memeriksa sendiri:

```sh
gh run download <id-run> -n app-release
ls *.apk   # empat berkas: tiga per arsitektur + satu universal
apksigner verify --print-certs app-arm64-v8a-release.apk
```

Sejak pemecahan per ABI diaktifkan, satu build menghasilkan **empat** APK:

| Berkas | Untuk siapa | Ukuran |
|---|---|---|
| `app-arm64-v8a-release.apk` | Mayoritas ponsel Android modern (64-bit) | ±3 MB |
| `app-armeabi-v7a-release.apk` | Ponsel lama 32-bit | ±3 MB |
| `app-x86_64-release.apk` | Emulator & Chromebook | ±3 MB |
| `app-universal-release.apk` | Cadangan: berjalan di semua arsitektur | terbesar |

`versionCode` tiap berkas sengaja berbeda (`abiCode * 1000 + versionCode`), sedangkan
universal memakai nilai terendah — supaya APK spesifik arsitektur selalu lebih diutamakan.

**Sidik jari SHA-256 wajib sama pada setiap rilis.** Bila berubah, pengguna lama
tidak bisa memperbarui aplikasi: mereka harus mencopot pasang dan kehilangan data
registrasi. Catat sidik jari rilis pertama sebagai acuan.

`apksigner` ada di Android SDK build-tools; di Termux: `pkg install apksigner`.

## 5. Terbitkan Release

```sh
gh release create v0.1.0 *.apk \
  --title "Velum 0.1.0" \
  --notes "Catatan rilis: salin dari CHANGELOG bagian rilis terkait.

Pilih berkas sesuai perangkat:
- Ponsel Android modern (mayoritas): app-arm64-v8a-release.apk
- Ponsel lama 32-bit: app-armeabi-v7a-release.apk
- Emulator/Chromebook: app-x86_64-release.apk
- Tidak yakin: app-universal-release.apk (berjalan di semua, ukuran lebih besar)"
```

Lewat browser: halaman **Releases** → *Draft a new release* → isi tag → unggah
keempat APK dari artifact `app-release`.

Versi (`versionName`/`versionCode`) hanya di-bump atas perintah eksplisit
(lihat kebijakan versioning proyek); sesuaikan tag dengan versi di `app/build.gradle.kts`.

## Bila terjadi kesalahan

| Gejala | Sebab | Perbaikan |
|---|---|---|
| Job `release` tetap di-skip | `ENABLE_RELEASE_SIGNING` dibuat sebagai Secret, bukan Variable | Pindahkan ke tab **Variables** |
| `keystore password was incorrect` | `KEYSTORE_PASSWORD`/`KEY_PASSWORD` tertukar | Samakan; keduanya sama bila langkah 1 memakai Enter |
| `Failed to read key velum` | `KEY_ALIAS` salah ketik | Cek dengan `keytool -list -keystore velum-release.keystore` |
| APK rilis tidak muncul | base64 rusak/terpotong | Buat ulang `.b64` dengan `-w0`, set ulang Secret |
| Job gagal di "Verifikasi tanda tangan" | APK tak bertanda tangan / berkunci debug | Secret keystore tidak terbaca; periksa keempat Secret di langkah 2 |
