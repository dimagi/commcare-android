package org.commcare.utils

import org.json.JSONException
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class JsonExtensionsTest {
    @Test
    fun `optIntSafe returns the int when present`() {
        val json = JSONObject().put("limit", 5)

        assertEquals(5, json.optIntSafe("limit", -1))
    }

    @Test
    fun `optIntSafe returns the fallback when the value is null`() {
        val json = JSONObject().put("limit", JSONObject.NULL)

        assertEquals(-1, json.optIntSafe("limit", -1))
    }

    @Test
    fun `optIntSafe returns the fallback when the key is missing`() {
        assertEquals(-1, JSONObject().optIntSafe("limit", -1))
    }

    @Test
    fun `optIntSafe throws when the value is not a number`() {
        val json = JSONObject().put("limit", "abc")

        assertThrows(JSONException::class.java) {
            json.optIntSafe("limit", -1)
        }
    }
}
