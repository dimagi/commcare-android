@file:JvmName("JsonExtensions") // This sets the generated class name

package org.commcare.utils

import org.json.JSONObject

fun JSONObject.optStringSafe(key: String, fallback: String? = null): String? {
    if (this.isNull(key)) return fallback
    return this.optString(key, fallback)
}

fun JSONObject.getRequiredString(key: String, index: Int): String {
    return this.optStringSafe(key)?.takeIf { it.isNotBlank() && it != "null" }
        ?: throw RuntimeException("$key is missing at index $index")
}