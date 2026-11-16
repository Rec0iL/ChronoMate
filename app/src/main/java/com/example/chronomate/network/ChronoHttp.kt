package com.example.chronomate.network

import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.io.IOException

/**
 * Fetches the chrono's page and keeps what arrived even when the connection ends early.
 *
 * The HT-X3000 firmware sends a Content-Length that overstates the real body and then closes the
 * connection, so a strict read ("unexpected end of stream") would throw the whole page away although
 * the telemetry always arrives before the cutoff. Same fix as ChronoMate Desktop's poller (dab3d33).
 */
fun fetchChronoPage(url: String, client: OkHttpClient): String? {
    val request = Request.Builder().url(url).header("Cache-Control", "no-cache").build()
    return try {
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body ?: return null
            val out = ByteArrayOutputStream()
            val buf = ByteArray(512)
            try {
                body.byteStream().use { stream ->
                    while (true) {
                        val n = stream.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                    }
                }
            } catch (e: IOException) {
                // connection closed before Content-Length was reached: keep what we have
            }
            if (out.size() == 0) return null
            val charset = body.contentType()?.charset() ?: Charsets.UTF_8
            String(out.toByteArray(), charset)
        }
    } catch (e: Exception) {
        null
    }
}
