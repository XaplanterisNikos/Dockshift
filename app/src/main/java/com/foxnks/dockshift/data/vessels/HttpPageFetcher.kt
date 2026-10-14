package com.foxnks.dockshift.data.vessels

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * [PageFetcher] with Java's built-in HTTP client: two plain GET requests do not need
 * a networking library.
 */
class HttpPageFetcher @Inject constructor() : PageFetcher {

    override suspend fun fetch(url: String): String = withContext(Dispatchers.IO) {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = TIMEOUT_MILLIS
            connection.readTimeout = TIMEOUT_MILLIS
            // Says who is asking, as polite clients do
            connection.setRequestProperty("User-Agent", USER_AGENT)
            connection.setRequestProperty("Accept", "text/html")
            val code = connection.responseCode
            if (code != HttpURLConnection.HTTP_OK) throw IOException("HTTP $code for $url")
            connection.inputStream.use { input ->
                // Copy with a size limit: a wrong address must not exhaust memory
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(BUFFER_SIZE)
                while (true) {
                    val read = input.read(buffer)
                    if (read == -1) break
                    output.write(buffer, 0, read)
                    if (output.size() > MAX_BYTES) throw IOException("Page larger than $MAX_BYTES bytes")
                }
                output.toString(Charsets.UTF_8.name())
            }
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        /** Give up on a page after this long, connecting or reading. */
        const val TIMEOUT_MILLIS = 15_000

        const val BUFFER_SIZE = 8 * 1024

        /** The vessel pages are about 50 KB; 2 MB is far beyond any real one. */
        const val MAX_BYTES = 2 * 1024 * 1024

        const val USER_AGENT = "Dockshift/2.0 (Android; shift tracker)"
    }
}
