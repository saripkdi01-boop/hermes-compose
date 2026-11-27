package id.sukiapps.hermes.util

import id.sukiapps.hermes.domain.Prefs
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v19.1 (audit P0): restore backup tidak boleh membuat tipe prefs salah
 * (dulu `current_conv` kembali sebagai Int → crash ClassCastException
 * setiap aplikasi dibuka) dan rahasia tidak boleh ikut backup.
 */
class BackupPrefsTest {

    /** Simulasi backup → JSON → restore persis seperti BackupManager. */
    private fun roundTrip(values: Map<String, Any?>, hints: Map<String, Any?> = emptyMap()): Map<String, Any?> {
        val jo = JSONObject()
        for ((k, v) in values) if (!BackupPrefs.isExcluded(k)) jo.put(k, BackupPrefs.toJsonValue(v))
        return BackupPrefs.decode(jo.toString(), hints)
    }

    @Test
    fun currentConv_smallLong_restoresAsLong() {
        val out = roundTrip(mapOf(Prefs.KEY_CURRENT_CONV to 12L))
        assertEquals(12L, out[Prefs.KEY_CURRENT_CONV])
        assertTrue(out[Prefs.KEY_CURRENT_CONV] is Long)
    }

    @Test
    fun currentConv_negativeOne_restoresAsLong() {
        val out = roundTrip(mapOf(Prefs.KEY_CURRENT_CONV to -1L))
        assertTrue(out[Prefs.KEY_CURRENT_CONV] is Long)
    }

    @Test
    fun wholeFloats_restoreAsFloat() {
        val out = roundTrip(
            mapOf(
                Prefs.KEY_TEMPERATURE to 1.0f,
                Prefs.KEY_TOP_P to 0.9f,
                Prefs.KEY_TTS_SPEED to 2.0f
            )
        )
        assertEquals(1.0f, out[Prefs.KEY_TEMPERATURE])
        assertEquals(0.9f, out[Prefs.KEY_TOP_P] as Float, 1e-6f)
        assertEquals(2.0f, out[Prefs.KEY_TTS_SPEED])
    }

    @Test
    fun maxTokens_staysInt_andStringsStayStrings() {
        val out = roundTrip(
            mapOf(
                Prefs.KEY_MAX_TOKENS to 2048,
                Prefs.KEY_MODEL to "gpt-x",
                Prefs.KEY_THEME to "system",
                Prefs.KEY_ONBOARDED to true
            )
        )
        assertEquals(2048, out[Prefs.KEY_MAX_TOKENS])
        assertEquals("gpt-x", out[Prefs.KEY_MODEL])
        assertEquals("system", out[Prefs.KEY_THEME])
        assertEquals(true, out[Prefs.KEY_ONBOARDED])
    }

    @Test
    fun unknownKey_usesExistingTypeHint() {
        val out = roundTrip(
            mapOf("some_timestamp" to 5L, "some_ratio" to 1.0f),
            hints = mapOf("some_timestamp" to 0L, "some_ratio" to 0.5f)
        )
        assertTrue(out["some_timestamp"] is Long)
        assertTrue(out["some_ratio"] is Float)
    }

    @Test
    fun stringSet_roundTripsAsSet() {
        val out = roundTrip(mapOf("tags" to setOf("a", "b")))
        assertEquals(setOf("a", "b"), out["tags"])
    }

    @Test
    fun secrets_neverExported_norImported() {
        assertTrue(BackupPrefs.isExcluded(Prefs.KEY_API_PLAINTEXT))
        assertTrue(BackupPrefs.isExcluded(Prefs.KEY_API_ENC))
        assertTrue(BackupPrefs.isExcluded("secret_enc_github_pat"))
        assertFalse(BackupPrefs.isExcluded(Prefs.KEY_MODEL))
        // Backup lama/buatan yang memuat rahasia: diabaikan saat restore.
        val raw = JSONObject()
            .put(Prefs.KEY_API_ENC, "xxx")
            .put("secret_enc_telegram", "yyy")
            .put(Prefs.KEY_MODEL, "m")
            .toString()
        val out = BackupPrefs.decode(raw)
        assertEquals(setOf(Prefs.KEY_MODEL), out.keys)
    }

    @Test
    fun jsonNull_meansRemove() {
        val out = BackupPrefs.decode("""{"persona":null}""")
        assertTrue(out.containsKey("persona"))
        assertEquals(null, out["persona"])
    }
}
