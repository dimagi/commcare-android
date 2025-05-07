@file:JvmName("JsonExtensions") // This sets the generated class name

package org.commcare.utils

import org.json.JSONObject

fun JSONObject.optStringSafe(key: String): String? {
    val value = this.optString(key, null)
    return if (this.isNull(key) || value == "null") null else value
}