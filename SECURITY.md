# Keamanan Velum

## Pemindaian sertifikat (certificate pinning)

Velum memin kunci publik CA penerbit untuk **`api.cloudflareclient.com`** melalui
`app/src/main/res/xml/network_security_config.xml` (dirujuk dari `AndroidManifest.xml`).
Berlaku untuk seluruh koneksi HTTPS aplikasi tersebut — termasuk `HttpURLConnection`
yang dipakai `VelumApi` — sejak `minSdk` 24 (API tempat fitur ini diperkenalkan).

**Yang dipin:** SPKI (SHA-256, Base64) dari enam CA penerbit khusus Cloudflare —
`WR1`/`WE1` (Google Trust Services) dan `YR1`/`YR2`/`YE1`/`YE2` (Let's Encrypt).
**Yang sengaja TIDAK dipin:** host uji trace (`www.cloudflare.com`,
`one.one.one.one`) dan `cloudflare-dns.com` (DoH untuk penyegaran kandidat endpoint),
karena tidak membawa kredensial dan setiap pin adalah permukaan kegagalan baru.

**Rembesan anti-brick:** `<pin-set expiration="2027-03-31">`. Setelah tanggal itu
platform mengabaikan pin (fail-open), jadi aplikasi lama tidak mati permanen bila
rotasi luput. Harganya: pin wajib disegarkan pada setiap rilis.

## Cara rotasi pin (saat Cloudflare mengganti CA, dan setiap rilis)

1. Ambil penerbit terbaru dari log Certificate Transparency:

   ```sh
   curl -s "https://api.certspotter.com/v1/issuances?domain=cloudflareclient.com&include_subdomains=false&expand=issuer" \
     | python3 -c 'import json,sys; [print(i["issuer"]["name"], i["issuer"]["pubkey_sha256"], i["not_after"]) for i in json.load(sys.stdin)]'
   ```

   (Alternatif: `openssl s_client -connect api.cloudflareclient.com:443 -servername
   api.cloudflareclient.com` lalu periksa `issuer=` sertifikat yang disajikan, dan
   unduh rantainya.)

2. Untuk setiap CA penerbit yang terlihat di langkah 1, pastikan nilai
   `pubkey_sha256`-nya benar hash SPKI DER-nya dengan menghitung sendiri dari
   sertifikat CA tersebut:

   ```sh
   openssl x509 -in ca.pem -pubkey -noout | openssl pkey -pubin -outform DER | openssl dgst -sha256
   ```

3. Ubah hex menjadi Base64 (format yang diminta `<pin digest="SHA-256">`):

   ```sh
   python3 -c 'import binascii,base64;print(base64.b64encode(binascii.unhexlify("HEX_DISINI")).decode())'
   ```

4. Perbarui `network_security_config.xml`: gantikan pin untuk CA yang berubah,
   **pertahankan minimal satu pin CA yang masih berlaku**, dan majukan
   `expiration` (aturan praktis: ±6 bulan dari tanggal rilis yang akan memuat
   berkas ini).

5. Verifikasi sebelum dirilis: pasang APK preview pada perangkat, tekan
   **Sambungkan**, dan pastikan registrasi berhasil (TLS lolos). Bila pin salah,
   kegagalannya adalah `SSLHandshakeException` pada koneksi pertama — terlihat
   langsung sebagai galat jaringan, bukan kegagalan senyap.

## Penyimpanan kredensial

Kunci privat WireGuard, token, dan identitas perangkat disimpan **hanya** di
`EncryptedSharedPreferences` (AES256-GCM, kunci dari Android Keystore) — tidak ada
fallback polos. Bila keystore atau prefs terenkripsi gagal dibuka, aplikasi melempar
`KeystoreUnavailableException`, menampilkan "Penyimpanan aman tidak tersedia.
Daftar ulang diperlukan.", dan berhenti. Aplikasi **tidak menghapus atau mereset
prefs secara otomatis**: reset kredensial harus menjadi tindakan pengguna yang
eksplisit agar kegagalan sementara atau permission/race tidak berubah menjadi
kehilangan data. Lihat `Prefs.open`.

## Pelaporan kerentanan

Buka *issue* di repositori ini untuk temuan berdampak rendah/sedang. Untuk temuan
kritis (kebocoran kunci privat, pemutusan tunnel), hubungi maintainer terlebih
dahulu lewat profil pemilik repositori sebelum membuka detail di publik.
