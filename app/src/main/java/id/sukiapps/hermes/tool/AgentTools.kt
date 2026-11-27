package id.sukiapps.hermes.tool

import id.sukiapps.hermes.data.agent.AgentFiles
import id.sukiapps.hermes.data.agent.SkillStore
import id.sukiapps.hermes.data.agent.TodoMd
import id.sukiapps.hermes.data.audit.DangerLevel
import id.sukiapps.hermes.data.local.MessageHit
import id.sukiapps.hermes.data.remote.SimpleCompletion
import id.sukiapps.hermes.data.store.ExecutionLog
import id.sukiapps.hermes.data.tool.CatalogToolRunner
import id.sukiapps.hermes.data.work.CronJobStore
import id.sukiapps.hermes.data.work.CronScheduler
import id.sukiapps.hermes.data.work.CronSpec
import id.sukiapps.hermes.di.agentDeps
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.time.ZonedDateTime
import java.util.Date
import java.util.Locale

/*
 * v20.1 — "Hermes Agent toolkit" untuk Android.
 *
 * Padanan tool inti Hermes Agent desktop (Nous Research, MIT) yang masuk akal
 * di HP, diimplementasikan ulang secara native di atas fondasi Hermes Compose:
 *   todo           → todo.md Jiwa Hermes (bertahan antar sesi)
 *   session_search → cari riwayat chat (Room, tanpa incognito)
 *   skills_list / skill_view / skill_manage → skill Markdown di filesDir
 *   execute_code   → Python 3.11 on-device (Chaquopy)
 *   cronjob        → tugas terjadwal (WorkManager + model + notifikasi)
 *   delegate_task  → sub-agen paralel (≤ 3) tanpa tool
 * Semua: satu interface HermesTool, satu baris di ToolRegistry, izin lewat
 * PermissionGate (otomatis tercatat di AuditLog), gagal tertutup (ToolResult
 * error, tidak pernah throw), output dibatasi.
 */

private fun schema(props: JSONObject, required: List<String> = emptyList()): JSONObject =
    JSONObject().put("type", "object").put("properties", props).put("required", JSONArray(required))

private fun p(type: String, desc: String): JSONObject = JSONObject().put("type", type).put("description", desc)

private fun pEnum(desc: String, vararg values: String): JSONObject =
    p("string", desc).put("enum", JSONArray(values.toList()))

private fun notReady(id: String) = ToolResult("Error: $id belum siap (konteks aplikasi belum dipasang).", true)

private const val MAX_OUT = 12_000

// ───────────────────────────── todo ─────────────────────────────

class TodoTool : HermesTool {
    override val id = "todo"
    override val name = "Daftar Tugas"
    override val description =
        "Kelola daftar tugas user (todo.md Jiwa Hermes, bertahan antar sesi). " +
            "action: list | add (text) | done (number) | undo (number) | remove (number). " +
            "Pakai untuk merencanakan pekerjaan multi-langkah dan mencentang yang selesai."
    override val inputSchema = schema(
        JSONObject()
            .put("action", pEnum("Aksi", "list", "add", "done", "undo", "remove"))
            .put("text", p("string", "Teks tugas (untuk add)"))
            .put("number", p("integer", "Nomor tugas dari list (untuk done/undo/remove)")),
        listOf("action")
    )
    override val capabilities = setOf(ToolCapability.READ_ONLY, ToolCapability.WRITE_FILE)
    override val requiresApproval = true

