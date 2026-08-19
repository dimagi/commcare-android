package org.commcare.connect.network.base

import com.google.common.collect.ArrayListMultimap
import com.google.gson.Gson
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.commcare.core.network.ModernHttpRequester
import org.commcare.util.LogTypes
import org.commcare.utils.GlobalErrorUtil
import org.commcare.utils.GlobalErrors
import org.commcare.utils.optStringSafe
import org.javarosa.core.io.StreamsUtil
import org.javarosa.core.services.Logger
import org.json.JSONException
import org.json.JSONObject
import retrofit2.HttpException
import java.io.IOException
import java.io.InputStream
import java.nio.charset.StandardCharsets

object NetworkUtils {

    @JvmStatic
    fun getErrorBody(stream: InputStream?): String {
        try {
            if (stream != null) {
                val errorBytes = StreamsUtil.inputStreamToByteArray(stream)
                return String(errorBytes, StandardCharsets.UTF_8)
            }
        } catch (e: Exception) {
            Logger.exception("Error parsing error_code", e);
        }
        return ""
    }

    /**
     * Extracts error_code and error_sub_code from a JSON error response body.
     * If the stream is null or parsing fails, returns empty strings for both codes.
     *
     * @param stream InputStream of the error response body
     * @return Pair of error_code and error_sub_code
     */
    @JvmStatic
    fun getErrorCodes(errorBody: String): Pair<String, String> {
        var errorCode = ""
        var errorSubCode = ""
        try {
            val json = JSONObject(errorBody)
            errorCode = json.optString("error_code", "");
            errorSubCode = json.optString("error_sub_code", "");
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
        errorBody: String
    ) {
        var message = "Response Message: $responseMessage | Response Code: $responseCode"
        message += if (errorBody.isNotEmpty()) " | error: $errorBody" else ""
        var errorMessage = when (responseCode) {
            400 -> "Bad Request: $message"
            401 -> "Unauthorized: $message"
            404 -> "Not Found: $message"
            500 -> "Server Error: $message"
            else -> "API Error: $message"

        }
        errorMessage += " for url ${endPoint ?: "unknown url"}"

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


    @JvmStatic
    fun addVersionHeader(
        headers: HashMap<String, String>,
        version: String,
    ) {
        headers["Accept"] = "application/json;version=$version"
    }

    @JvmStatic
    fun buildPostFormHeaders(
        params: HashMap<String, Any>,
        useFormEncoding: Boolean,
        version: String,
        outputHeaders: HashMap<String, String>,
    ): RequestBody {
        val requestBody: RequestBody

        if (useFormEncoding) {
            val multimap = ArrayListMultimap.create<String, String>()
            for ((key, value) in params) {
                multimap.put(key, value.toString())
            }
            requestBody = ModernHttpRequester.getPostBody(multimap)
            outputHeaders.putAll(getContentHeadersForXFormPost(requestBody))
        } else {
            val json = Gson().toJson(params)
            requestBody = json.toRequestBody("application/json".toMediaType())
        }

        addVersionHeader(outputHeaders, version)
        return requestBody
    }

    private fun getContentHeadersForXFormPost(postBody: RequestBody): HashMap<String, String> {
        val headers = HashMap<String, String>()
        headers["Content-Type"] = "application/x-www-form-urlencoded"
        try {
            headers["Content-Length"] = postBody.contentLength().toString()
        } catch (_: Exception) {
        }
        return headers
    }

    @JvmStatic
    fun checkForLoginFromDifferentDevice(errorBody: String?): Boolean {
        if (errorBody == null) return false
        return try {
            val json = JSONObject(errorBody)
            "LOGIN_FROM_DIFFERENT_DEVICE" == json.optStringSafe("error_code", null)
        } catch (_: JSONException) {
            false
        }
    }

    @JvmStatic
    fun mapHttpErrorCode(
        responseCode: Int,
        errorBody: String?,
    ): BaseApiHandler.PersonalIdOrConnectApiErrorCodes =
        when (responseCode) {
            401 -> {
                BaseApiHandler.PersonalIdOrConnectApiErrorCodes.FAILED_AUTH_ERROR
            }

            403 -> {
                BaseApiHandler.PersonalIdOrConnectApiErrorCodes.FORBIDDEN_ERROR
            }

            429 -> {
                BaseApiHandler.PersonalIdOrConnectApiErrorCodes.RATE_LIMIT_EXCEEDED_ERROR
            }

            400 -> {
                if (checkForLoginFromDifferentDevice(errorBody)) {
                    GlobalErrorUtil.triggerGlobalError(GlobalErrors.PERSONALID_LOGIN_FROM_DIFFERENT_DEVICE_ERROR)
                }
                BaseApiHandler.PersonalIdOrConnectApiErrorCodes.BAD_REQUEST_ERROR
            }

            in 500..509 -> {
                BaseApiHandler.PersonalIdOrConnectApiErrorCodes.SERVER_ERROR
            }

            else -> {
                BaseApiHandler.PersonalIdOrConnectApiErrorCodes.UNKNOWN_ERROR
            }
        }
}
