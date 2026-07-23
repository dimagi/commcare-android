@file:JvmName("StreamExtensions") // This sets the generated class name

package org.commcare.utils

import org.javarosa.core.services.Logger
import java.io.IOException
import java.io.OutputStream

/** Closes the stream, logging any [IOException] as a non-fatal instead of throwing. */
fun OutputStream.closeQuietly() {
    try {
        close()
    } catch (e: IOException) {
        Logger.exception("Failed to close output stream", e)
    }
}
