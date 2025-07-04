package org.commcare.connect.network.base


import org.commcare.connect.network.IApiCallback
import org.commcare.connect.network.base.BaseApiHandler.PersonalIdOrConnectApiErrorCodes
import org.javarosa.core.io.StreamsUtil
import org.javarosa.core.services.Logger
import org.json.JSONObject
import java.io.InputStream

/**
 * This is base class for all API callbacks. It by default handles all error messages, no need
 * to define the error handling in all api handlers
 */
abstract class BaseApiCallback<T>(val baseApiHandler: BaseApiHandler<T>) :
    IApiCallback {

    override fun processFailure(responseCode: Int, errorResponse: InputStream?, url: String?) {
        // Common error_code handler used before checking error response code
        if (handleErrorCodeIfPresent(errorResponse)) return

        when (responseCode) {
            401 -> baseApiHandler.onFailure(
                PersonalIdOrConnectApiErrorCodes.FAILED_AUTH_ERROR,
                null
            )

            403 -> baseApiHandler.onFailure(
                PersonalIdOrConnectApiErrorCodes.FORBIDDEN_ERROR,
                null
            )

            429 -> baseApiHandler.onFailure(
                PersonalIdOrConnectApiErrorCodes.RATE_LIMIT_EXCEEDED_ERROR,
                null
            )

            in 500..509 -> baseApiHandler.onFailure(
                PersonalIdOrConnectApiErrorCodes.SERVER_ERROR,
                null
            )

            else -> {
                val exception = Exception("Encountered response code $responseCode for url ${url ?: "url not found"}")
                Logger.exception("Unknown http response code", exception)
                baseApiHandler.onFailure(PersonalIdOrConnectApiErrorCodes.UNKNOWN_ERROR, exception)
            }
        }
    }

    /**
     * Checks for "error_code" in the API error response and handles known cases.
     * Returns true if the error was handled, otherwise false.
     */
    private fun handleErrorCodeIfPresent(errorResponse: InputStream?): Boolean {
        if (errorResponse == null) return false

        return try {
            errorResponse.use {
                val json =
                    JSONObject(String(StreamsUtil.inputStreamToByteArray(it), Charsets.UTF_8))
                val errorCode = json.optString("error_code", "")

                if (errorCode.equals("LOCKED_ACCOUNT", ignoreCase = true)) {
                    baseApiHandler.onFailure(
                        PersonalIdOrConnectApiErrorCodes.ACCOUNT_LOCKED_ERROR,
                        null
                    )
                    true
                } else {
                    false
                }
            }
        } catch (e: Exception) {
            Logger.exception("Error parsing error_code", e)
            false
        }
    }

    override fun processNetworkFailure() {
        baseApiHandler.onFailure(
            PersonalIdOrConnectApiErrorCodes.NETWORK_ERROR,
            null
        )
    }

    override fun processTokenUnavailableError() {
        baseApiHandler.onFailure(
            PersonalIdOrConnectApiErrorCodes.TOKEN_UNAVAILABLE_ERROR,
            null
        )
    }

    override fun processTokenRequestDeniedError() {
        baseApiHandler.onFailure(
            PersonalIdOrConnectApiErrorCodes.TOKEN_DENIED_ERROR,
            null
        )
    }

    override fun processOldApiError() {
        baseApiHandler.onFailure(
            PersonalIdOrConnectApiErrorCodes.OLD_API_ERROR,
            null
        )
    }
}