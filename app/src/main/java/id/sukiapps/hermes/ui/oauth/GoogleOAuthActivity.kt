package id.sukiapps.hermes.ui.oauth

import android.net.Uri
import android.os.Bundle
import android.util.Base64
import androidx.activity.ComponentActivity
import androidx.browser.customtabs.CustomTabsIntent
import androidx.lifecycle.lifecycleScope
import id.sukiapps.hermes.data.connectors.CredentialVault
import kotlinx.coroutines.launch
import java.security.MessageDigest
import java.security.SecureRandom

class GoogleOAuthActivity : ComponentActivity() {

    companion object {
        const val SCHEME = "hermes"
        const val HOST = "oauth"
        const val PATH_CALLBACK = "/callback"
        const val SERVICE_GOOGLE = "google"
        const val AUTH_ENDPOINT = "https://accounts.google.com/o/oauth2/v2/auth"
        const val TOKEN_ENDPOINT = "https://oauth2.googleapis.com/token"
        const val REDIRECT_URI = "$SCHEME://$HOST$PATH_CALLBACK"
        const val SCOPE = "openid email profile"
    }

    private lateinit var credentialVault: CredentialVault
    private var codeVerifier: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        credentialVault = CredentialVault(applicationContext)
        val verifier = generateCodeVerifier()
        codeVerifier = verifier
        val challenge = generateCodeChallenge(verifier)
        val authUri = Uri.parse(AUTH_ENDPOINT).buildUpon()
            .applyQueryParameter("client_id", BuildConfig.GOOGLE_OAUTH_CLIENT_ID)
            .applyQueryParameter("redirect_uri", REDIRECT_URI)
            .applyQueryParameter("response_type", "code")
            .applyQueryParameter("scope", SCOPE)
            .applyQueryParameter("code_challenge", challenge)
            .applyQueryParameter("code_challenge_method", "S256")
            .applyQueryParameter("state", generateState())
            .build()
        val customTabsIntent = CustomTabsIntent.Builder().build()
        customTabsIntent.launchUrl(this, authUri)
    }

    private fun generateCodeVerifier(): String {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }

    private fun generateCodeChallenge(verifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(verifier.toByteArray(Charsets.US_ASCII))
        return Base64.encodeToString(digest, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }

    private fun generateState(): String = Base64.encodeToString(
        ByteArray(16).also { SecureRandom().nextBytes(it) },
        Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP
    )

    fun handleCallback(uri: Uri) {
        val code = uri.getQueryParameter("code") ?: run { finish(); return }
        lifecycleScope.launch {
            try {
                val token = exchangeCodeForToken(code, codeVerifier!!)
                credentialVault.saveToken(SERVICE_GOOGLE, token)
                setResult(RESULT_OK)
            } catch (e: Exception) {
                setResult(RESULT_CANCELED)
            } finally {
                finish()
            }
        }
    }

    private suspend fun exchangeCodeForToken(code: String, verifier: String): String {
        throw UnsupportedOperationException("token exchange via PKCE not implemented")
    }
}
