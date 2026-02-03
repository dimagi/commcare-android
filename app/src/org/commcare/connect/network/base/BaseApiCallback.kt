package org.commcare.connect.network.base

import org.commcare.activities.FormEntryActivity
import org.commcare.connect.database.ConnectDatabaseHelper
import org.commcare.connect.network.ConnectNetworkHelper
import org.commcare.connect.network.IApiCallback
import org.commcare.connect.network.base.BaseApiHandler.PersonalIdOrConnectApiErrorCodes
import org.commcare.utils.GlobalErrorUtil
import org.commcare.utils.GlobalErrors
import org.javarosa.core.services.Logger

/**
 * This is base class for all API callbacks. It by default handles all error messages, no need
 * to define the error handling in all api handlers
 */
abstract class BaseApiCallback<T>(
    val baseApiHandler: BaseApiHandler<T>,
) : IApiCallback {
    override fun processFailure(
        responseCode: Int,
        url: String?,
        errorBody: String,
    ) {
        // Common error_code handler used before checking error response code
        when (responseCode) {
            401 ->
                baseApiHandler.stopLoadingAndInformError(
                    PersonalIdOrConnectApiErrorCodes.FAILED_AUTH_ERROR,
                    null,
                )

            403 ->
                baseApiHandler.stopLoadingAndInformError(
                    PersonalIdOrConnectApiErrorCodes.FORBIDDEN_ERROR,
                    null,
                )

            429 ->
                baseApiHandler.stopLoadingAndInformError(
                    PersonalIdOrConnectApiErrorCodes.RATE_LIMIT_EXCEEDED_ERROR,
                    null,
                )

            400 -> {
                if (FormEntryActivity.mFormController == null &&
                    ConnectNetworkHelper.checkForLoginFromDifferentDevice(errorBody))
                {
                    GlobalErrorUtil.triggerGlobalError(
                        GlobalErrors.PERSONALID_LOGIN_FROM_DIFFERENT_DEVICE_ERROR
                    )
                }
                baseApiHandler.stopLoadingAndInformError(
                    PersonalIdOrConnectApiErrorCodes.BAD_REQUEST_ERROR,
                    null,
                )
            }

            in 500..509 ->
                baseApiHandler.stopLoadingAndInformError(
                    PersonalIdOrConnectApiErrorCodes.SERVER_ERROR,
                    null,
                )

            else -> {
                val exception =
                    Exception("Encountered response code $responseCode for url ${url ?: "url not found"}")
                Logger.exception("Unknown http response code", exception)
                baseApiHandler.stopLoadingAndInformError(
                    PersonalIdOrConnectApiErrorCodes.UNKNOWN_ERROR,
                    exception,
                )
            }
        }
    }

    override fun processNetworkFailure() {
        baseApiHandler.stopLoadingAndInformError(
            PersonalIdOrConnectApiErrorCodes.NETWORK_ERROR,
            null,
        )
    }

    override fun processTokenUnavailableError() {
        baseApiHandler.stopLoadingAndInformError(
            PersonalIdOrConnectApiErrorCodes.TOKEN_UNAVAILABLE_ERROR,
            null,
        )
    }

    override fun processTokenRequestDeniedError() {
        baseApiHandler.stopLoadingAndInformError(
            PersonalIdOrConnectApiErrorCodes.TOKEN_DENIED_ERROR,
            null,
        )
    }

    override fun processOldApiError() {
        baseApiHandler.stopLoadingAndInformError(
            PersonalIdOrConnectApiErrorCodes.OLD_API_ERROR,
            null,
        )
    }
}
