package org.commcare.connect.network

import org.javarosa.core.io.StreamsUtil
import org.javarosa.core.services.Logger
import org.json.JSONObject
import java.io.InputStream
import java.nio.charset.StandardCharsets

object NetworkUtils {

    /**
     * Extracts error_code and error_sub_code from a JSON error response body.
     * If the stream is null or parsing fails, returns empty strings for both codes.
     *
     * @param stream InputStream of the error response body
     * @return Pair of error_code and error_sub_code
     */
    @JvmStatic
    fun getErrorCodes(stream: InputStream?): Pair<String, String> {
        var errorCode = ""
        var errorSubCode = ""
        try {
            if (stream != null) {
                val errorBytes = StreamsUtil.inputStreamToByteArray(stream)
                val jsonStr = String(errorBytes, StandardCharsets.UTF_8)
                val json = JSONObject(jsonStr)
                errorCode = json.optString("error_code", "");
                errorSubCode = json.optString("error_sub_code", "");
            }
        } catch (e: Exception) {
            Logger.exception("Error parsing error_code", e);
        }
        return Pair(errorCode, errorSubCode)
    }
}
