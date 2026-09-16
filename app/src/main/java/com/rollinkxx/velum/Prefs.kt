package com.rollinkxx.velum

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.io.File

/**
 * Penyimpanan data registrasi. **Hanya** terenkripsi (AndroidX Security + Tink);
 * data era polos (berkas lama) dimigrasi sekali.
 *
 * Tidak ada fallback ke berkas polos: kunci privat dan token tidak boleh pernah
 * tertulis tanpa enkripsi. Bila keystore perangkat gagal dibuka, [open] melempar
 * [KeystoreUnavailableException] dan UI meminta pengguna menyalakan ulang perangkat
 * lalu mendaftar ulang.
 */
class Prefs(context: Context) {
    private val sp: SharedPreferences = open(context.applicationContext)

    var privateKey: String?
        get() = sp.getString(K_PRIV, null)
        set(v) = sp.edit().putString(K_PRIV, v).apply()

    var deviceId: String?
        get() = sp.getString(K_ID, null)
        set(v) = sp.edit().putString(K_ID, v).apply()

    var token: String?
        get() = sp.getString(K_TOKEN, null)
        set(v) = sp.edit().putString(K_TOKEN, v).apply()

    var addressV4: String?
        get() = sp.getString(K_V4, null)
        set(v) = sp.edit().putString(K_V4, v).apply()

    var addressV6: String?
        get() = sp.getString(K_V6, null)
        set(v) = sp.edit().putString(K_V6, v).apply()

    var peerPublicKey: String?
        get() = sp.getString(K_PEER, null)
        set(v) = sp.edit().putString(K_PEER, v).apply()

    var endpoint: String?
        get() = sp.getString(K_ENDPOINT, null)
        set(v) = sp.edit().putString(K_ENDPOINT, v).apply()

    /**
     * Endpoint pilihan pengguna ("host:port"), diisi lewat layar utama.
     *
     * Bila diisi, ia MENGATASI seluruh pemilihan otomatis: proba dilewati dan
     * speed/working endpoint tidak lagi dipakai membangun tunnel. Dipertahankan oleh
     * [clear] — ia pilihan milik pengguna, sama seperti [excludedApps], bukan milik
     * registrasi.
     */
    var manualEndpoint: String?
        get() = sp.getString(K_MANUAL_EP, null)
        set(v) = sp.edit().putString(K_MANUAL_EP, v).apply()

    /** Kandidat anycast hasil DoH terakhir (dipisah koma); null/kosong = daftar statis. */
    var dohCandidates: String?
        get() = sp.getString(K_DOH_EP, null)
        set(v) = sp.edit().putString(K_DOH_EP, v).apply()

    /** Kapan kandidat DoH terakhir diambil (epoch ms); basi setelah 24 jam. */
    var dohCandidatesAt: Long
        get() = sp.getLong(K_DOH_AT, 0L)
        set(v) = sp.edit().putLong(K_DOH_AT, v).apply()

    /** Endpoint tercepat hasil proba (null = pakai endpoint registrasi). */
    var speedEndpoint: String?
        get() = sp.getString(K_SPEED_EP, null)
        set(v) = sp.edit().putString(K_SPEED_EP, v).apply()

    /** Waktu proba terakhir (epoch ms); basi setelah 1 jam. */
    var speedEndpointAt: Long
        get() = sp.getLong(K_SPEED_AT, 0L)
        set(v) = sp.edit().putLong(K_SPEED_AT, v).apply()

    /**
     * Endpoint yang **terbukti** menghasilkan handshake di perangkat ini.
     *
     * Berbeda dari [speedEndpoint] yang hanya perkiraan urutan RTT, nilai ini bukti nyata;
     * karena itu diutamakan (bukti > perkiraan). Dikosongkan oleh [EndpointProbe.rotate]
     * ketika endpoint tersebut justru gagal handshake.
     */
    var workingEndpoint: String?
        get() = sp.getString(K_WORKING_EP, null)
        set(v) = sp.edit().putString(K_WORKING_EP, v).apply()

    /** Hasil uji trace terakhir (`null` = belum pernah diuji). Bertahan lintas restart. */
    var lastTest: VelumTestResult?
        get() = VelumTestResult.decode(sp.getString(K_LAST_TEST, null))
        set(v) = sp.edit().putString(K_LAST_TEST, v?.encode()).apply()

    /**
     * Endpoint efektif: pilihan **manual** pengguna, lalu yang terbukti bekerja, lalu
     * hasil proba, lalu endpoint registrasi. Manual didahulukan karena ia satu-satunya
     * nilai yang dipilih langsung oleh pengguna.
     */
    val effectiveEndpoint: String?
        get() = manualEndpoint ?: workingEndpoint ?: speedEndpoint ?: endpoint

