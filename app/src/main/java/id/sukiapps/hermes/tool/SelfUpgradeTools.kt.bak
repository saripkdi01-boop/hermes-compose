package id.sukiapps.hermes.tool

import android.content.Context
import id.sukiapps.hermes.KeyVault
import id.sukiapps.hermes.data.agent.SelfUpgradePrefs
import id.sukiapps.hermes.data.store.ExecutionLog
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * SelfUpgradeTools — tool agen untuk Upgrade Diri Otomatis (14.8,
 * keputusan owner 24 Okt 2026: agen otomatis sampai merge + rilis
 * DRAFT; signing + lampir APK tetap oleh maintainer seperti biasa).
 *
 * Gerbang keamanan (berlapis, semuanya wajib lolos):
 * 1. Toggle SelfUpgradePrefs.enabled — kill switch di Pengaturan >
 *    Agen. Nonaktif = semua tool di sini menolak mentah-mentah.
 * 2. Repo TERKUNCI ke saripkdi01-boop/hermes-compose (repo Hermes
 *    sendiri). Tool ini tidak bisa dipakai ke repo lain.
 * 3. Branch TERKUNCI ke awalan "selfupgrade/" — tulis file hanya ke
 *    branch itu, tidak pernah ke main.
 * 4. Merge DITOLAK bila status CI gabungan bukan "success".
 * 5. Rilis yang dibuat SELALU draft + body wajib memuat baris
 *    "versionCode:NN" (yang dibaca UpdateChecker setelah publish).
 *
 * Berbeda dari ConnectorTools: TIDAK ada popup PermissionGate di
 * sini — itulah arti "penuh otomatis" yang dipilih owner. Sebagai
 * gantinya, setiap aksi dicatat ke ExecutionLog (layar Aktivitas).
 * Kredensial: PAT dari KeyVault, tidak pernah lewat argumen model.
 */
object SelfUpgradeTools {

    const val BRANCH_PREFIX = "selfupgrade/"
    private const val OWNER = "saripkdi01-boop"
    private const val REPO = "hermes-compose"

