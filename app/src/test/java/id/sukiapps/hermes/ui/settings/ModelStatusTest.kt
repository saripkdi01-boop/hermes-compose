package id.sukiapps.hermes.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Unit test JVM untuk klasifikasi & format status model (tanpa Android). */
class ModelStatusTest {

    @Test
    fun classify_401_isDead() {
        assertEquals(
            ModelStatusState.DEAD,
            ModelStatusHelper.classifyError("API key salah atau kedaluwarsa (401).")
        )
    }

    @Test
    fun classify_404_isDead() {
        assertEquals(
            ModelStatusState.DEAD,
            ModelStatusHelper.classifyError("Model \"x\" tidak ditemukan di server (404).")
        )
    }

    @Test
    fun classify_500_isDead() {
        assertEquals(
            ModelStatusState.DEAD,
            ModelStatusHelper.classifyError("Validasi gagal (HTTP 500) untuk model \"x\".")
        )
    }

    @Test
    fun classify_timeout_isUnknown() {
        assertEquals(
            ModelStatusState.UNKNOWN,
            ModelStatusHelper.classifyError("Koneksi timeout. Coba lagi.")
        )
    }

    @Test
    fun classify_429_isUnknown() {
        assertEquals(
            ModelStatusState.UNKNOWN,
            ModelStatusHelper.classifyError("Batas request tercapai (429). Coba lagi sebentar.")
        )
    }

    @Test
    fun classify_null_isUnknown() {
        assertEquals(ModelStatusState.UNKNOWN, ModelStatusHelper.classifyError(null))
    }

    @Test
    fun format_underOneSecond_isMs() {
        assertEquals("842 mdtk", ModelStatusHelper.formatLatency(842))
    }

    @Test
    fun format_overOneSecond_isSecondsId() {
        assertEquals("1,3 dtk", ModelStatusHelper.formatLatency(1340))
    }

    @Test
    fun format_null_isNull() {
        assertNull(ModelStatusHelper.formatLatency(null))
    }
}
