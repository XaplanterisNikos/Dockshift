package com.foxnks.dockshift.data.legacy

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Reads a whole text file chosen by the user. An interface, so ViewModels can be tested with a fake. */
interface TextFileReader {

    /**
     * @param uri the `content://` address the system file picker returned.
     * @return the file contents decoded as UTF-8 (the encoding v1 wrote).
     * @throws IOException if the file cannot be opened, read, or is unreasonably large.
     */
    suspend fun read(uri: String): String
}

/**
 * [TextFileReader] through the system's ContentResolver: works for any file the
 * user picked, without any storage permission.
 *
 * @param context the application context, injected by Hilt.
 */
class ContentResolverTextReader @Inject constructor(
    @ApplicationContext private val context: Context,
) : TextFileReader {

    override suspend fun read(uri: String): String = withContext(Dispatchers.IO) {
        val input = context.contentResolver.openInputStream(Uri.parse(uri))
            ?: throw IOException("Cannot open $uri")

        input.use { stream ->
            // Copy with a size limit: a wrong pick (a video, say) must not exhaust memory
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(BUFFER_SIZE)
            while (true) {
                val read = stream.read(buffer)
                if (read == -1) break
                output.write(buffer, 0, read)
                if (output.size() > MAX_BYTES) throw IOException("File larger than $MAX_BYTES bytes")
            }
            output.toString(Charsets.UTF_8.name())
        }
    }

    private companion object {
        const val BUFFER_SIZE = 8 * 1024

        /** A v1 file with 10 years of shifts is about 200 KB; 10 MB is far beyond any real one. */
        const val MAX_BYTES = 10 * 1024 * 1024
    }
}
