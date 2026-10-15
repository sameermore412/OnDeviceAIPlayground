package com.sameer.ondeviceai.llmchat

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

object ModelDownloader {
    /**
     * Downloads [url] to [target], resuming from a previous partial download if there is one.
     * [onProgress] gets bytes downloaded and the total size when the server reports it.
     */
    suspend fun download(
        url: String,
        target: File,
        onProgress: (downloaded: Long, total: Long?) -> Unit,
    ) = withContext(Dispatchers.IO) {
        val partial = File(target.path + ".part")
        var offset = if (partial.exists()) partial.length() else 0L

        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            if (offset > 0) setRequestProperty("Range", "bytes=$offset-")
        }
        try {
            when (val code = connection.responseCode) {
                HttpURLConnection.HTTP_OK -> offset = 0 // Server ignored the range; start over.
                HttpURLConnection.HTTP_PARTIAL -> Unit
                416 -> { // Range not satisfiable: the partial file is already complete.
                    moveIntoPlace(partial, target)
                    return@withContext
                }
                401, 403 -> throw IOException(
                    "The model host refused the download (HTTP $code). It may need a Hugging Face sign-in.",
                )
                else -> throw IOException("Download failed with HTTP $code")
            }

            val total = connection.contentLengthLong.takeIf { it > 0 }?.plus(offset)
            var downloaded = offset
            var lastReported = 0L
            FileOutputStream(partial, offset > 0).use { output ->
                connection.inputStream.use { input ->
                    val buffer = ByteArray(1 shl 16)
                    while (true) {
                        ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        if (downloaded - lastReported >= 4L * 1024 * 1024) {
                            onProgress(downloaded, total)
                            lastReported = downloaded
                        }
                    }
                }
            }
            if (total != null && downloaded < total) {
                throw IOException("Download stopped early; try again to resume.")
            }
            moveIntoPlace(partial, target)
        } finally {
            connection.disconnect()
        }
    }

    private fun moveIntoPlace(partial: File, target: File) {
        if (!partial.renameTo(target)) throw IOException("Couldn't move the downloaded model into place")
    }
}