    override suspend fun execute(args: JSONObject, ctx: ToolContext): ToolResult {
        val app = ctx.appContext ?: return notReady(id)
        val gate = ctx.gate ?: return notReady(id)
        return try {
            val action = args.optString("action").trim()
            val md = AgentFiles.read(app, AgentFiles.TODO)
            if (action == "list") {
                return ToolResult(TodoMd.render(TodoMd.items(md)))
            }
            val number = args.optInt("number", -1)
            val next = when (action) {
                "add" -> TodoMd.add(md, args.optString("text"))
                "done" -> TodoMd.setDone(md, number, true)
                "undo" -> TodoMd.setDone(md, number, false)
                "remove" -> TodoMd.remove(md, number)
                else -> return ToolResult("Error: action '$action' tidak dikenal", true)
            }
            val detail = when (action) {
                "add" -> "Tambah tugas: ${args.optString("text").take(120)}"
                else -> "$action tugas #$number"
            }
            if (!gate.request("Todo", detail, DangerLevel.LOW)) {
                return ToolResult("Ditolak: user tidak mengizinkan perubahan daftar tugas.", true)
            }
            AgentFiles.write(app, AgentFiles.TODO, next)
            ExecutionLog.log("tool", "todo $action", detail, "ok")
            ToolResult("OK.\n" + TodoMd.render(TodoMd.items(next)))
        } catch (e: IllegalArgumentException) {
            ToolResult("Error: ${e.message}", true)
        } catch (e: Exception) {
            ToolResult("Error: ${e.javaClass.simpleName}: ${e.message}", true)
        }
    }
}

// ───────────────────────── session_search ─────────────────────────

/** Bagian murni session_search (escape LIKE + format hasil) — di-unit-test. */
object SessionSearch {
    fun escapeLike(q: String): String =
        q.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")

    /** Cuplikan ±[radius] karakter di sekitar kecocokan pertama (case-insensitive). */
    fun snippet(content: String, query: String, radius: Int = 80): String {
        val flat = content.replace(Regex("\\s+"), " ").trim()
        val i = flat.lowercase().indexOf(query.lowercase())
        if (i < 0) return flat.take(radius * 2)
        val start = (i - radius).coerceAtLeast(0)
        val end = (i + query.length + radius).coerceAtMost(flat.length)
        return (if (start > 0) "…" else "") + flat.substring(start, end) + (if (end < flat.length) "…" else "")
    }

    fun format(hits: List<MessageHit>, query: String, maxConvs: Int = 6, perConv: Int = 3): String {
        if (hits.isEmpty()) return "Tidak ada pesan yang cocok dengan \"$query\"."
        val fmt = SimpleDateFormat("d MMM yyyy HH:mm", Locale("id", "ID"))
        return hits.groupBy { it.convId }.entries.take(maxConvs).joinToString("\n\n") { (_, list) ->
            val head = list.first()
            "## ${head.title} (chat #${head.convId}, ${fmt.format(Date(head.createdAt))})\n" +
                list.take(perConv).joinToString("\n") { h ->
                    "- ${if (h.role == "user") "User" else "Hermes"}: ${snippet(h.content, query)}"
                }
        }.take(MAX_OUT)
    }
}

class SessionSearchTool : HermesTool {
    override val id = "session_search"
    override val name = "Cari Riwayat Chat"
    override val description =
        "Cari kata/frasa di riwayat percakapan user sebelumnya (tidak termasuk chat incognito). " +
            "Pakai saat user merujuk obrolan lama (\"yang kemarin kita bahas…\")."
    override val inputSchema = schema(
        JSONObject()
            .put("query", p("string", "Kata kunci (min 2 karakter)"))
            .put("limit", p("integer", "Maks pesan (default 30, maks 100)")),
        listOf("query")
    )
    override val capabilities = setOf(ToolCapability.READ_ONLY)

    override suspend fun execute(args: JSONObject, ctx: ToolContext): ToolResult {
        val app = ctx.appContext ?: return notReady(id)
        val q = args.optString("query").trim()
        if (q.length < 2) return ToolResult("Error: query minimal 2 karakter", true)
        return try {
            val hits = agentDeps(app).chatRepository()
                .searchMessages(q.take(100), args.optInt("limit", 30).coerceIn(1, 100))
            ExecutionLog.log("tool", "session_search", "\"${q.take(60)}\" → ${hits.size} pesan", "ok")
            ToolResult(SessionSearch.format(hits, q))
        } catch (e: Exception) {
            ToolResult("Error: ${e.javaClass.simpleName}: ${e.message}", true)
        }
    }
}

