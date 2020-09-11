package org.commcare.utils

import okhttp3.MediaType
import okhttp3.RequestBody
import okio.BufferedSink

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

    override fun writeTo(sink: BufferedSink) {
        okio.Okio.source(classLoader.getResourceAsStream(fileName)).use {
            sink.writeAll(it)
        }
    }
}