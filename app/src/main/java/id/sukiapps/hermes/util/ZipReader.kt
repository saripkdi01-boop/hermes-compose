package id.sukiapps.hermes.util

import java.io.File
import java.util.zip.ZipFile

/**
 * Ekstraksi ZIP untuk lampiran chat.
 * Membaca file teks di dalam ZIP dan mengembalikan ringkasannya
 * untuk dikirim sebagai konteks ke AI.
 */
object ZipReader {

    /** Batas ukuran per file teks yang dibaca (512 KB). */
    private const val MAX_FILE_BYTES = 512 * 1024

    /** Batas total konten yang dikembalikan (2 MB). */
    private const val MAX_TOTAL_BYTES = 2 * 1024 * 1024

    /** Ekstensi yang dianggap file teks dan aman dibaca. */
    private val TEXT_EXTENSIONS = setOf(
        "kt", "java", "xml", "gradle", "kts", "json", "md",
        "txt", "properties", "yaml", "yml", "toml", "csv"
    )

    data class ZipContent(
        val fileCount: Int,
        val textFiles: List<TextFile>,
        val skipped: List<String>,
        val summary: String
    )

    data class TextFile(
        val path: String,
        val content: String,
        val truncated: Boolean
    )

    /**
     * Baca ZIP dan kembalikan konten file teks di dalamnya.
     * @param zipFile file ZIP yang sudah tersimpan lokal.
     */
    fun read(zipFile: File): ZipContent {
        val textFiles = mutableListOf<TextFile>()
        val skipped = mutableListOf<String>()
        var totalBytes = 0
        var fileCount = 0

        ZipFile(zipFile).use { zip ->
            val entries = zip.entries().asSequence()
                .filter { !it.isDirectory }
                .toList()
            fileCount = entries.size

            for (entry in entries) {
                if (totalBytes >= MAX_TOTAL_BYTES) {
                    skipped.add("... (batas total tercapai)")
                    break
                }
                val name = entry.name
                val ext = name.substringAfterLast('.', "").lowercase()

                // Skip file binary / tidak dikenal.
                if (ext !in TEXT_EXTENSIONS) {
                    skipped.add(name)
                    continue
                }
                // Cegah zip-slip: tolak path yang keluar direktori.
                if (name.contains("..")) {
                    skipped.add("$name (path tidak aman)")
                    continue
                }

                try {
                    zip.getInputStream(entry).use { input ->
                        val bytes = input.readBytes()
                        val truncated = bytes.size > MAX_FILE_BYTES
                        val content = String(
                            if (truncated) bytes.copyOf(MAX_FILE_BYTES) else bytes,
                            Charsets.UTF_8
                        )
                        totalBytes += content.length
                        textFiles.add(TextFile(name, content, truncated))
                    }
                } catch (e: Exception) {
                    skipped.add("$name (gagal baca: ${e.message})")
                }
            }
        }

        val summary = buildString {
            appendLine("ZIP: ${zipFile.name}")
            appendLine("Total file: $fileCount, file teks dibaca: ${textFiles.size}")
            if (skipped.isNotEmpty()) {
                appendLine("Dilewati (${skipped.size}): ${skipped.take(10).joinToString(", ")}")
            }
        }

        return ZipContent(fileCount, textFiles, skipped, summary.trim())
    }

    /**
     * Format konten untuk dikirim sebagai konteks ke AI.
     */
    fun formatForAi(content: ZipContent): String {
        return buildString {
            appendLine(content.summary)
            appendLine()
            appendLine("=== ISI FILE ===")
            for (f in content.textFiles) {
                appendLine()
                appendLine("--- ${f.path} ---")
                appendLine(f.content)
                if (f.truncated) appendLine("[... dipotong, file asli lebih besar]")
            }
        }
    }
}