// ───────────────────────────── skills ─────────────────────────────

class SkillsListTool : HermesTool {
    override val id = "skills_list"
    override val name = "Daftar Skill"
    override val description = "Daftar skill tersimpan (nama + deskripsi). Buka isi dengan skill_view."
    override val inputSchema = schema(JSONObject())
    override val capabilities = setOf(ToolCapability.READ_ONLY)

    override suspend fun execute(args: JSONObject, ctx: ToolContext): ToolResult {
        val app = ctx.appContext ?: return notReady(id)
        return runCatching {
            SkillStore.forApp(app.filesDir).index(max = 50).ifBlank { "(belum ada skill tersimpan)" }
        }.fold({ ToolResult(it) }, { ToolResult("Error: ${it.message}", true) })
    }
}

class SkillViewTool : HermesTool {
    override val id = "skill_view"
    override val name = "Buka Skill"
    override val description = "Baca isi lengkap satu skill tersimpan (langkah kerja yang pernah berhasil)."
    override val inputSchema = schema(JSONObject().put("name", p("string", "Nama skill")), listOf("name"))
    override val capabilities = setOf(ToolCapability.READ_ONLY)

    override suspend fun execute(args: JSONObject, ctx: ToolContext): ToolResult {
        val app = ctx.appContext ?: return notReady(id)
        return runCatching {
            val s = SkillStore.forApp(app.filesDir).get(args.optString("name").trim())
                ?: return ToolResult("Error: skill tidak ditemukan", true)
            "# ${s.name}\n${s.description}\n\n${s.body}".take(MAX_OUT)
        }.fold({ ToolResult(it) }, { ToolResult("Error: ${it.message}", true) })
    }
}

class SkillManageTool : HermesTool {
    override val id = "skill_manage"
    override val name = "Kelola Skill"
    override val description =
        "Simpan cara kerja yang berhasil sebagai skill agar bisa dipakai lagi. " +
            "action: create (name, description, content) | edit (name, description?, content?) | " +
            "rename (name, new_name) | delete (name). Nama: huruf kecil/angka/strip."
    override val inputSchema = schema(
        JSONObject()
            .put("action", pEnum("Aksi", "create", "edit", "rename", "delete"))
            .put("name", p("string", "Nama skill, mis. rilis-apk"))
            .put("new_name", p("string", "Nama baru (rename)"))
            .put("description", p("string", "Deskripsi satu kalimat"))
            .put("content", p("string", "Isi skill (Markdown, ≤ 12 KB)")),
        listOf("action", "name")
    )
    override val capabilities = setOf(ToolCapability.WRITE_FILE)
    override val requiresApproval = true

    override suspend fun execute(args: JSONObject, ctx: ToolContext): ToolResult {
        val app = ctx.appContext ?: return notReady(id)
        val gate = ctx.gate ?: return notReady(id)
        val action = args.optString("action").trim()
        val name = args.optString("name").trim()
        val detail = "Skill $action: $name" +
            (args.optString("description").takeIf { it.isNotBlank() }?.let { "\n$it" } ?: "")
        if (!gate.request("Skill", detail, DangerLevel.MEDIUM)) {
            return ToolResult("Ditolak: user tidak mengizinkan perubahan skill.", true)
        }
        return try {
            val store = SkillStore.forApp(app.filesDir)
            val msg = when (action) {
                "create" -> store.create(name, args.optString("description"), args.optString("content")).let { "Skill '${it.name}' dibuat." }
                "edit" -> store.edit(
                    name,
                    args.optString("description").takeIf { args.has("description") },
                    args.optString("content").takeIf { args.has("content") }
                ).let { "Skill '${it.name}' diperbarui." }
                "rename" -> store.rename(name, args.optString("new_name").trim()).let { "Skill diganti nama menjadi '${it.name}'." }
                "delete" -> { store.delete(name); "Skill '$name' dihapus." }
                else -> return ToolResult("Error: action '$action' tidak dikenal", true)
            }
            ExecutionLog.log("tool", "skill_manage $action", name, "ok")
            ToolResult(msg)
        } catch (e: IllegalArgumentException) {
            ToolResult("Error: ${e.message}", true)
        } catch (e: Exception) {
            ToolResult("Error: ${e.javaClass.simpleName}: ${e.message}", true)
        }
    }
}

