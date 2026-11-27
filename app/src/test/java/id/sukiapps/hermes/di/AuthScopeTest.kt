package id.sukiapps.hermes.di

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** v19.1 (audit P1): API key hanya boleh menuju host base URL provider. */
class AuthScopeTest {

    private val base = "https://je.jerouter.web.id/v1"

    @Test
    fun sameHost_anyPath_getsKey() {
        assertTrue(AuthScope.matches("https://je.jerouter.web.id/v1/chat/completions".toHttpUrl(), base))
        assertTrue(AuthScope.matches("https://JE.jerouter.web.id/v1/models".toHttpUrl(), base))
    }

    @Test
    fun otherHosts_neverGetKey() {
        assertFalse(AuthScope.matches("https://api.github.com/user".toHttpUrl(), base))
        assertFalse(AuthScope.matches("https://api.telegram.org/botX/getMe".toHttpUrl(), base))
        assertFalse(AuthScope.matches("https://evil.example/je.jerouter.web.id/v1".toHttpUrl(), base))
        // Subdomain/lookalike bukan host yang sama.
        assertFalse(AuthScope.matches("https://je.jerouter.web.id.evil.example/v1".toHttpUrl(), base))
    }

    @Test
    fun schemeOrPortMismatch_noKey() {
        assertFalse(AuthScope.matches("http://je.jerouter.web.id/v1/models".toHttpUrl(), base))
        assertFalse(AuthScope.matches("https://je.jerouter.web.id:8443/v1/models".toHttpUrl(), base))
    }

    @Test
    fun invalidBaseUrl_noKey() {
        assertFalse(AuthScope.matches("https://je.jerouter.web.id/v1".toHttpUrl(), ""))
        assertFalse(AuthScope.matches("https://je.jerouter.web.id/v1".toHttpUrl(), "bukan url"))
    }
}
