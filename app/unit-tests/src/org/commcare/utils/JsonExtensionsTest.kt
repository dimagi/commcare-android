package org.commcare.utils

import org.json.JSONException
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class JsonExtensionsTest {
    @Test
    fun `requireIntOrDefaultIfNull returns the int when present`() {
        val json = JSONObject().put("limit", 5)

        assertEquals(5, json.requireIntOrDefaultIfNull("limit", -1))
    }

    @Test
    fun `requireIntOrDefaultIfNull returns the null value when the value is null`() {
        val json = JSONObject().put("limit", JSONObject.NULL)

        assertEquals(-1, json.requireIntOrDefaultIfNull("limit", -1))
    }

    @Test
    fun `requireIntOrDefaultIfNull throws when the key is missing`() {
        assertThrows(JSONException::class.java) {
            JSONObject().requireIntOrDefaultIfNull("limit", -1)
        }
    }

    @Test
    fun `requireIntOrDefaultIfNull throws when the value is not a number`() {
        val json = JSONObject().put("limit", "abc")

        assertThrows(JSONException::class.java) {
            json.requireIntOrDefaultIfNull("limit", -1)
        }
    }
}
