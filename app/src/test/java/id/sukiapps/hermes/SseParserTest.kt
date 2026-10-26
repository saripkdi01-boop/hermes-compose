package id.sukiapps.hermes

import id.sukiapps.hermes.data.remote.SseDecoder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Unit test JVM untuk dekoder SSE (tanpa Android). */
class SseParserTest {

    @Test
    fun extractDelta_simpleContent() {
        val data = """{"id":"1","choices":[{"delta":{"role":"assistant","content":"Halo"}}]}"""
        assertEquals("Halo", SseDecoder.extractDelta(data))
    }

    @Test
    fun extractDelta_unicodeEscape() {
        val data = """{"choices":[{"delta":{"content":"caf\u00e9"}}]}"""
        assertEquals("café", SseDecoder.extractDelta(data))
    }

    @Test
    fun extractDelta_roleOnly_returnsNull() {
        val data = """{"choices":[{"delta":{"role":"assistant"}}]}"""
        assertNull(SseDecoder.extractDelta(data))
    }

    @Test
    fun extractDelta_done_returnsNull() {
        assertNull(SseDecoder.extractDelta("[DONE]"))
    }

    @Test
    fun extractDelta_malformed_returnsNull() {
        assertNull(SseDecoder.extractDelta("bukan json"))
        assertNull(SseDecoder.extractDelta(""))
    }

    @Test
    fun extractDelta_escapedQuotes() {
        val data = """{"choices":[{"delta":{"content":"dia bilang \"hai\""}}]}"""
        assertEquals("dia bilang \"hai\"", SseDecoder.extractDelta(data))
    }

    @Test
    fun extractDelta_newlineEscape() {
        val data = """{"choices":[{"delta":{"content":"baris1\nbaris2"}}]}"""
        assertEquals("baris1\nbaris2", SseDecoder.extractDelta(data))
    }

    // 14.29 (B6): parse bertipe — error provider tidak boleh hilang
    // menjadi null seperti "bukan konten".

    @Test
    fun parseEvent_delta() {
        val data = """{"choices":[{"delta":{"content":"Halo"}}]}"""
        assertEquals(SseDecoder.SseEvent.Delta("Halo"), SseDecoder.parseEvent(data))
    }

    @Test
    fun parseEvent_providerError_rateLimit_isRetryable() {
        val data = """{"error":{"message":"Rate limit reached","type":"rate_limit_error","code":"rate_limit_exceeded"}}"""
        assertEquals(
            SseDecoder.SseEvent.ProviderError("Rate limit reached", true),
            SseDecoder.parseEvent(data)
        )
    }

    @Test
    fun parseEvent_providerError_invalidKey_isNotRetryable() {
        val data = """{"error":{"message":"Incorrect API key","type":"authentication_error","code":"invalid_api_key"}}"""
        assertEquals(
            SseDecoder.SseEvent.ProviderError("Incorrect API key", false),
            SseDecoder.parseEvent(data)
        )
    }

    @Test
    fun parseEvent_usageOnly_isIgnored() {
        val data = """{"usage":{"prompt_tokens":10,"completion_tokens":0}}"""
        assertEquals(SseDecoder.SseEvent.Ignored, SseDecoder.parseEvent(data))
    }

    @Test
    fun parseEvent_malformed_isParseFailed() {
        assertEquals(SseDecoder.SseEvent.ParseFailed, SseDecoder.parseEvent("bukan json"))
    }

    @Test
    fun parseEvent_done_isIgnored() {
        assertEquals(SseDecoder.SseEvent.Ignored, SseDecoder.parseEvent("[DONE]"))
    }
}
