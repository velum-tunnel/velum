package com.rollinkxx.velum

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class VelumErrorTest {

    @Test
    fun kodePenolakanKlien_dikenali() {
        for (code in listOf(401, 403, 404, 410, 426)) {
            assertEquals(
                "HTTP $code",
                VelumError.Kind.SERVER_REJECT,
                VelumError.kindOf(VelumApi.HttpError(code, "HTTP $code"))
            )
        }
    }

    @Test
    fun kodeServerLain_bukanPenolakanKlien() {
        for (code in listOf(500, 502, 503, 429)) {
            assertEquals(
                "HTTP $code",
                VelumError.Kind.UNKNOWN,
                VelumError.kindOf(VelumApi.HttpError(code, "HTTP $code"))
            )
        }
    }

    @Test
    fun kegagalanJaringan_dikenali() {
        assertEquals(VelumError.Kind.NETWORK, VelumError.kindOf(UnknownHostException("api")))
        assertEquals(VelumError.Kind.NETWORK, VelumError.kindOf(SocketTimeoutException("timeout")))
        assertEquals(VelumError.Kind.NETWORK, VelumError.kindOf(ConnectException("refused")))
        assertEquals(VelumError.Kind.NETWORK, VelumError.kindOf(IOException("reset")))
    }

    @Test
    fun kegagalanKeystore_bukanDituduhMasalahJaringan() {
        // KeystoreUnavailableException adalah IOException, tetapi ia tidak pernah berarti
        // jaringan putus — dan SERVICE_MARKERS tidak boleh menangkapnya sebagai
        // penolakan layanan latar depan.
        assertEquals(
            VelumError.Kind.KEYSTORE,
            VelumError.kindOf(KeystoreUnavailableException(SecurityException("keystore?")))
        )
        assertEquals(
            VelumError.Kind.KEYSTORE,
            VelumError.kindOf(KeystoreUnavailableException(RuntimeException("gagal total")))
        )
    }

    @Test
    fun kegagalanTakDikenal_dan_null() {
        assertEquals(VelumError.Kind.UNKNOWN, VelumError.kindOf(IllegalStateException("aneh")))
        assertEquals(VelumError.Kind.UNKNOWN, VelumError.kindOf(null))
    }

    @Test
    fun persistenceFailure_bukanNetwork() {
        assertEquals(
            VelumError.Kind.STORAGE,
            VelumError.kindOf(PersistenceException("disk penuh"))
        )
    }

    @Test
    fun penolakanLayananLatarDepan_dikenali() {
        // Bentuk pesan yang mungkin datang dari framework/library pada Android 16+.
        val contoh = listOf(
            SecurityException("Starting FGS with type specialUse requires permissions"),
            IllegalStateException("Context.startForegroundService() did not then call Service.startForeground()"),
            RuntimeException("Unable to start VpnService"),
            Exception("background start not allowed: service is not allowed to start")
        )
        for (e in contoh) {
            assertEquals(e.message, VelumError.Kind.SERVICE_BLOCKED, VelumError.kindOf(e))
        }
    }

    @Test
    fun penolakanLayanan_dikenaliLewatPenyebab() {
        val e = IllegalStateException("gagal menyambung", SecurityException("foreground service denied"))
        assertEquals(VelumError.Kind.SERVICE_BLOCKED, VelumError.kindOf(e))
    }

    @Test
    fun kegagalanJaringan_tidakSalahDiklasifikasikanSebagaiLayanan() {
        // Pesan jaringan tetap NETWORK walau kebetulan memuat kata umum.
        assertEquals(VelumError.Kind.NETWORK, VelumError.kindOf(IOException("connection reset")))
    }

    @Test
    fun penolakanLayanan_terbungkusIOException_tetapDikenali() {
        // Regresi: cabang `is IOException -> NETWORK` dulu dievaluasi lebih dulu, sehingga
        // penolakan layanan yang terbungkus IOException dilaporkan ke pengguna sebagai
        // "Kesalahan jaringan" dan menyuruhnya memeriksa jaringan yang sebenarnya sehat.
        assertEquals(
            VelumError.Kind.SERVICE_BLOCKED,
            VelumError.kindOf(IOException("startForeground() not allowed for service"))
        )
        assertEquals(
            VelumError.Kind.SERVICE_BLOCKED,
            VelumError.kindOf(IOException("Unable to start VpnService foreground service"))
        )
        // Sisi lainnya juga dijaga: IOException jaringan biasa tidak boleh ikut berubah.
        assertEquals(
            VelumError.Kind.NETWORK,
            VelumError.kindOf(IOException("connection reset by peer"))
        )
    }
}
