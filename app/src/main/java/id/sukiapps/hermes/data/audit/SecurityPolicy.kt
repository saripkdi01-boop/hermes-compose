package id.sukiapps.hermes.data.audit

/**
 * SecurityPolicy — guard konten + allowlist untuk write GitHub (v14.9 hardening).
 *
 * Dipanggil chokepoint [id.sukiapps.hermes.data.connectors.GitHubConnector.putContents]
 * SEBELUM token PAT tersentuh ke endpoint GitHub. Return null = lolos.
 * Return non-null = alasan blok (connector lempar Result.failure).
 *
 * Asimetri sengaja: file KONSEPSI (logika, konfigurasi, UI) boleh ditulis,
 * file EKSEKUSI (CI workflow, skrip shell, biner, keystore) diblokir.
 */
object SecurityPolicy {

    /** Repo yang boleh disentuh jalur self-upgrade. */
    private val ALLOWED_OWNER_REPO: Set<String> = setOf(
        "saripkdi01-boop/hermes-compose"
    )

    /** Path yang tidak boleh ditulis otomatis — CI + aset eksekusi. */
    private val BLOCKED_EXT = setOf(
        ".sh", ".bash", ".zsh", ".apk", ".aab",
        ".keystore", ".jks", ".p12", ".pem", ".key",
        ".bin", ".so", ".exe", ".dll", ".bat", ".cmd", ".ps1"
    )

    /**
     * Pola konten berbahaya. Sengaja spesifik supaya kode patch yang
     * membahas nama pola ("curl", "sh") sebagai literal string tidak
     * mengunci diri sendiri.
     */
    private val DANGEROUS_PATTERNS = listOf(
        Regex("""curl[^\n|]*\|[^\n]*(?:ba)?sh""", RegexOption.IGNORE_CASE),
        Regex("""wget[^\n|]*\|[^\n]*(?:ba)?sh""", RegexOption.IGNORE_CASE),
        Regex("""base64\s+-d[^\n|]*\|[^\n]*(?:ba)?sh""", RegexOption.IGNORE_CASE),
        Regex("""rm\s+-rf\s+/(?:\s|$)"""),
        Regex("""(?:ba)?sh\s+-c\s+.*curl""", RegexOption.IGNORE_CASE),
        Regex("""nc\s+-e""", RegexOption.IGNORE_CASE),
        Regex("""/dev/tcp/""", RegexOption.IGNORE_CASE)
    )

    /**
     * Evaluasi apakah write GitHub harus diblokir.
     *
     * @param owner GitHub owner (mis. "saripkdi01-boop")
     * @param repo nama repo (mis. "hermes-compose")
     * @param path path file di repo (mis. "app/build.gradle.kts")
     * @param content isi file yang akan di-push
     * @return null kalau boleh; string alasan kalau diblokir.
     */
    fun blockedReason(
        owner: String,
        repo: String,
        path: String,
        content: String
    ): String? {
        // 1. Allowlist owner/repo — jalur self-upgrade hanya repo sendiri.
        val full = "$owner/$repo".lowercase()
        if (full !in ALLOWED_OWNER_REPO) {
            return "Diblokir SecurityPolicy: $full tidak ada di allowlist self-upgrade."
        }

        // 2. Path CI — supply-chain vector paling mahal.
        val p = path.lowercase().trim()
        if (p.startsWith(".github/")) {
            return "Diblokir SecurityPolicy: .github/ (CI workflow) tidak boleh ditulis otomatis."
        }

        // 3. Ekstensi eksekusi.
        val ext = p.substringAfterLast('.', "")
        if (ext.isNotEmpty() && ".$ext" in BLOCKED_EXT) {
            return "Diblokir SecurityPolicy: ekstensi .$ext terlarang (aset eksekusi)."
        }

        // 4. Konten berbahaya.
        val head = content.take(200_000)
        for (re in DANGEROUS_PATTERNS) {
            if (re.containsMatchIn(head)) {
                return "Diblokir SecurityPolicy: konten cocok pola berbahaya (${re.pattern.take(40)}…)."
            }
        }

        // 5. Ukuran — GitHub contents API max 1MB; batasi aman 800KB.
        if (content.toByteArray(Charsets.UTF_8).size > 800_000) {
            return "Diblokir SecurityPolicy: file >800KB, pakai Git Data API."
        }

        return null
    }

    /**
     * Varian longgar untuk write MULTI-FILE: batch dianggap aman hanya
     * kalau SEMUA file lolos. Return daftar path yang diblokir (kosong = aman).
     */
    fun blockedBatch(
        owner: String,
        repo: String,
        files: List<Pair<String, String>>
    ): List<String> =
        files.filter { (path, content) ->
            blockedReason(owner, repo, path, content) != null
        }.map { it.first }
}
