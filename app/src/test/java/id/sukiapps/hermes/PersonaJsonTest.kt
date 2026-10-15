package id.sukiapps.hermes

import id.sukiapps.hermes.domain.Persona
import id.sukiapps.hermes.domain.PersonaPreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Unit test JVM untuk format JSON persona presets [{"n":..,"t":..}]. */
class PersonaJsonTest {

    @Test
    fun parse_validJson() {
        val raw = """[{"n":"Asisten","t":"Jadilah asisten."},{"n":"Koder","t":"Tulis kode."}]"""
        val list = Persona.parse(raw)
        assertEquals(2, list.size)
        assertEquals("Asisten", list[0].n)
        assertEquals("Jadilah asisten.", list[0].t)
    }

    @Test
    fun parse_emptyFallsBackToDefaults() {
        val list = Persona.parse("")
        assertTrue(list.isNotEmpty())
        assertEquals(Persona.defaults().size, list.size)
    }

    @Test
    fun parse_nullFallsBackToDefaults() {
        assertEquals(Persona.defaults().size, Persona.parse(null).size)
    }

    @Test
    fun parse_malformedFallsBackToDefaults() {
        assertEquals(Persona.defaults().size, Persona.parse("{{{").size)
    }

    @Test
    fun parse_skipsBlankEntries() {
        val raw = """[{"n":"","t":"x"},{"n":"Ok","t":"Bagus"}]"""
        val list = Persona.parse(raw)
        assertEquals(1, list.size)
        assertEquals("Ok", list[0].n)
    }

    @Test
    fun roundTrip_preservesKeys() {
        val list = listOf(PersonaPreset("A", "teks a"), PersonaPreset("B", "teks b"))
        val json = Persona.toJson(list)
        // Format kontrak: key "n" dan "t".
        assertTrue(json.contains("\"n\""))
        assertTrue(json.contains("\"t\""))
        assertEquals(list, Persona.parse(json))
    }
}
