package id.sukiapps.hermes

import id.sukiapps.hermes.data.connectors.UpinProtocol
import id.sukiapps.hermes.domain.AIHttpException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test

/** Unit test JVM untuk parsing kontrak API Upin (tanpa Android). */
class UpinProtocolTest {

    @Test
    fun jobId_ok() {
        assertEquals("abc-123", UpinProtocol.jobIdOf("""{"job_id":"abc-123"}"""))
    }

    @Test
    fun jobId_kosong_throws() {
        try {
            UpinProtocol.jobIdOf("{}")
            fail("harus melempar")
        } catch (e: AIHttpException) {
            assertEquals("Respons Upin tidak dikenali.", e.message)
        }
    }

    @Test
    fun jobId_bukanJson_throws() {
        try {
            UpinProtocol.jobIdOf("bukan json")
            fail("harus melempar")
        } catch (e: AIHttpException) {
            // ok
        }
    }

    @Test
    fun reply_done() {
        val (status, answer) =
            UpinProtocol.replyOf("""{"status":"done","answer":"Halo"}""")
        assertEquals("done", status)
        assertEquals("Halo", answer)
    }

    @Test
    fun reply_pending_answerNull() {
        val (status, answer) =
            UpinProtocol.replyOf("""{"status":"pending","answer":null}""")
        assertEquals("pending", status)
        assertNull(answer)
    }

    @Test
    fun reply_statusDinormalisasi() {
        val (status, _) = UpinProtocol.replyOf("""{"status":" DONE "}""")
        assertEquals("done", status)
    }

    @Test
    fun reply_bukanJson_throws() {
        try {
            UpinProtocol.replyOf("bukan json")
            fail("harus melempar")
        } catch (e: AIHttpException) {
            // ok
        }
    }
}
