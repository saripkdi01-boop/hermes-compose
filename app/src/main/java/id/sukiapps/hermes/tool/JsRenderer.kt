package id.sukiapps.hermes.tool

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import kotlin.coroutines.resume

/**
 * JsRenderer — render JS via WebView headless (untuk render_js=true).
 *
 * Perbaikan dari spek asli:
 * - WebView WAJIB dibuat di main thread (dibuat via Handler) — versi spek
 *   membuatnya di Dispatchers.IO → crash.
 * - evaluateJavascript ASYNC via callback (versi spek memperlakukannya
 *   sebagai sync → tidak kompilasi).
 * - Hasil evaluateJavascript adalah JSON-quoted string → di-unquote aman.
 * - renderIfNeeded yang rusak (melempar HTML sebagai URL) dihapus;
 *   ToolLoop memanggil get() langsung dengan fallback.
 *
 * WAJIB: panggil JsRenderer.init(context) sekali dari Application.onCreate.
 */
object JsRenderer {

    @Volatile
    private var appContext: Context? = null

    fun init(ctx: Context) {
        appContext = ctx.applicationContext
    }

    /**
     * Render URL → outerHTML, atau null bila gagal/timeout/tidak di-init.
     * Harus dipanggil dari coroutine (bukan main thread — menunggu via suspend).
     */
    @SuppressLint("SetJavaScriptEnabled")
    suspend fun get(url: String, timeoutMs: Long = 15_000): String? {
        val ctx = appContext ?: return null
        // Jangan deadlock bila dipanggil dari main thread.
        if (Looper.myLooper() == Looper.getMainLooper()) return null
        return withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine { cont ->
                val main = Handler(Looper.getMainLooper())
                var webView: WebView? = null
                var done = false

                fun finish(v: String?) {
                    if (done) return
                    done = true
                    val wv = webView
                    webView = null
                    main.post { runCatching { wv?.destroy() } }
                    if (cont.isActive) cont.resume(v)
                }

                main.post {
                    try {
                        val wv = WebView(ctx)
                        webView = wv
                        wv.settings.javaScriptEnabled = true
                        wv.settings.domStorageEnabled = false
                        wv.settings.blockNetworkImage = true
                        wv.settings.userAgentString =
                            "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 HermesBot/9.3"
                        wv.webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView, u: String) {
                                // 1 tick agar DOM async ke-flush
                                view.postDelayed({
                                    try {
                                        view.evaluateJavascript(
                                            "(function(){return document.documentElement.outerHTML})()",
                                        ) { result ->
                                            finish(unquoteJs(result))
                                        }
                                    } catch (_: Exception) {
                                        finish(null)
                                    }
                                }, 600)
                            }

                            override fun onReceivedError(
                                view: WebView?,
                                request: WebResourceRequest?,
                                error: WebResourceError?,
                            ) {
                                finish(null)
                            }
                        }
                        wv.loadUrl(url)
                    } catch (_: Exception) {
                        finish(null)
                    }
                }
                cont.invokeOnCancellation { finish(null) }
            }
        }
    }

    /**
     * Unquote hasil evaluateJavascript: ia mengembalikan JSON string literal
     * ("<html>...") atau string "null". Parse aman via JSONArray.
     */
    private fun unquoteJs(result: String?): String? {
        if (result.isNullOrEmpty() || result == "null") return null
        return runCatching {
            val arr = JSONArray("[$result]")
            arr.optString(0, null)?.takeIf { it.isNotEmpty() }
        }.getOrNull()
    }
}
