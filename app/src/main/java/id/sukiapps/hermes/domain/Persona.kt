package id.sukiapps.hermes.domain

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * Persona preset — format JSON KONTRAK: [{"n":nama,"t":teks}]
 * (dipertahankan dari legacy Ui.personaPresets).
 */
@Serializable
data class PersonaPreset(
    val n: String,
    val t: String
)

object Persona {
    private val json = Json { ignoreUnknownKeys = true }
    private val listSer = ListSerializer(PersonaPreset.serializer())

    fun defaults(): List<PersonaPreset> = listOf(
        PersonaPreset(
            "Asisten Umum",
            "Kamu adalah asisten AI yang membantu, menjawab dengan jelas dan ringkas dalam Bahasa Indonesia."
        ),
        PersonaPreset(
            "Programmer",
            "Kamu adalah programmer senior. Jawab dengan kode yang benar dan penjelasan singkat dalam Bahasa Indonesia."
        ),
        PersonaPreset(
            "Penulis",
            "Kamu adalah penulis kreatif. Gunakan gaya yang hidup namun tetap jelas dalam Bahasa Indonesia."
        )
    )

    /** Parse JSON presets; fallback ke defaults bila kosong/rusak. */
    fun parse(raw: String?): List<PersonaPreset> {
        if (raw.isNullOrBlank()) return defaults()
        return try {
            val list = json.decodeFromString(listSer, raw)
                .filter { it.n.isNotBlank() && it.t.isNotBlank() }
            if (list.isEmpty()) defaults() else list
        } catch (e: Exception) {
            defaults()
        }
    }

    fun toJson(list: List<PersonaPreset>): String =
        json.encodeToString(listSer, list)
}
