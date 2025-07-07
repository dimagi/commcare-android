package org.commcare.connect.network.base

import org.commcare.connect.network.IApiCallback
import org.javarosa.core.services.Logger
import org.json.JSONException
import java.io.IOException
import java.io.InputStream

/**
 * Base class for all API handlers
 */
abstract class BaseApiHandler<T> {

    abstract fun onSuccess(data: T)

    abstract fun onFailure(errorCode: PersonalIdOrConnectApiErrorCodes, t: Throwable?)


    enum class PersonalIdOrConnectApiErrorCodes {
        UNKNOWN_ERROR,
        NETWORK_ERROR,
        OLD_API_ERROR,
        FORBIDDEN_ERROR,
        TOKEN_UNAVAILABLE_ERROR,
        TOKEN_DENIED_ERROR,
        INVALID_RESPONSE_ERROR,
        JSON_PARSING_ERROR,
        FAILED_AUTH_ERROR,
        SERVER_ERROR,
        RATE_LIMIT_EXCEEDED_ERROR,
        ACCOUNT_LOCKED_ERROR,
        INTEGRITY_ERROR;

        fun shouldAllowRetry(): Boolean {
            return this == NETWORK_ERROR || (this == TOKEN_UNAVAILABLE_ERROR) || (this == SERVER_ERROR
                    ) || (this == UNKNOWN_ERROR) || (this == INTEGRITY_ERROR)
        }
    }


    fun createCallback(
        parser: BaseApiResponseParser<T>
    ): IApiCallback {
        return object : BaseApiCallback<T>(this) {
            override fun processSuccess(responseCode: Int, responseData: InputStream) {
                try {
                    onSuccess(parser.parse(responseCode,responseData))
                } catch (e: JSONException) {
                    Logger.exception("JSON error parsing API response", e)
                    onFailure(PersonalIdOrConnectApiErrorCodes.JSON_PARSING_ERROR, e)
                } catch (e: IOException) {
                    Logger.exception("Error parsing API response", e)
                    onFailure(PersonalIdOrConnectApiErrorCodes.NETWORK_ERROR, e)
                }

            }
        }
    }


}