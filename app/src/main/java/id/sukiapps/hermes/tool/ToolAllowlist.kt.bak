package id.sukiapps.hermes.tool

import android.content.Context

/**
 * ToolAllowlist — daftar domain yang boleh diakses web_fetch.
 *
 * Disimpan di SharedPreferences "hermes_tools" (bukan Room).
 * KOSONG = semua domain publik boleh; SSRF guard di WebFetchTool tetap
 * menolak IP privat/loopback/link-local/CGNAT di semua mode.
 * Bila terisi: hanya domain terdaftar dan subdomainnya yang boleh.
 */
object ToolAllowlist {

    private const val FILE = "hermes_tools"
    private const val KEY = "webfetch_allowlist"

    @Volatile
    private var domains: Set<String> = emptySet()

    fun init(ctx: Context) {
        val raw = ctx.applicationContext
            .getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .getString(KEY, "")
            .orEmpty()
        domains = parse(raw)
    }

    /** Ganti isi allowlist (dipakai editor di Pengaturan). */
    fun setDomains(ctx: Context, list: Collection<String>) {
        val clean = list.map { it.trim().lowercase() }
            .filter { it.isNotEmpty() }
            .toSet()
        domains = clean
        ctx.applicationContext
            .getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, clean.joinToString("\n"))
            .apply()
    }

    fun current(): Set<String> = domains

    fun isAllowed(host: String): Boolean = hostAllowed(host, domains)

    private fun parse(raw: String): Set<String> =
        raw.split('\n')
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() }
            .toSet()

    /** Logika murni (tanpa Android) — bisa di-unit-test di JVM. */
    fun hostAllowed(host: String, allowed: Set<String>): Boolean {
        if (allowed.isEmpty()) return true
        val h = host.trim().lowercase()
        if (h.isEmpty()) return false
        return allowed.any { d -> h == d || h.endsWith(".$d") }
    }
}
