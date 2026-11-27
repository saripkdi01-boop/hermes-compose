package id.sukiapps.hermes.data.remote

import id.sukiapps.hermes.domain.AIHttpException
import id.sukiapps.hermes.domain.ChatRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlin.concurrent.thread

/**
 * v19.1 (audit P0) — tombol Stop harus memutus koneksi HTTP, bukan hanya
 * menyembunyikan token di UI. Server SSE lokal (ServerSocket mentah, tanpa
 * dependensi tambahan) mengalirkan token tiap 50 ms selama ±10 detik dan
 * mencatat kapan klien memutus socket.
 */
class SseClientCancelTest {

    private lateinit var server: ServerSocket
    private val disconnectedAt = AtomicLong(0L)
    private val serverDone = CountDownLatch(1)

    private val req = ChatRequest(
        model = "m",
        systemPrompt = "s",
        history = emptyList(),
        prompt = "halo"
    )

    @Before
    fun setUp() {
        server = ServerSocket(0)
    }

    @After
    fun tearDown() {
        runCatching { server.close() }
    }

    private fun baseUrl() = "http://127.0.0.1:${server.localPort}/v1"

    /** Satu koneksi: header chunked, lalu [onBody] menulis isi body. */
    private fun serveOnce(onBody: (OutputStream, Socket) -> Unit) {
        thread(isDaemon = true) {
            try {
                server.accept().use { sock ->
                    val input = sock.getInputStream().bufferedReader()
                    // Baca header request sampai baris kosong (body diabaikan).
                    while (true) {
                        val line = input.readLine() ?: break
                        if (line.isEmpty()) break
                    }
                    val out = sock.getOutputStream()
                    out.write(
                        ("HTTP/1.1 200 OK\r\n" +
                            "Content-Type: text/event-stream\r\n" +
                            "Transfer-Encoding: chunked\r\n\r\n").toByteArray()
                    )
                    out.flush()
                    onBody(out, sock)
                }
            } catch (_: IOException) {
            } finally {
                serverDone.countDown()
            }
        }
    }

    private fun OutputStream.chunk(text: String) {
        val bytes = text.toByteArray()
        write((Integer.toHexString(bytes.size) + "\r\n").toByteArray())
        write(bytes)
        write("\r\n".toByteArray())
        flush()
    }

    private fun delta(t: String) = "data: {\"choices\":[{\"delta\":{\"content\":\"$t\"}}]}\n\n"

    private fun slowTokens(out: OutputStream) {
        try {
            repeat(200) { i ->
                out.chunk(delta("t$i"))
                Thread.sleep(50)
            }
            out.chunk("data: [DONE]\n\n")
            out.write("0\r\n\r\n".toByteArray())
            out.flush()
        } catch (e: IOException) {
            // Klien memutus socket → tulis gagal (broken pipe / reset).
            disconnectedAt.set(System.currentTimeMillis())
        }
    }

    private fun assertDisconnectsQuickly(flow: Flow<String>) = runBlocking {
        serveOnce { out, _ -> slowTokens(out) }
        val got = withTimeout(5_000) {
            flow.flowOn(Dispatchers.IO).take(3).toList()
        }
        val cancelledAt = System.currentTimeMillis()
        assertEquals(listOf("t0", "t1", "t2"), got)
        // Server harus melihat koneksi putus jauh sebelum 10 detik stream
        // selesai. Tanpa perbaikan, klien terus membaca sampai [DONE].
        assertTrue("server tidak selesai", serverDone.await(4, TimeUnit.SECONDS))
        val at = disconnectedAt.get()
        assertTrue("klien tidak memutus koneksi saat dibatalkan", at > 0L)
        assertTrue("putus terlambat: ${at - cancelledAt} ms", at - cancelledAt < 3_000)
    }

    @Test
    fun stream_cancelCollector_closesHttpConnection() {
        assertDisconnectsQuickly(SseClient(OkHttpClient()).stream(baseUrl(), req))
    }

    @Test
    fun streamWithTools_cancelCollector_closesHttpConnection() {
        assertDisconnectsQuickly(
            SseClient(OkHttpClient()).streamWithTools(baseUrl(), req, { _, _, _ -> })
        )
    }

    @Test
    fun streamWithTools_midStreamDrop_isErrorNotSilentTruncation() = runBlocking {
        serveOnce { out, sock ->
            out.chunk(delta("Halo"))
            // Potong di tengah chunk tanpa terminator → IOException di klien.
            out.write("40\r\ndata: {\"choi".toByteArray())
            out.flush()
            sock.close()
        }
        val tokens = mutableListOf<String>()
        try {
            withTimeout(5_000) {
                SseClient(OkHttpClient())
                    .streamWithTools(baseUrl(), req, { _, _, _ -> })
                    .flowOn(Dispatchers.IO)
                    .collect { tokens += it }
            }
            fail("putus di tengah stream harus jadi error, bukan selesai normal")
        } catch (e: AIHttpException) {
            assertTrue(e.message!!.startsWith("Stream terputus"))
            assertTrue(e.retryable)
        }
        assertEquals(listOf("Halo"), tokens)
    }

    @Test
    fun stream_normalCompletion_stillWorks() = runBlocking {
        serveOnce { out, _ ->
            out.chunk(delta("A"))
            out.chunk(delta("B"))
            out.chunk("data: [DONE]\n\n")
            out.write("0\r\n\r\n".toByteArray())
            out.flush()
        }
        val got = withTimeout(5_000) {
            SseClient(OkHttpClient()).stream(baseUrl(), req).flowOn(Dispatchers.IO).toList()
        }
        assertEquals(listOf("A", "B"), got)
    }
}
