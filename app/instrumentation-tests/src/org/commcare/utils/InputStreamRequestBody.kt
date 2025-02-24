package org.commcare.utils

import okhttp3.MediaType
import okhttp3.RequestBody
import okio.BufferedSink
import org.javarosa.core.io.StreamsUtil
import java.io.InputStream

/**
 * A utility that's a wrapper around OkHttp's RequestBody which provides initialization of RequestBody
 * directly using InputStream.
 * The input stream is obtained from ClassLoader.getResourceAsStream.
 *
 * Inspired By: https://github.com/square/okhttp/issues/3585#issuecomment-327319196
 */
class InputStreamRequestBody(private val contentType: MediaType,
                             private val classLoader: ClassLoader,
                             private val fileName: String) : RequestBody() {

    override fun contentType(): MediaType? {
        return contentType
    }

    override fun contentLength(): Long {
        val stream = classLoader.getResourceAsStream(fileName)
        return stream.available().toLong()
    }

    override fun writeTo(sink: BufferedSink) {
        val stream = classLoader.getResourceAsStream(fileName)
        stream.use {
            StreamsUtil.writeFromInputToOutputUnmanaged(stream, sink.outputStream())
        }
    }
}