    /** Paket aplikasi yang dikecualikan dari tunnel (split tunneling). */
    var excludedApps: Set<String>
        get() = sp.getStringSet(K_EXCLUDED, null)?.toSet() ?: emptySet()
        set(v) = sp.edit().putStringSet(K_EXCLUDED, v).apply()

    /** Memo: akun terkonfirmasi memakai flag WARP penuh (set oleh registrasi/ensure). */
    var warpEnabled: Boolean
        get() = sp.getBoolean(K_WARP, false)
        set(v) = sp.edit().putBoolean(K_WARP, v).apply()

    /** Memo: terakhir kali tunnel memang UP (untuk sambung ulang saat boot). */
    var wasUp: Boolean
        get() = sp.getBoolean(K_WAS_UP, false)
        set(v) = sp.edit().putBoolean(K_WAS_UP, v).apply()

    /**
     * Menetapkan memo lifecycle secara durabel. Nilai ini menentukan apakah boot/recovery
     * boleh menghidupkan VPN setelah proses mati, sehingga `apply()` tidak cukup untuk
     * jalur pembatalan atau keberhasilan koneksi.
     */
    @SuppressLint("ApplySharedPref")
    fun setWasUpDurable(value: Boolean): Boolean =
        sp.edit().putBoolean(K_WAS_UP, value).commit()

    /**
     * Rekaman percobaan sambung ulang otomatis terakhir oleh [BootReceiver], dalam format
     * [VelumDiagnostics.encodeBoot] (`outcome|durationMs|atEpochMs`); null bila belum pernah.
     *
     * Angka inilah satu-satunya bukti berapa lama boot menghabiskan anggaran `goAsync()`,
     * dan maintainer membacanya dari layar diagnostik karena tidak punya adb.
     */
    val bootRecord: String?
        get() = sp.getString(K_BOOT, null)

    /**
     * Tulis rekaman boot secara **durabel** (`commit()`).
     *
     * HANYA boleh dipanggil dari thread latar: `commit()` menulis ke disk secara sinkron.
     * Dipakai [BootReceiver] di dalam thread pekerjaannya, tepat sebelum
     * `PendingResult.finish()` — sesudah itu proses boleh dibunuh sistem kapan saja, dan
     * `apply()` yang masih mengantre di memori bisa hilang bersama angkanya.
     */
    @SuppressLint("ApplySharedPref")
    fun writeBootRecordDurable(value: String) {
        sp.edit().putString(K_BOOT, value).commit()
    }

    /**
     * Tulis rekaman boot tanpa menahan thread (`apply()`).
     *
     * Dipakai pada jalur [BootReceiver] yang berjalan di **main thread** (persetujuan VPN
     * hilang, tidak ada pekerjaan latar). Rekaman ini murni diagnostik, dan menulis sinkron
     * di dalam receiver yang berjalan di main thread adalah biaya yang tidak sepadan untuk
     * satu baris teks. Konsekuensi yang diterima sadar: bila proses dibunuh sebelum tulisan
     * mendarat, rekaman hilang dan baris `Boot` menunjukkan percobaan sebelumnya atau
     * "belum ada percobaan" — membingungkan, tetapi tidak merusak apa pun.
     */
    fun writeBootRecord(value: String) {
        sp.edit().putString(K_BOOT, value).apply()
    }

    /** Registrasi dianggap lengkap bila semua bidang inti tersedia. */
    val isRegistered: Boolean
        get() = !privateKey.isNullOrEmpty() && !addressV4.isNullOrEmpty() &&
            !peerPublicKey.isNullOrEmpty() && !endpoint.isNullOrEmpty()

    /**
     * Menulis seluruh hasil registrasi dalam SATU transaksi.
     *
     * Sebelumnya ketujuh bidang ditulis satu per satu lewat `apply()`. Proses yang mati di
     * tengah penulisan meninggalkan campuran kunci privat BARU dengan endpoint/peer LAMA,
     * sementara [isRegistered] tetap `true` karena semua bidang terisi — akibatnya
     * handshake gagal terus-menerus tanpa pesan yang menunjuk penyebabnya, dan satu-satunya
     * jalan keluar adalah pengguna menemukan tombol "Daftar ulang" sendiri.
     *
     * `commit()` dipakai sengaja (bukan `apply()`): SharedPreferences menulis ke berkas
     * sementara lalu mengganti namanya, sehingga satu `commit` bersifat atomik terhadap
     * proses yang mati — seluruh bidang masuk, atau tidak sama sekali.
     */
    @SuppressLint("ApplySharedPref")
    fun saveRegistration(r: VelumRegistration.Result, privateKeyBase64: String) {
        sp.edit()
            .putString(K_PRIV, privateKeyBase64)
            .putString(K_ID, r.id)
            .putString(K_TOKEN, r.token)
            .putString(K_V4, r.addressV4)
            // null di sini menghapus kunci lama: alamat IPv6 sesi sebelumnya tidak boleh
            // tertinggal menempel pada kunci yang baru.
            .putString(K_V6, r.addressV6)
            .putString(K_PEER, r.peerPublicKey)
            .putString(K_ENDPOINT, r.endpoint)
            .putBoolean(K_WARP, true) // body registrasi memang meminta warp_enabled
            .commit()
    }

