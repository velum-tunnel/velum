package com.rollinkxx.velum

import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.widget.Button
import android.widget.CheckBox
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.wireguard.android.backend.Tunnel
import java.util.concurrent.Executors
import kotlin.math.roundToInt

/**
 * Memilih aplikasi yang **dikecualikan** dari tunnel (split tunneling): aplikasi
 * yang dicentang memakai jalur internet langsung, sisanya tetap lewat Velum.
 *
 * Sengaja tanpa RecyclerView/daftar berkinerja tinggi: layar ini dibuka jarang
 * dan daftarnya pendek (hanya aplikasi yang bisa diluncurkan), sehingga
 * LinearLayout seadanya menjaga ukuran APK tetap kecil.
 *
 * Yang bisa dibaca daftarnya dibatasi oleh `<queries>` di manifest (aplikasi
 * peluncur), sehingga tidak perlu izin `QUERY_ALL_PACKAGES` yang dibatasi.
 */
class AppExclusionActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs
    private lateinit var list: LinearLayout
    private val boxes = LinkedHashMap<String, CheckBox>()

    /** Menyambungkan ulang tunnel di latar setelah daftar pengecualian berubah. */
    private val worker = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app_exclusion)
        VelumInsets.applySystemBars(findViewById(R.id.root))
        // Tanpa keystore tidak ada penyimpanan untuk dibaca/ditulis: layar ini tidak
        // bisa bekerja, jadi tutup setelah menjelaskannya — bukan berpura-pura kosong.
        prefs = try {
            Prefs.of(this)
        } catch (e: KeystoreUnavailableException) {
            Toast.makeText(this, R.string.err_keystore_title, Toast.LENGTH_LONG).show()
            finish()
            return
        }
        list = findViewById(R.id.appList)

        val excluded = prefs.excludedApps
        val apps = launcherApps()
        if (apps.isEmpty()) {
            // Perangkat tanpa aplikasi peluncur yang terbaca (mis. profil kerja
            // terbatas): jelaskan keadaan kosong, jangan biarkan layar menggantung.
            list.addView(TextView(this).apply {
                setText(R.string.excluded_empty)
                setTextColor(resources.getColor(R.color.muted, theme))
                textSize = 13f
                setPadding(0, dp(12), 0, dp(12))
            })
        }
        // Yang dikecualikan dinaikkan ke atas di bawah labelnya sendiri, supaya
        // pilihan pengguna tidak tenggelam di antara puluhan aplikasi lain.
        val (dikecualikan, lainnya) = apps.partition { it.packageName in excluded }
        if (dikecualikan.isNotEmpty()) {
            addSectionHeader(getString(R.string.excluded_section_on), true)
            for (app in dikecualikan) addAppRow(app, excluded)
            addSectionHeader(getString(R.string.excluded_section_off), false)
            for (app in lainnya) addAppRow(app, excluded)
        } else {
            for (app in lainnya) addAppRow(app, excluded)
        }
        findViewById<Button>(R.id.save).setOnClickListener { save() }
        // Tombol kembali di bilah atas: memakai dispatcher yang sama dengan gestur
        // sistem, sehingga ikut tampil mulus pada animasi predictive back.
        findViewById<ImageButton>(R.id.back).setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
    }

    /**
     * dp -> piksel.
     *
     * `View.setPadding` menerima **piksel**, dan `12` mentah berarti 12px: di layar 3x
     * itu hanya 4dp, sehingga jarak antarbaris nyaris hilang di ponsel padat piksel
     * sementara tetap longgar di ponsel lama. Ukuran visual tidak boleh bergantung pada
     * kerapatan layar secara kebetulan seperti itu.
     */
    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).roundToInt()

    /** Judul bagian kecil di atas kelompok aplikasi. */
    private fun addSectionHeader(text: String, accent: Boolean) {
        list.addView(TextView(this).apply {
            setText(text)
            setTextColor(resources.getColor(if (accent) R.color.accent else R.color.muted, theme))
            setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL))
            textSize = 12f
            setPadding(0, dp(18), 0, dp(4))
        })
    }

    /** Satu baris aplikasi; yang dikecualikan dibedakan warna dan ketebalannya. */
    private fun addAppRow(app: AppInfo, excluded: Set<String>) {
        val picked = app.packageName in excluded
        val box = CheckBox(this).apply {
            text = app.label
            isChecked = picked
            setTextColor(resources.getColor(if (picked) R.color.accent else R.color.fg, theme))
            if (picked) setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL))
            setPadding(0, dp(12), 0, dp(12))
        }
        boxes[app.packageName] = box
        list.addView(box)
    }

    /**
     * Simpan daftar, lalu terapkan SEKARANG bila tunnel sedang naik.
     *
     * Daftar pengecualian hanya dibaca ketika tunnel dibangun (`VelumTunnel.buildConfig`),
     * jadi selama ini perubahan baru berlaku setelah pengguna memutus dan menyambungkan
     * sendiri — layar memang mengatakan itu, tetapi membebankan pekerjaan yang bisa
     * dilakukan aplikasi. [VelumTunnel.restart] membuat itu tidak perlu: down+up berjalan
     * atomik terhadap pelaku lain (ubin, pemantau jaringan) dan membaca ulang niat
     * pengguna di tengah jalan, jadi ia tidak akan menghidupkan tunnel yang sedang
     * diminta mati.
     */
    private fun save() {
        val before = prefs.excludedApps
        // Package visibility Android tidak selalu menampilkan semua package yang pernah
        // disimpan. Hanya package yang terlihat boleh diubah oleh checkbox; pilihan lama
        // untuk package tersembunyi harus dipertahankan agar tidak hilang diam-diam.
        val visible = boxes.keys
        val after = (before - visible) + boxes.filter { it.value.isChecked }.keys
        prefs.excludedApps = after
        val app = applicationContext
        if (after == before || VelumTunnel.state != Tunnel.State.UP) {
            // Tidak ada yang berubah, atau tunnel sedang turun: pengecualian dipakai saat
            // penyambungan berikutnya, tidak ada yang perlu disambungkan ulang sekarang.
            Toast.makeText(this, R.string.excluded_saved, Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        Toast.makeText(this, R.string.excluded_saved_restarting, Toast.LENGTH_SHORT).show()
        worker.execute {
            try {
                if (!VelumConnectionContract.reconnect(
                        app,
                        prefs,
                        VelumConnectionContract.HANDSHAKE_WAIT_MS
                    ) { !prefs.wasUp }
                ) {
                    VelumLog.w(TAG, "pengecualian aplikasi tidak menghasilkan handshake")
                }
            } catch (e: Exception) {
                VelumLog.w(TAG, "gagal menyambungkan ulang setelah pengecualian disimpan", e)
            }
        }
        finish()
    }

    /**
     * `shutdown()`, BUKAN `shutdownNow()`: memotong `restart()` di tengah berarti
     * membiarkan tunnel dalam keadaan turun padahal pengguna tidak pernah memintanya.
     * Tugas yang sudah terantre tetap dijalankan sampai selesai.
     */
    override fun onDestroy() {
        worker.shutdown()
        super.onDestroy()
    }

    /** Aplikasi peluncur terurut; Velum sendiri tidak ditawarkan. */
    private fun launcherApps(): List<AppInfo> {
        val pm = packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        @Suppress("DEPRECATION")
        val resolved = pm.queryIntentActivities(intent, 0)
        return resolved
            .map { AppInfo(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
            .filter { it.packageName != packageName }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }

    private data class AppInfo(val packageName: String, val label: String)

    private companion object {
        const val TAG = "Velum"
    }
}
