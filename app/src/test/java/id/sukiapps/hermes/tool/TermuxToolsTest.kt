package id.sukiapps.hermes.tool

import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Test tool Termux (termux_exec) — masterprompt §5.4: satu test kecil per
 * tool; input valid → hasil benar; input tidak valid → error jelas, bukan
 * crash.
 *
 * Semua test murni JVM (tanpa Android): validasi argumen, format hasil
 * (fungsi murni [TermuxExecTool.formatTermuxResult]), registry, dan
 * round-trip [TermuxResultStore]. Pengiriman intent RUN_COMMAND +
 * penerimaan hasil via PendingIntent butuh Android + aplikasi Termux —
 * jalur itu TIDAK diuji di sini; ia diverifikasi CI + uji tap di HP.
 */
class TermuxToolsTest {

    private fun run(tool: HermesTool, json: String): ToolResult =
        runBlocking { tool.execute(JSONObject(json)) }

    private fun okOutcome(
        stdout: String = "hello\n",
        stderr: String = "",
        exitCode: Int = 0,
        stdoutOriginalLength: String? = null,
        stderrOriginalLength: String? = null,
    ) = TermuxExecOutcome(
        stdout = stdout,
        stderr = stderr,
        exitCode = exitCode,
        stdoutOriginalLength = stdoutOriginalLength,
        stderrOriginalLength = stderrOriginalLength,
        err = -1,
        errmsg = null,
    )

    // ---- validasi argumen (tanpa Android) ----

    @Test
    fun termuxExec_perintahKosong_errorJelasBukanCrash() {
        assertTrue(run(TermuxExecTool(), """{"command": ""}""").isError)
        assertTrue(run(TermuxExecTool(), """{}""").isError)
    }

    @Test
    fun termuxExec_perintahTerlaluPanjang_errorJelas() {
        val panjang = "a".repeat(TermuxExecTool.MAX_COMMAND_CHARS + 1)
        val r = run(
            TermuxExecTool(),
            JSONObject().put("command", panjang).toString(),
        )
        assertTrue(r.isError)
        assertTrue(r.content.contains("terlalu panjang"))
    }

    @Test
    fun termuxExec_tanpaKonteksAndroid_errorJelasBukanCrash() {
        // Tanpa appContext/gate (test JVM), tool gagal tertutup — bukan crash
        // dan bukan mengirim intent ke mana pun.
        val r = run(TermuxExecTool(), """{"command": "ls"}""")
        assertTrue(r.isError)
        assertTrue(r.content.contains("belum diinisialisasi"))
    }

    // ---- format hasil (fungsi murni) ----

    @Test
    fun formatHasil_exitNol_bukanError_tampilkanStdoutStderr() {
        val r = TermuxExecTool.formatTermuxResult(
            okOutcome(stdout = "hello\n", stderr = "warn\n", exitCode = 0),
        )
        assertFalse(r.isError)
        assertTrue(r.content.contains("exit code: 0"))
        assertTrue(r.content.contains("hello"))
        assertTrue(r.content.contains("warn"))
        assertFalse(r.content.contains("bukan 0"))
    }

    @Test
    fun formatHasil_exitBukanNol_bukanErrorTool_tapiExitCodeJelas() {
        val r = TermuxExecTool.formatTermuxResult(
            okOutcome(stdout = "", stderr = "not found\n", exitCode = 127),
        )
        // Perintahnya jalan (tool berhasil) — hanya exit code-nya bukan 0.
        assertFalse(r.isError)
        assertTrue(r.content.contains("exit code: 127"))
        assertTrue(r.content.contains("bukan 0"))
        assertTrue(r.content.contains("not found"))
    }

    @Test
    fun formatHasil_errorInternalTermux_isErrorDenganErrmsg() {
        val r = TermuxExecTool.formatTermuxResult(
            okOutcome().copy(err = 1, errmsg = "gagal memulai perintah"),
        )
        assertTrue(r.isError)
        assertTrue(r.content.contains("gagal memulai perintah"))
    }

    @Test
    fun formatHasil_outputKosong_ditulisJelas() {
        val r = TermuxExecTool.formatTermuxResult(okOutcome(stdout = "", stderr = ""))
        assertFalse(r.isError)
        assertTrue(r.content.contains("(kosong)"))
    }

    @Test
    fun formatHasil_dipotongTermux_diberiCatatan() {
        val r = TermuxExecTool.formatTermuxResult(
            okOutcome(
                stdout = "abc",
                stdoutOriginalLength = "150000",
                stderr = "",
                stderrOriginalLength = "0",
            ),
        )
        assertFalse(r.isError)
        assertTrue(r.content.contains("dipotong Termux"))
        assertTrue(r.content.contains("150000"))
    }

    @Test
    fun formatHasil_dipotongHermes_diberiCatatan() {
        val panjang = "x".repeat(200)
        val r = TermuxExecTool.formatTermuxResult(
            okOutcome(stdout = panjang, stderr = ""),
            maxChars = 100,
        )
        assertFalse(r.isError)
        assertTrue(r.content.contains("dipotong Hermes"))
        assertTrue(r.content.length <= 200)
    }

    // ---- registry + wiring ToolLoop ----

    @Test
    fun registry_mendaftarkanTermuxExec_butuhPersetujuan_kemampuanExecute() {
        val tool = ToolRegistry.find("termux_exec")
        assertTrue("termux_exec harus terdaftar", tool != null)
        tool!!
        assertTrue(tool.requiresApproval)
        assertTrue(tool.capabilities.contains(ToolCapability.EXECUTE))
        assertTrue(tool.description.length > 20)
        assertEquals("object", tool.inputSchema.getString("type"))
        assertTrue(tool.inputSchema.getJSONObject("properties").has("command"))
        assertTrue(tool.inputSchema.getJSONArray("required").getString(0) == "command")
    }

    @Test
    fun toolLoop_toolsArray_memuatTermuxExec_tanpaDuplikat() {
        val names = HashSet<String>()
        val arr = ToolLoop.toolsArray(includeSelfUpgrade = false)
        for (i in 0 until arr.length()) {
            names += arr.getJSONObject(i).getJSONObject("function").getString("name")
        }
        assertTrue(names.contains("termux_exec"))
        assertEquals(arr.length(), names.size)
    }

    // ---- TermuxResultStore round-trip (JVM) ----

    @Test
    fun resultStore_registerCompleteAwait_roundTrip() {
        val id = TermuxResultStore.register()
        val outcome = okOutcome(stdout = "ok\n", exitCode = 0)
        assertTrue(TermuxResultStore.complete(id, outcome))
        val got = runBlocking { TermuxResultStore.await(id) }
        assertEquals("ok\n", got.stdout)
        assertEquals(0, got.exitCode)
        // Hasil kedua untuk id yang sama sudah tidak ada (dihapus saat complete).
        assertFalse(TermuxResultStore.complete(id, outcome))
    }

    @Test
    fun resultStore_cancel_menghapusEntri() {
        val id = TermuxResultStore.register()
        TermuxResultStore.cancel(id)
        assertFalse(TermuxResultStore.complete(id, okOutcome()))
    }
}
