package org.commcare.connect.network.base

import org.commcare.connect.network.IApiCallback
import org.commcare.interfaces.base.BaseConnectView
import org.javarosa.core.services.Logger
import org.json.JSONException
import java.io.IOException
import java.io.InputStream

/**
 * Base class for all API handlers
 */
abstract class BaseApiHandler<T>(
    val loading: Boolean? = null,
    val view: BaseConnectView? = null,
) {
    abstract fun onSuccess(data: T)

    abstract fun onFailure(
        errorCode: PersonalIdOrConnectApiErrorCodes,
        t: Throwable?,
    )

    enum class PersonalIdApiSubErrorCodes {
        INTEGRITY_HEADERS,
        PHONE_NUMBER_REQUIRED,
        UNLICENSED_APP_ERROR,
        DEVICE_INTEGRITY_ERROR,
        APP_INTEGRITY_ERROR,
        INTEGRITY_REQUEST_ERROR,
    }

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
        INTEGRITY_ERROR,
        BAD_REQUEST_ERROR, ;

        fun shouldAllowRetry(): Boolean =
            this == NETWORK_ERROR || (this == TOKEN_UNAVAILABLE_ERROR) || (this == SERVER_ERROR) || (this == UNKNOWN_ERROR) ||
                (this == INTEGRITY_ERROR)
    }

    fun createCallback(
        parser: BaseApiResponseParser<T>,
        anyInputObject: Any? = null,
    ): IApiCallback {
        onStart()
        return object : BaseApiCallback<T>(this) {
            override fun processSuccess(
                responseCode: Int,
                responseData: InputStream,
            ) {
                try {
                    onSuccess(parser.parse(responseCode, responseData, anyInputObject))
                    onStop()
                } catch (e: JSONException) {
                    Logger.exception("JSON error parsing API response", e)
                    stopLoadingAndInformError(
                        PersonalIdOrConnectApiErrorCodes.JSON_PARSING_ERROR,
                        e,
                    )
                } catch (e: IOException) {
                    Logger.exception("Error parsing API response", e)
                    stopLoadingAndInformError(PersonalIdOrConnectApiErrorCodes.NETWORK_ERROR, e)
                }
            }
        }
    }

    fun onStart() {
        loading?.let {
            if (loading) view?.showLoading()
        }
    }

    fun onStop() {
        loading?.let {
            if (loading) view?.hideLoading()
        }
    }

    fun stopLoadingAndInformError(
        errorCode: PersonalIdOrConnectApiErrorCodes,
        t: Throwable?,
    ) {
        onStop()
        onFailure(errorCode, t)
    }
}