    /**
     * Membersihkan data registrasi.
     *
     * Dua hal sengaja DIPERTAHANKAN karena keduanya bukan bagian dari registrasi:
     * - [wasUp]: niat pengguna untuk tersambung saat boot;
     * - [excludedApps]: pilihan split tunneling milik pengguna. Sebelumnya ikut terhapus,
     *   sehingga menekan "Daftar ulang" diam-diam menghapus daftar pengecualian yang sudah
     *   disusun pengguna — dan dialog konfirmasinya tidak mengatakan itu.
     *
     * Semuanya ditulis dalam SATU transaksi (`clear()` + ketiga `put` + `commit()`), bukan
     * beberapa tulisan terpisah seperti sebelumnya: proses yang mati di antara `clear()`
     * dan penulisan ulang akan menghapus niat dan pengecualian pengguna — persis kelas
     * kegagalan yang [saveRegistration] tutup dengan `commit()`.
     *
     * [bootRecord] ikut dipertahankan (ditambahkan 2026-09-13), walaupun ia bukan milik
     * registrasi: baris `Boot` pada layar diagnostik adalah satu-satunya bukti tanpa-adb
     * untuk anggaran `goAsync()` (TODO 77), dan menghapusnya setiap kali pengguna menekan
     * Daftar ulang berarti menghilangkan ukuran yang belum sempat dibaca.
     *
     * [manualEndpoint] juga dipertahankan: ia disusun pengguna sama seperti daftar
     * pengecualian, dan menghapusnya diam-diam akan membuat proba otomatis berjalan lagi
     * padahal pengguna pernah dengan sengaja mematikannya.
     */
    @SuppressLint("ApplySharedPref")
    fun clear() {
        val keepUp = wasUp
        val keepExcluded = excludedApps
        val keepBoot = bootRecord
        val keepManual = manualEndpoint
        val ed = sp.edit().clear()
        if (keepUp) ed.putBoolean(K_WAS_UP, true)
        if (keepExcluded.isNotEmpty()) ed.putStringSet(K_EXCLUDED, keepExcluded)
        if (keepBoot != null) ed.putString(K_BOOT, keepBoot)
        if (keepManual != null) ed.putString(K_MANUAL_EP, keepManual)
        ed.commit()
    }

