package id.sukiapps.hermes.tool

import org.json.JSONObject

/**
 * file_read — baca isi file teks (masterprompt §5.3: `file.read`).
 *
 * ADAPTER, bukan implementasi baru: eksekusi didelegasikan ke
 * [DevTools.execute] ("file_read") supaya SEMUA guard lama tetap berlaku
 * tanpa duplikasi: path HANYA di dalam sandbox `filesDir/terminal-sandbox`
 * (cek canonical — path di luar sandbox ditolak), izin via [PermissionGate]
 * (MEDIUM — user bisa "ingat"), dan log ke ExecutionLog.
 *
 * File yang dilampirkan user di chat (ChatViewModel.attachments) belum
 * otomatis masuk sandbox — pemanggil LLM yang memakai tool ini membaca
 * file sandbox. Menyalin lampiran ke sandbox adalah pekerjaan lanjutan
 * (lihat TOOLS.md); sampai ada, deskripsi tool jujur menyebut "sandbox".
 *
 * `requiresApproval = true` (override): membaca file user tetap butuh
 * persetujuan, sesuai perilaku DevTools yang sudah ada.
 */
class FileReadHermesTool : HermesTool {

    override val id: String = "file_read"
    override val name: String = "Baca File"
    override val description: String =
        "Baca file teks di dalam sandbox terminal aplikasi " +
            "(path relatif terhadap sandbox, mis. \"README.md\"). " +
            "Gunakan saat butuh isi file yang sudah ada di sandbox. " +
            "File di luar sandbox TIDAK bisa dibaca. Maks 20.000 karakter."
    override val inputSchema: JSONObject = toolInputSchema(
        JSONObject().put(
            "path",
            stringProp("Path relatif file di sandbox, mis. \"notes/todo.md\"."),
        ),
        listOf("path"),
    )
    override val capabilities: Set<ToolCapability> = setOf(ToolCapability.READ_ONLY)
    override val requiresApproval: Boolean = true

    override suspend fun execute(args: JSONObject, ctx: ToolContext): ToolResult {
        val path = args.optString("path", "").trim()
        if (path.isEmpty()) {
            return ToolResult("Error: parameter 'path' wajib diisi", true)
        }
        if (path.startsWith("/") || path.startsWith("\\") || path.contains(":")) {
            return ToolResult(
                "Error: path absolut ditolak — pakai path relatif di sandbox: $path",
                true,
            )
        }
        val appCtx = ctx.appContext
        val gate = ctx.gate
        if (appCtx == null || gate == null) {
            return ToolResult(
                "Error: file_read belum diinisialisasi " +
                    "(konteks aplikasi belum dipasang).",
                true,
            )
        }
        return DevTools.execute("file_read", args, appCtx, gate)
    }
}
