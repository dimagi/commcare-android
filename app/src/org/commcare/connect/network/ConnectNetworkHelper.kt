package org.commcare.connect.network

import com.google.common.collect.ArrayListMultimap
import com.google.gson.Gson
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.commcare.CommCareApplication
import org.commcare.android.database.connect.models.ConnectUserRecord
import org.commcare.connect.network.base.BaseApiHandler.PersonalIdOrConnectApiErrorCodes
import org.commcare.connect.network.base.ConnectApiException
import org.commcare.core.network.AuthInfo
import org.commcare.core.network.ModernHttpRequester
import org.commcare.network.HttpUtils
import org.commcare.utils.GlobalErrorUtil
import org.commcare.utils.GlobalErrors
import org.commcare.utils.optStringSafe
import org.json.JSONException
import org.json.JSONObject
import kotlin.coroutines.resume

class ConnectNetworkHelper {
    companion object {
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
                // Empty headers if something goes wrong
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
    }
}

suspend fun getAuthorizationHeader(
    user: ConnectUserRecord,
): Result<String> =
    suspendCancellableCoroutine { continuation ->
        ConnectSsoHelper.retrievePersonalIdToken(
            CommCareApplication.instance(),
            user,
            object : ConnectSsoHelper.TokenCallback {
                override fun tokenRetrieved(token: AuthInfo.TokenAuth) {
                    continuation.resume(Result.success(HttpUtils.getCredential(token)))
                }

                override fun tokenUnavailable() {
                    continuation.resume(
                        Result.failure(ConnectApiException(PersonalIdOrConnectApiErrorCodes.TOKEN_UNAVAILABLE_ERROR)),
                    )
                }

                override fun tokenRequestDenied() {
                    continuation.resume(
                        Result.failure(ConnectApiException(PersonalIdOrConnectApiErrorCodes.TOKEN_DENIED_ERROR)),
                    )
                }
            },
        )
    }

internal fun mapHttpErrorCode(
    responseCode: Int,
    errorBody: String?,
): PersonalIdOrConnectApiErrorCodes =
    when (responseCode) {
        401 -> PersonalIdOrConnectApiErrorCodes.FAILED_AUTH_ERROR
        403 -> PersonalIdOrConnectApiErrorCodes.FORBIDDEN_ERROR
        429 -> PersonalIdOrConnectApiErrorCodes.RATE_LIMIT_EXCEEDED_ERROR
        400 -> {
            if (ConnectNetworkHelper.checkForLoginFromDifferentDevice(errorBody)) {
                GlobalErrorUtil.triggerGlobalError(GlobalErrors.PERSONALID_LOGIN_FROM_DIFFERENT_DEVICE_ERROR)
            }
            PersonalIdOrConnectApiErrorCodes.BAD_REQUEST_ERROR
        }
        in 500..509 -> PersonalIdOrConnectApiErrorCodes.SERVER_ERROR
        else -> PersonalIdOrConnectApiErrorCodes.UNKNOWN_ERROR
    }