    companion object {
        @Volatile
        private var instance: Prefs? = null

        /**
         * Satu instance per proses. Membuka prefs terenkripsi itu mahal (baca + dekripsi
         * seluruh nilai untuk pengecekan migrasi), sehingga dipakai bersama oleh UI,
         * [ReconnectMonitor], dan [BootReceiver].
         *
         * Melempar [KeystoreUnavailableException] bila keystore perangkat tidak bisa
         * dipakai — sengaja TIDAK menyimpan apa pun dalam keadaan itu (tidak ada
         * fallback polos), jadi instance yang gagal juga tidak di-cache: pemanggilan
         * berikutnya mencoba membuka lagi dari awal.
         */
        @Throws(KeystoreUnavailableException::class)
        fun of(context: Context): Prefs =
            instance ?: synchronized(this) {
                instance ?: Prefs(context.applicationContext).also { instance = it }
            }

        const val TAG = "Velum"
        const val FILE = "velum"
        const val LEGACY_FILE = "warp"
        const val K_PRIV = "private_key"
        const val K_ID = "device_id"
        const val K_TOKEN = "token"
        const val K_V4 = "addr_v4"
        const val K_V6 = "addr_v6"
        const val K_PEER = "peer_pub"
        const val K_ENDPOINT = "endpoint"
        const val K_SPEED_EP = "speed_ep"
        const val K_SPEED_AT = "speed_at"
        const val K_WORKING_EP = "working_ep"
        const val K_LAST_TEST = "last_test"
        const val K_WARP = "warp_enabled"
        const val K_WAS_UP = "was_up"
        const val K_BOOT = "boot_last"
        const val K_EXCLUDED = "excluded_apps"
        const val K_MANUAL_EP = "manual_ep"
        const val K_DOH_EP = "doh_ep"
        const val K_DOH_AT = "doh_at"

        /**
         * Membuka penyimpanan terenkripsi, hanya itu.
         *
         * Kegagalan pertama dicoba pulihkan SEKALI: penyebab tersering adalah berkas
         * prefs terenkripsi yang rusak (mis. penulisan yang terputus di tengah), yang
         * membuat `create()` gagal SELAMANYA sehingga aplikasi tidak bisa menyimpan apa
         * pun. Berkas yang sudah terbukti tidak terbaca untuk kunci ini tidak menyimpan
         * apa pun yang masih bisa diselamatkan, jadi ia dikosongkan lalu pembukaan
         * diulang — data registrasinya memang hilang, tetapi aplikasi bisa mendaftar
         * ulang (persis konsekuensi yang dipilih untuk perangkat era fallback polos:
         * daftar ulang SEKALI).
         *
         * Kegagalan kedua berarti keystore-nya yang bermasalah. Di sini SENGAJA tidak
         * ada fallback ke berkas polos (kunci privat tidak boleh tersimpan tanpa
         * enkripsi, berapa pun harganya): lempar [KeystoreUnavailableException] dan
         * biarkan pemanggil menjelaskannya ke pengguna.
         */
        @Throws(KeystoreUnavailableException::class)
        private fun open(ctx: Context): SharedPreferences {
            try {
                return openEncrypted(ctx).also { migrateLegacy(ctx, it) }
            } catch (e: Exception) {
                VelumLog.w(TAG, "prefs terenkripsi gagal dibuka; berkas dikosongkan lalu dicoba ulang", e)
                deleteEncryptedFile(ctx)
                return try {
                    openEncrypted(ctx).also { migrateLegacy(ctx, it) }
                } catch (kedua: Exception) {
                    throw KeystoreUnavailableException(kedua)
                }
            }
        }

        private fun openEncrypted(ctx: Context): SharedPreferences {
            val masterKey = MasterKey.Builder(ctx)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            return EncryptedSharedPreferences.create(
                ctx,
                FILE,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        }

        /** Menghapus berkas terenkripsi yang sudah terbukti tidak bisa dibuka. */
        private fun deleteEncryptedFile(ctx: Context) {
            try {
                File(File(ctx.applicationInfo.dataDir, "shared_prefs"), "$FILE.xml").delete()
            } catch (e: Exception) {
                VelumLog.w(TAG, "gagal mengosongkan berkas prefs rusak", e)
            }
        }

        /** Keberadaan berkas era lama, tanpa membuka/dekripsi isinya. */
        private fun legacyFileExists(ctx: Context): Boolean = try {
            File(File(ctx.applicationInfo.dataDir, "shared_prefs"), "$LEGACY_FILE.xml").exists()
        } catch (_: Exception) {
            false
        }

        /**
         * Menyalin sekali data era polos (file "warp") ke penyimpanan terenkripsi.
         * `commit()` dipakai sengaja: kita harus tahu pasti data sudah menetap sebelum
         * berkas lama dikosongkan.
         */
        @SuppressLint("ApplySharedPref")
        private fun migrateLegacy(ctx: Context, dst: SharedPreferences) {
            // Cek murah dulu: bila berkas era lama tak pernah ada, tak ada yang dimigrasi
            // dan kita terhindar dari pembacaan + dekripsi seluruh nilai (`dst.all`).
            if (!legacyFileExists(ctx)) return
            if (dst.all.isNotEmpty()) return
            val legacy = ctx.getSharedPreferences(LEGACY_FILE, Context.MODE_PRIVATE)
            val rencana = VelumMigration.plan(legacy.all)
            if (rencana.isEmpty()) return
            try {
                val ed = dst.edit()
                for ((k, v) in rencana) {
                    when (v) {
                        is String -> ed.putString(k, v)
                        is Boolean -> ed.putBoolean(k, v)
                        is Int -> ed.putInt(k, v)
                        is Long -> ed.putLong(k, v)
                        is Float -> ed.putFloat(k, v)
                        is Set<*> -> {
                            @Suppress("UNCHECKED_CAST")
                            ed.putStringSet(k, v as Set<String>)
                        }
                    }
                }
                if (!ed.commit()) return
                legacy.edit().clear().commit()
                VelumLog.i(TAG, "migrasi prefs lama selesai")
            } catch (e: Exception) {
                VelumLog.w(TAG, "migrasi prefs lama gagal", e)
            }
        }
    }
}
