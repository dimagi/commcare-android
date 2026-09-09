@file:JvmName("JsonExtensions") // This sets the generated class name

package org.commcare.utils

import org.javarosa.core.model.utils.DateUtils
import org.json.JSONException
import org.json.JSONObject
import java.util.Date

fun JSONObject.hasNonNull(key: String): Boolean = has(key) && !isNull(key)

fun JSONObject.optStringSafe(
    key: String,
    fallback: String? = null,
): String? {
    if (this.isNull(key)) return fallback
    return this.optString(key, fallback)
}

/** Returns the value at [key] if it is present and not blank, otherwise null. */
fun JSONObject.optNonBlankStringSafe(key: String): String? = optStringSafe(key, null)?.takeIf { it.isNotBlank() }

fun JSONObject.requireDate(key: String): Date =
    DateUtils.parseDate(getString(key))
        ?: throw JSONException("Unparseable date for $key: ${getString(key)}")