// ─────────────────────────── execute_code ───────────────────────────

class ExecuteCodeTool : HermesTool {
    override val id = "execute_code"
    override val name = "Jalankan Python"
    override val description =
        "Jalankan kode Python 3.11 (stdlib) di HP lewat interpreter on-device untuk hitungan, " +
            "olah teks/data, atau konversi. Tampilkan hasil dengan print(). Timeout 30 detik, " +
            "output maks 20.000 karakter. Tidak bisa memanggil tool lain. Selalu minta izin user."
    override val inputSchema = schema(JSONObject().put("code", p("string", "Kode Python")), listOf("code"))
    override val capabilities = setOf(ToolCapability.EXECUTE)
    override val requiresApproval = true

    override suspend fun execute(args: JSONObject, ctx: ToolContext): ToolResult {
        val app = ctx.appContext ?: return notReady(id)
        val gate = ctx.gate ?: return notReady(id)
        val code = args.optString("code")
        if (code.isBlank()) return ToolResult("Error: parameter 'code' wajib diisi", true)
        if (code.length > 20_000) return ToolResult("Error: kode terlalu panjang (maks 20.000 karakter)", true)
        // HIGH: kode berjalan di dalam proses aplikasi (bisa membaca file
        // aplikasi & memakai internet) — selalu popup, tanpa "ingat".
        val detail = "Python on-device (berjalan di dalam aplikasi, bisa akses file app & internet):\n" +
            code.take(600) + if (code.length > 600) "\n…" else ""
        if (!gate.request("Python", detail, DangerLevel.HIGH)) {
            return ToolResult("Ditolak: user tidak mengizinkan menjalankan kode.", true)
        }
        return when (val r = CatalogToolRunner.runCode(code, app)) {
            is CatalogToolRunner.RunResult.Success -> {
                ExecutionLog.log("tool", "execute_code", "${r.output.length} karakter", "ok")
                ToolResult(r.output.ifBlank { "(selesai tanpa output — pakai print())" })
            }
            is CatalogToolRunner.RunResult.Failed -> {
                ExecutionLog.log("tool", "execute_code", r.error.take(120), "fail")
                ToolResult("Error Python:\n${r.error}", true)
            }
            CatalogToolRunner.RunResult.TimedOut -> ToolResult("Error: timeout 30 detik", true)
        }
    }
}

// ───────────────────────────── cronjob ─────────────────────────────

class CronJobTool : HermesTool {
    override val id = "cronjob"
    override val name = "Tugas Terjadwal"
    override val description =
        "Jadwalkan prompt agar dijalankan Hermes otomatis di latar; hasil masuk chat \"⏰ nama\" + notifikasi. " +
            "action: create (name, prompt, schedule) | list | pause (id) | resume (id) | remove (id) | run (id). " +
            "schedule: 'setiap 30 menit' (min 15), 'setiap 2 jam', 'setiap hari 07:30', 'sekali 2026-11-28 09:00'. " +
            "Memakai model & kuota API user — konfirmasi dulu ke user."
    override val inputSchema = schema(
        JSONObject()
            .put("action", pEnum("Aksi", "create", "list", "pause", "resume", "remove", "run"))
            .put("id", p("string", "Id tugas (dari list)"))
            .put("name", p("string", "Nama singkat tugas"))
            .put("prompt", p("string", "Instruksi yang dijalankan tiap jadwal"))
            .put("schedule", p("string", "Jadwal, lihat deskripsi")),
        listOf("action")
    )
    override val capabilities = setOf(ToolCapability.NETWORK, ToolCapability.WRITE_FILE)
    override val requiresApproval = true

