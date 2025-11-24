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
        // A catch-all for errors with an unusual response code.
        UNKNOWN_ERROR,
        // Typical network errors like not having an internet connection.
        NETWORK_ERROR,
        // The app is outdated and needs to be updated.
        OLD_API_ERROR,
        // Examples of this include the user being in an unsupported country, an uninvited user
        // receiving an OTP, or an integrity failure.
        FORBIDDEN_ERROR,
        // The request to get an SSO auth token failed, usually due to network issues.
        TOKEN_UNAVAILABLE_ERROR,
        // The request to get an SSO auth token is denied.
        TOKEN_DENIED_ERROR,
        // The firebase ID token is invalid.
        TOKEN_INVALID_ERROR,
        INVALID_RESPONSE_ERROR,
        // There was an issue parsing an API response.
        JSON_PARSING_ERROR,
        // Examples of this include the user enterring an incorrect OTP, account lockout, or an
        // expired SSO auth token.
        FAILED_AUTH_ERROR,
        // Examples of this include a user profile photo failing to upload or the photo being too
        // large.
        SERVER_ERROR,
        // There were too many request attempts.
        RATE_LIMIT_EXCEEDED_ERROR,
        // The user enetered an incorrect backup code too many times.
        ACCOUNT_LOCKED_ERROR,
        // The user's device did not pass security checks.
        INTEGRITY_ERROR,
        // There is something wrong with the API request such as a lost PersonalID configuration.
        BAD_REQUEST_ERROR,
        // The user's backup code is not set correctly.
        NO_RECOVERY_PIN_SET_ERROR,
        // There's missing data in the API request.
        MISSING_DATA_ERROR,
        // There was a phone number mismatch when vaildating the firebase ID token.
        PHONE_MISMATCH_ERROR,
        // The firebase ID token was missing when validating it.
        MISSING_TOKEN_ERROR,
        // There was an issue verifying the firebase ID token.
        FAILED_VALIDATING_TOKEN_ERROR,
        // The user's name is missing from the API request.
        NAME_REQUIRED_ERROR,
        // The user attempted to create a new profile with a phone number that's already tied to an
        // existing account.
        ACTIVE_USER_EXISTS_ERROR, ;

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
