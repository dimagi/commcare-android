package org.commcare.utils

import org.json.JSONArray
import org.json.JSONObject

/**
 * @author $|-|!˅@M
 */

/**
 * Converts a JSONArray to a float array.
 * Assumes that the JSONArray contains only float values.
 *
 * @throws NumberFormatException if the JSONArray contains a string which is not a valid number.
 */
fun JSONArray.toFloatArray(): FloatArray {
    val labels = FloatArray(length())
    for (i in 0 until length()) {
        labels[i] = getString(i).toFloat()
    }
    return labels
}

/**
 * Uses a JSONObject and create a float array of all the keys and map of key value.
 * Assumes that the JSONObject is a simple mapping of float key and string value.
 *
 * @throws NumberFormatException if the key which is not a valid number.
 * @throws ClassCastException if the value is not a valid string.
 */
fun JSONObject.toFloatKeyAndStringValueArray(): Pair<FloatArray, Map<Float, String>> {
    val floatKeys = arrayListOf<Float>()
    val mapValues = mutableMapOf<Float, String>()
    keys().forEach { currentKey ->
        val str = currentKey as String // Unfortunately, Kotlin fails to recognise currentKey as string
        floatKeys.add(str.toFloat())
        mapValues[str.toFloat()] = getString(str)
    }
    return Pair(floatKeys.toFloatArray(), mapValues)
}