    override suspend fun execute(args: JSONObject, ctx: ToolContext): ToolResult {
        val app = ctx.appContext ?: return notReady(id)
        val gate = ctx.gate ?: return notReady(id)
        val store = CronJobStore.forApp(app.filesDir)
        val action = args.optString("action").trim()
        val jobId = args.optString("id").trim()
        return try {
            when (action) {
                "list" -> ToolResult(
                    store.all().ifEmpty { return ToolResult("(belum ada tugas terjadwal)") }
                        .joinToString("\n") { j ->
                            "- ${j.id} · ${j.name} · ${runCatching { CronSpec.parse(j.schedule).describe() }.getOrDefault(j.schedule)}" +
                                (if (j.paused) " · DIJEDA" else "") +
                                (if (j.lastStatus.isNotBlank()) " · terakhir: ${j.lastStatus}" else "")
                        }
                )
                "create" -> {
                    val spec = CronSpec.parse(args.optString("schedule"))
                    require(!spec.isInPast(ZonedDateTime.now())) { "waktu 'sekali' sudah lewat" }
                    val prompt = args.optString("prompt").trim()
                    require(prompt.isNotEmpty()) { "prompt wajib diisi" }
                    val name = args.optString("name").trim()
                    // HIGH: tugas berjalan di latar memakai kuota API user.
                    val ok = gate.request(
                        "Jadwal",
                        "Buat tugas terjadwal \"${name.ifBlank { prompt.take(40) }}\" — ${spec.describe()}.\n" +
                            "Berjalan di latar memakai model & kuota API kamu.\nPrompt: ${prompt.take(300)}",
                        DangerLevel.HIGH
                    )
                    if (!ok) return ToolResult("Ditolak: user tidak mengizinkan tugas terjadwal.", true)
                    val job = store.add(name, prompt, spec.describe(), System.currentTimeMillis())
                    CronScheduler.schedule(app, job)
                    ExecutionLog.log("tool", "cronjob create", "${job.name} · ${spec.describe()}", "ok")
                    ToolResult("Tugas dibuat: ${job.id} · ${job.name} · ${spec.describe()}")
                }
                "pause", "resume", "remove", "run" -> {
                    val job = store.get(jobId) ?: return ToolResult("Error: tugas '$jobId' tidak ada", true)
                    val level = if (action == "run") DangerLevel.MEDIUM else DangerLevel.LOW
                    if (!gate.request("Jadwal", "$action tugas ${job.name}", level)) {
                        return ToolResult("Ditolak oleh user.", true)
                    }
                    when (action) {
                        "pause" -> { store.update(jobId) { it.copy(paused = true) }; CronScheduler.cancel(app, jobId) }
                        "resume" -> CronScheduler.schedule(app, store.update(jobId) { it.copy(paused = false) })
                        "remove" -> { CronScheduler.cancel(app, jobId); store.remove(jobId) }
                        "run" -> CronScheduler.runNow(app, jobId)
                    }
                    ExecutionLog.log("tool", "cronjob $action", job.name, "ok")
                    ToolResult("OK: $action ${job.name}" + if (action == "run") " (hasil masuk chat \"⏰ ${job.name}\")" else "")
                }
                else -> ToolResult("Error: action '$action' tidak dikenal", true)
            }
        } catch (e: IllegalArgumentException) {
            ToolResult("Error: ${e.message}", true)
        } catch (e: Exception) {
            ToolResult("Error: ${e.javaClass.simpleName}: ${e.message}", true)
        }
    }
}

// ─────────────────────────── delegate_task ───────────────────────────

/** Bagian murni delegate_task: normalisasi argumen (1–3 tugas). */
object DelegateArgs {
    data class Task(val goal: String, val context: String)

