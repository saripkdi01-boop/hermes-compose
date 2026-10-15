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
}
