package id.sukiapps.hermes.ui.web

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import id.sukiapps.hermes.data.connectors.CredentialVault

class InteractiveWebActivity : ComponentActivity() {

    private lateinit var webView: WebView
    private lateinit var credentialVault: CredentialVault

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        credentialVault = CredentialVault(applicationContext)
        val url = intent.getStringExtra(EXTRA_URL) ?: DEFAULT_URL
        setupWebView(url)
        onBackPressedDispatcher.addCallback(this) {
            if (webView.canGoBack()) webView.goBack() else finish()
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView(url: String) {
        webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.databaseEnabled = true
            loadUrl(url)
        }
        setContentView(webView)
    }

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_URL = "extra_url"
        const val DEFAULT_URL = "https://muse.ai"
    }
}
