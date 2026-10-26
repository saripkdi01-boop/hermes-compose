package id.sukiapps.hermes

import id.sukiapps.hermes.data.auth.SupabaseAuth
import id.sukiapps.hermes.data.auth.SupabaseAuthCallback
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * D3 (14.31) — test JVM murni untuk logika SupabaseAuth.
 * Tidak menyentuh network / Android framework: seluruh fungsi yang diuji
 * murni (PKCE, URL, parse JSON, callback) sehingga bisa jalan di CI
 * tanpa device maupun proyek Supabase.
 */
class SupabaseAuthTest {

    @Test
    fun pkceChallengeMatchesRfc7636Vector() {
        // RFC 7636 Appendix B — verifier & challenge yang sudah baku.
        val verifier = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"
        assertEquals(
            "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM",
            SupabaseAuth.codeChallenge(verifier)
        )
    }

    @Test
    fun generateCodeVerifierFormat() {
        val v1 = SupabaseAuth.generateCodeVerifier()
        val v2 = SupabaseAuth.generateCodeVerifier()
        assertEquals(43, v1.length)
        assertTrue(v1.matches(Regex("[A-Za-z0-9_-]+")))
        assertNotEquals(v1, v2)
    }

    @Test
    fun buildAuthorizeUrlContainsAllParams() {
        val url = SupabaseAuth.buildAuthorizeUrl(
            "https://xyzcompany.supabase.co/",
            "CHALLENGE123"
        )
        assertTrue(url != null)
        assertTrue(url!!.startsWith("https://xyzcompany.supabase.co/auth/v1/authorize?"))
        assertTrue(url.contains("provider=google"))
        assertTrue(url.contains("code_challenge=CHALLENGE123"))
        assertTrue(url.contains("code_challenge_method=S256"))
        assertTrue(
            url.contains("redirect_to=id.sukiapps.hermes%3A%2F%2Fauth%2Fcallback")
        )
    }

    @Test
    fun buildAuthorizeUrlRejectsBlankAndNonHttps() {
        assertNull(SupabaseAuth.buildAuthorizeUrl("", "CH"))
        assertNull(SupabaseAuth.buildAuthorizeUrl("   ", "CH"))
        assertNull(SupabaseAuth.buildAuthorizeUrl("http://x.supabase.co", "CH"))
    }

    @Test
    fun parseTokenResponseExtractsSessionAndProfile() {
        val json = """{
            "access_token": "acc-1",
            "refresh_token": "ref-1",
            "expires_in": 3600,
            "token_type": "bearer",
            "user": {
                "id": "u-1",
                "email": "pemilik@contoh.id",
                "user_metadata": {"full_name": "Nama Pemilik"}
            }
        }"""
        val s = SupabaseAuth.parseTokenResponse(json)
        assertEquals("acc-1", s.accessToken)
        assertEquals("ref-1", s.refreshToken)
        assertEquals(3600L, s.expiresInSec)
        assertEquals("u-1", s.userId)
        assertEquals("pemilik@contoh.id", s.email)
        assertEquals("Nama Pemilik", s.name)
        // expiresAt = now + 3600 − 60: harus di masa depan, tidak jauh.
        val now = System.currentTimeMillis() / 1000
        assertTrue(s.expiresAtEpochSec in (now + 3400)..(now + 3600))
    }

    @Test
    fun parseUserResponseFallsBackToEmptyName() {
        val json = """{"id": "u-2", "email": "a@b.id", "user_metadata": {}}"""
        val p = SupabaseAuth.parseUserResponse(json)
        assertEquals("u-2", p.id)
        assertEquals("a@b.id", p.email)
        assertEquals("", p.name)
    }

    @Test
    fun callbackCaptureAndSingleUse() {
        assertFalse(
            SupabaseAuthCallback.capture("https://example.com/other?code=x")
        )
        assertTrue(
            SupabaseAuthCallback.capture(
                "id.sukiapps.hermes://auth/callback?code=CODE123"
            )
        )
        assertTrue(SupabaseAuthCallback.hasPending())
        val (code, error) = SupabaseAuthCallback.take()
        assertEquals("CODE123", code)
        assertNull(error)
        assertFalse(SupabaseAuthCallback.hasPending())
        // Tangkap ulang kode yang sama = diabaikan (anti tukar ganda).
        assertFalse(
            SupabaseAuthCallback.capture(
                "id.sukiapps.hermes://auth/callback?code=CODE123"
            )
        )
        assertFalse(SupabaseAuthCallback.hasPending())
    }

    @Test
    fun callbackErrorCaptured() {
        assertTrue(
            SupabaseAuthCallback.capture(
                "id.sukiapps.hermes://auth/callback?error=access_denied"
            )
        )
        val (code, error) = SupabaseAuthCallback.take()
        assertNull(code)
        assertEquals("access_denied", error)
    }

    @Test
    fun errorMessageMapping() {
        assertTrue(SupabaseAuth.errorMessage(401, "").contains("Key Supabase ditolak"))
        assertTrue(SupabaseAuth.errorMessage(400, "").contains("redirect"))
        assertTrue(SupabaseAuth.errorMessage(422, "").contains("login ulang"))
    }
}