    suspend fun execute(
        name: String,
        args: JSONObject,
        ctx: Context,
        bag: ConnectorTools.Bag
    ): ToolResult {
        if (!SelfUpgradePrefs.enabled(ctx)) {
            return ToolResult(
                "Error: Upgrade Diri Otomatis nonaktif — aktifkan dulu " +
                    "di Pengaturan > Agen.",
                true
            )
        }
        val pat = KeyVault.getSecret(ctx, "github_pat").orEmpty().trim()
        if (pat.isEmpty()) {
            return ToolResult(
                "Error: PAT GitHub belum diisi (Pengaturan > Konektor).",
                true
            )
        }
        val gh = bag.github
        return try {
            when (name) {
                "selfupgrade_branch" -> {
                    val branch = args.optString("branch", "").trim().ifEmpty {
                        val ts = SimpleDateFormat(
                            "yyyyMMdd-HHmm", Locale.US
                        ).format(Date())
                        "$BRANCH_PREFIX$ts"
                    }
                    if (!branch.startsWith(BRANCH_PREFIX)) {
                        return ToolResult(
                            "Error: branch upgrade-diri wajib berawalan " +
                                BRANCH_PREFIX,
                            true
                        )
                    }
                    gh.createBranch(pat, OWNER, REPO, branch).fold(
                        onSuccess = {
                            ExecutionLog.log(
                                "selfupgrade", "Branch dibuat", branch, "ok"
                            )
                            ToolResult("Branch dibuat: $branch")
                        },
                        onFailure = {
                            ToolResult("Error: ${it.message}", true)
                        }
                    )
                }
                "selfupgrade_write_file" -> {
                    val branch = args.optString("branch", "").trim()
                    val path = args.optString("path", "").trim()
                    val content = args.optString("content", "")
                    val message = args.optString("message", "").trim()
                    if (!branch.startsWith(BRANCH_PREFIX)) {
                        return ToolResult(
                            "Error: tulis upgrade-diri hanya ke branch " +
                                BRANCH_PREFIX,
                            true
                        )
                    }
                    if (path.isEmpty() || message.isEmpty()) {
                        return ToolResult(
                            "Error: parameter 'path' dan 'message' wajib diisi.",
                            true
                        )
                    }
                    gh.putContentsOnBranch(
                        pat, OWNER, REPO, path, content, message, branch
                    ).fold(
                        onSuccess = {
                            ExecutionLog.log(
                                "selfupgrade", "File ditulis",
                                "$branch/$path", "ok"
                            )
                            ToolResult("File ditulis: $branch/$path")
                        },
                        onFailure = {
                            ToolResult("Error: ${it.message}", true)
                        }
                    )
                }
                "selfupgrade_pr" -> {
                    val branch = args.optString("branch", "").trim()
                    val title = args.optString("title", "").trim()
                    val body = args.optString("body", "")
                    if (!branch.startsWith(BRANCH_PREFIX) || title.isEmpty()) {
                        return ToolResult(
                            "Error: 'branch' (awalan $BRANCH_PREFIX) dan " +
                                "'title' wajib diisi.",
                            true
                        )
                    }
                    gh.createPullRequest(
                        pat, OWNER, REPO, branch, title, body
                    ).fold(
                        onSuccess = { pr ->
                            ExecutionLog.log(
                                "selfupgrade", "PR dibuat", "#$pr", "ok"
                            )
                            ToolResult("PR dibuat: #$pr")
                        },
                        onFailure = {
                            ToolResult("Error: ${it.message}", true)
                        }
                    )
                }
                "selfupgrade_ci" -> {
                    val pr = args.optInt("pr", 0)
                    if (pr <= 0) {
                        return ToolResult(
                            "Error: parameter 'pr' (nomor PR) wajib diisi.",
                            true
                        )
                    }
                    val sha = gh.getPullHeadSha(pat, OWNER, REPO, pr)
                        .getOrElse {
                            return ToolResult("Error: ${it.message}", true)
                        }
                    gh.getCombinedStatus(pat, OWNER, REPO, sha).fold(
                        onSuccess = { state ->
                            ToolResult("CI PR #$pr: $state (head $sha)")
                        },
                        onFailure = {
                            ToolResult("Error: ${it.message}", true)
                        }
                    )
                }
                "selfupgrade_merge" -> {
                    val pr = args.optInt("pr", 0)
                    if (pr <= 0) {
                        return ToolResult(
                            "Error: parameter 'pr' (nomor PR) wajib diisi.",
                            true
                        )
                    }
                    val sha = gh.getPullHeadSha(pat, OWNER, REPO, pr)
                        .getOrElse {
                            return ToolResult("Error: ${it.message}", true)
                        }
                    val state = gh.getCombinedStatus(pat, OWNER, REPO, sha)
                        .getOrElse {
                            return ToolResult("Error: ${it.message}", true)
                        }
                    if (state != "success") {
                        return ToolResult(
                            "DITOLAK: CI PR #$pr masih '$state' — merge " +
                                "hanya bila 'success'.",
                            true
                        )
                    }
                    gh.mergePullRequest(pat, OWNER, REPO, pr).fold(
                        onSuccess = {
                            ExecutionLog.log(
                                "selfupgrade", "PR merged", "#$pr", "ok"
                            )
                            ToolResult("PR #$pr merged ke main.")
                        },
                        onFailure = {
                            ToolResult("Error: ${it.message}", true)
                        }
                    )
                }
                "selfupgrade_release" -> {
                    val tag = args.optString("tag", "").trim()
                    val rname = args.optString("name", "").trim()
                    val body = args.optString("body", "")
                    if (tag.isEmpty() || rname.isEmpty()) {
                        return ToolResult(
                            "Error: parameter 'tag' dan 'name' wajib diisi.",
                            true
                        )
                    }
                    if (!body.contains("versionCode:")) {
                        return ToolResult(
                            "Error: body rilis wajib memuat baris " +
                                "'versionCode:NN' (dibaca UpdateChecker).",
                            true
                        )
                    }
                    gh.createReleaseDraft(
                        pat, OWNER, REPO, tag, rname, body
                    ).fold(
                        onSuccess = { url ->
                            ExecutionLog.log(
                                "selfupgrade", "Draft rilis dibuat", tag, "ok"
                            )
                            ToolResult(
                                "Draft rilis dibuat: $url — langkah berikut " +
                                    "oleh maintainer: sign APK dari artifact " +
                                    "CI, lampirkan ke draft, lalu publish. " +
                                    "Setelah publish, update bisa diunduh " +
                                    "dari Cek update."
                            )
                        },
                        onFailure = {
                            ToolResult("Error: ${it.message}", true)
                        }
                    )
                }
                else -> ToolResult(
                    "Error: self-upgrade tool '$name' tidak dikenal", true
                )
            }
        } catch (e: Exception) {
            ToolResult("Error: ${e.javaClass.simpleName}: ${e.message}", true)
        }
    }
}