    fun parse(args: JSONObject): List<Task> {
        val arr = args.optJSONArray("tasks")
        val list = if (arr != null) {
            (0 until arr.length()).mapNotNull { i ->
                when (val v = arr.opt(i)) {
                    is JSONObject -> Task(v.optString("goal").trim(), v.optString("context").trim())
                    is String -> Task(v.trim(), "")
                    else -> null
                }
            }
        } else {
            listOf(Task(args.optString("goal").trim(), args.optString("context").trim()))
        }
        val clean = list.filter { it.goal.isNotEmpty() }
        require(clean.isNotEmpty()) { "minimal satu tugas (goal)" }
        require(clean.size <= 3) { "maksimal 3 sub-agen sekaligus" }
        return clean.map { Task(it.goal.take(1_000), it.context.take(4_000)) }
    }
}

class DelegateTaskTool : HermesTool {
    override val id = "delegate_task"
    override val name = "Sub-agen"
    override val description =
        "Bagi pekerjaan ke 1–3 sub-agen yang berjalan PARALEL dengan konteks terpisah (tanpa tool), " +
            "mis. bandingkan 3 opsi, ringkas 3 dokumen. Input: goal + context, atau tasks: " +
            "[{goal, context}] (maks 3). Hasil tiap sub-agen dikembalikan untuk kamu rangkum."
    override val inputSchema = schema(
        JSONObject()
            .put("goal", p("string", "Tujuan (bila satu tugas)"))
            .put("context", p("string", "Konteks/bahan yang dibutuhkan sub-agen"))
            .put(
                "tasks",
                JSONObject().put("type", "array").put("description", "Daftar tugas (maks 3)")
                    .put(
                        "items", JSONObject().put("type", "object").put(
                            "properties",
                            JSONObject().put("goal", p("string", "Tujuan")).put("context", p("string", "Konteks"))
                        )
                    )
            )
    )
    override val capabilities = setOf(ToolCapability.NETWORK)
    override val requiresApproval = true

    override suspend fun execute(args: JSONObject, ctx: ToolContext): ToolResult {
        val app = ctx.appContext ?: return notReady(id)
        val gate = ctx.gate ?: return notReady(id)
        val tasks = try {
            DelegateArgs.parse(args)
        } catch (e: IllegalArgumentException) {
            return ToolResult("Error: ${e.message}", true)
        }
        if (!gate.request("Sub-agen", "Jalankan ${tasks.size} sub-agen (memakai kuota API):\n" +
                tasks.joinToString("\n") { "• ${it.goal.take(100)}" }, DangerLevel.LOW)
        ) return ToolResult("Ditolak oleh user.", true)
        val deps = agentDeps(app)
        val prefs = deps.appPrefs()
        val system = "Kamu sub-agen Hermes. Kerjakan SATU tugas berikut secara mandiri, dalam bahasa " +
            "Indonesia, dan laporkan hasil akhir yang padat (maks ±250 kata). Kamu tidak punya tool " +
            "dan tidak bisa bertanya balik; nyatakan asumsi bila perlu. Jangan mengarang fakta."
        val results = coroutineScope {
            tasks.mapIndexed { i, t ->
                async {
                    val user = t.goal + if (t.context.isNotBlank()) "\n\nKonteks:\n${t.context}" else ""
                    val r = withTimeoutOrNull(90_000) {
                        runCatching {
                            SimpleCompletion.complete(deps.okHttpClient(), prefs.baseUrl, prefs.model, system, user, 700)
                        }
                    }
                    val text = when {
                        r == null -> "(gagal: timeout 90 detik)"
                        r.isFailure -> "(gagal: ${r.exceptionOrNull()?.message?.take(120)})"
                        else -> r.getOrThrow()
                    }
                    "### Sub-agen ${i + 1}: ${t.goal.take(80)}\n$text"
                }
            }.awaitAll()
        }
        ExecutionLog.log("tool", "delegate_task", "${tasks.size} sub-agen", "ok")
        return ToolResult(results.joinToString("\n\n").take(MAX_OUT))
    }
}
