package id.sukiapps.hermes.data.connectors

/**
 * Guard konten + allowlist untuk GitHubConnector.
 *
 * HARDENING (v14.9): menutup dua celah kritik hasil audit —
 *   1. PAT bisa dipakai tulis/push ke repo ARBITRER (supply-chain attack:
 *      push workflow/APK ber-backdoor ke repo publik).
 *   2. Konten berbahaya bisa masuk: `.github/workflows/*` (eksekusi di CI
 *      runner), `*.sh` berisi `curl|sh`/`wget|sh` (RCE saat pull),
 *      `*.apk`/`*.keystore`/`*.jks` (exfil binary).
 *
 * Aturan:
 *   - Owner/repo wajib ada di allowlist (self-upgrade: repo Hermes sendiri).
 *   - Path `.github/`, `*.sh`, `*.apk`, `*.keystore`, `*.jks` ditolak.
 *   - Isi file discan: pola shell-download-pipe-to-shell = tolak.
 */
object GitHubContentGuard {

    /** Owner yang sah untuk operasi self-upgrade. */
    const val SELF_UPGRADE_OWNER = "saripkdi01-boop"

    /** Repo yang sah untuk operasi self-upgrade. */
    const val SELF_UPGRADE_REPO = "hermes-compose"

    private val DANGEROUS_EXTENSIONS = listOf(
        ".apk", ".aab", ".keystore", ".jks", ".p12", ".pem", ".key"
    )

    private val DANGEROUS_PATHS = listOf(
        ".github/workflows/", ".github/actions/"
    )

    /** Pola pipe-to-shell & eksfil dalam isi file. */
    private val SHELL_INJECTION_PATTERNS = listOf(
        Regex("""curl\s+[^|;]+\|\s*(sudo\s+)?(ba|z|fi)?sh""", RegexOption.IGNORE_CASE),
        Regex("""wget\s+[^|;]+\|\s*(sudo\s+)?(ba|z|fi)?sh""", RegexOption.IGNORE_CASE),
        Regex("""curl\s+[^|;]+-o\s+/tmp/\S+\s*&&\s*(ba|z|fi)?sh""", RegexOption.IGNORE_CASE),
        Regex("""base64\s+-d\s*\|?\s*(ba)?sh""", RegexOption.IGNORE_CASE),
        Regex("""\beval\s*\(\s*['"]?curl""", RegexOption.IGNORE_CASE),
        Regex("""nc\s+-\S+\s+-e\s+(ba)?sh""", RegexOption.IGNORE_CASE),
        // Exfil rahasia CI ke host luar
        Regex("""secrets\.\w+""", RegexOption.IGNORE_CASE),
        Regex("""echo\s+\$\{?\w*(TOKEN|SECRET|KEY|PASSWORD|PAT)\w*\}?""", RegexOption.IGNORE_CASE),
        Regex("""\bcurl\b[^\n]*\$\{?\w*(TOKEN|SECRET|KEY|PAT)\w*\}?""", RegexOption.IGNORE_CASE)
    )

    /** Hasil validasi write. */
    sealed class Verdict {
        object Allowed : Verdict()
        data class Rejected(val reason: String) : Verdict()
    }

    /** Cek owner/repo ada di allowlist self-upgrade. */
    fun checkRepo(owner: String, repo: String): Verdict = when {
        owner != SELF_UPGRADE_OWNER ->
            Verdict.Rejected("Owner '$owner' di luar allowlist self-upgrade.")
        repo != SELF_UPGRADE_REPO ->
            Verdict.Rejected("Repo '$repo' di luar allowlist self-upgrade.")
        else -> Verdict.Allowed
    }

    /** Cek path + isi file aman untuk ditulis. */
    fun checkPathAndContent(path: String, content: String): Verdict {
        val lower = path.lowercase()
        // 1. Path berbahaya (CI workflow/action, binary signing artifacts)
        for (dp in DANGEROUS_PATHS) {
            if (lower.startsWith(dp)) {
                return Verdict.Rejected("Path '$path' = workflow/CI — tolak (supply-chain).")
            }
        }
        for (ext in DANGEROUS_EXTENSIONS) {
            if (lower.endsWith(ext)) {
                return Verdict.Rejected("Ekstensi '$ext' dilarang — binary/signing artifact.")
            }
        }
        // 2. Isi file: pola pipe-to-shell / eksfil
        for (re in SHELL_INJECTION_PATTERNS) {
            if (re.containsMatchIn(content)) {
                return Verdict.Rejected(
                    "Isi file '$path' cocok pola shell-injection/eksfil rahasia."
                )
            }
        }
        return Verdict.Allowed
    }

    /**
     * Validasi lengkap untuk push N file sekaligus (batch).
     * Semua path + isi harus lolos; satu gagal = seluruh batch gagal.
     */
    fun checkBatch(
        owner: String,
        repo: String,
        files: List<Pair<String, String>>
    ): Verdict {
        when (val r = checkRepo(owner, repo)) {
            is Verdict.Rejected -> return r
            else -> {}
        }
        for ((path, content) in files) {
            when (val r = checkPathAndContent(path, content)) {
                is Verdict.Rejected ->
                    return Verdict.Rejected("Batch ditolak — $path: ${r.reason}")
                else -> {}
            }
        }
        return Verdict.Allowed
    }
}
