package org.commcare.connect.network

import org.commcare.util.LogTypes
import org.javarosa.core.io.StreamsUtil
import org.javarosa.core.services.Logger
import org.json.JSONObject
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
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

    @JvmStatic
    fun logFailedResponse(
        responseMessage: String,
        responseCode: Int,
        endPoint: String,
        errorCode: String,
        errorSubCode: String
    ) {
        var message = "Response Message: $responseMessage | Response Code: $responseCode"
        message += if (errorCode.isNotEmpty()) " | error_code: $errorCode" else ""
        message += if (errorSubCode.isNotEmpty()) " | error_sub_code: $errorSubCode" else ""
        var errorMessage = when (responseCode) {
            400 -> "Bad Request: $message"
            401 -> "Unauthorized: $message"
            404 -> "Not Found: $message"
            500 -> "Server Error: $message"
            else -> "API Error: $message"

        }
        errorMessage += " for url ${endPoint ?: "url not found"}"

        Logger.log(
            LogTypes.TYPE_ERROR_SERVER_COMMS,
            errorMessage
        )
        Logger.exception(LogTypes.TYPE_ERROR_SERVER_COMMS, Throwable(errorMessage))
    }

    @JvmStatic
    fun logNetworkError(t: Throwable, endPoint: String) {
        val message = t.message

        var errorMessage = when (t) {
            is IOException -> "Network Error: $message"
            is HttpException -> "HTTP Error: $message"
            else -> "Unexpected Error: $message"
        }

        errorMessage += " for url ${endPoint ?: "url not found"}"
        Logger.log(
            LogTypes.TYPE_ERROR_SERVER_COMMS,
            errorMessage
        )
        Logger.exception(errorMessage, t)
    }
}
