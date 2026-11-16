package com.example.chronomate

import com.example.chronomate.network.fetchChronoPage
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.ServerSocket
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/** The HT-X3000 announces a longer body than it sends and closes early: keep what arrived. */
class ChronoHttpTest {
    private val client = OkHttpClient.Builder().readTimeout(2, TimeUnit.SECONDS).build()

    private fun serveOnce(response: ByteArray): Int {
        val server = ServerSocket(0)
        thread(isDaemon = true) {
            server.use { s ->
                s.accept().use { c ->
                    val input = c.getInputStream().bufferedReader()
                    while (input.readLine()?.isNotEmpty() == true) { /* read request headers */ }
                    c.getOutputStream().apply { write(response); flush() }
                }   // closing here = the firmware cutting the connection
            }
        }
        return server.localPort
    }

    @Test
    fun truncatedBodyIsKept() {
        val page = "<html>FireRate: 0.0 01: 98.50 m/s 02: 99.10 m/s"
        val raw = "HTTP/1.1 200 OK\r\nContent-Type: text/html\r\nContent-Length: 4000\r\n\r\n$page"
        val port = serveOnce(raw.toByteArray())
        val got = fetchChronoPage("http://127.0.0.1:$port/", client)
        assertEquals(page, got)
        assertTrue(got!!.contains("02: 99.10"))
    }

    @Test
    fun completeBodyAndErrors() {
        val page = "<html>01: 98.50 m/s</html>"
        val ok = serveOnce("HTTP/1.1 200 OK\r\nContent-Length: ${page.length}\r\n\r\n$page".toByteArray())
        assertEquals(page, fetchChronoPage("http://127.0.0.1:$ok/", client))
        val err = serveOnce("HTTP/1.1 500 Oops\r\nContent-Length: 0\r\n\r\n".toByteArray())
        assertNull(fetchChronoPage("http://127.0.0.1:$err/", client))
    }
}
