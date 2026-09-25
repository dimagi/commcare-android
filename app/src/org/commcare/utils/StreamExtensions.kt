@file:JvmName("StreamExtensions") // This sets the generated class name

package org.commcare.utils

import org.javarosa.core.services.Logger
import java.io.Closeable
import java.io.IOException

/** Closes the resource, logging any [IOException] as a non-fatal instead of throwing. */
fun Closeable?.closeQuietly() {
    try {
        this?.close()
    } catch (e: IOException) {
        Logger.exception("Failed to close ${this?.javaClass?.simpleName}", e)
    }
}
