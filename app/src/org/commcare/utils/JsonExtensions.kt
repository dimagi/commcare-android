@file:JvmName("JsonExtensions") // This sets the generated class name

package org.commcare.utils

import org.json.JSONObject

fun JSONObject.optStringSafe(key: String, fallback: String? = null): String? {
    if (this.isNull(key)) return fallback
    return this.optString(key, fallback)
}