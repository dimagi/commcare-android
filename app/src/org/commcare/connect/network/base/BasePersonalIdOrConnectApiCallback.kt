package org.commcare.connect.network.base

import android.widget.Toast
import org.commcare.CommCareApplication
import org.commcare.connect.network.IApiCallback
import org.commcare.connect.network.base.BasePersonalIdOrConnectApiHandler.PersonalIdOrConnectApiErrorCodes


import org.javarosa.core.io.StreamsUtil
import org.javarosa.core.services.Logger
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.io.InputStream

/**
 * This is base class for all API callbacks. It by default handles all error messages, no need
 * to define the error handling in all api handlers
 */
abstract class BasePersonalIdOrConnectApiCallback<T>(val basePersonalIdOrConnectApiHandler: BasePersonalIdOrConnectApiHandler<T>) :
    IApiCallback {


    override fun processFailure(responseCode: Int, errorResponse: InputStream?) {
        if (responseCode == 401) {
            basePersonalIdOrConnectApiHandler.onFailure(
                PersonalIdOrConnectApiErrorCodes.FAILED_AUTH_ERROR,
                null
            )
            return
        }

        if (responseCode == 403) {
            basePersonalIdOrConnectApiHandler.onFailure(
                PersonalIdOrConnectApiErrorCodes.FORBIDDEN_ERROR,
                null
            )
            return
        }

        if (responseCode == 429 || responseCode == 503) {
            basePersonalIdOrConnectApiHandler.onFailure(
                PersonalIdOrConnectApiErrorCodes.RATE_LIMIT_EXCEEDED_ERROR,
                null
            )
            return
        }

        if (responseCode == 500) {
            basePersonalIdOrConnectApiHandler.onFailure(
                PersonalIdOrConnectApiErrorCodes.SERVER_ERROR,
                null
            )
            return
        }

        val info = StringBuilder("Response $responseCode")
        if (errorResponse != null) {
            try {
                errorResponse.use { `in` ->
                    val json =
                        JSONObject(String(StreamsUtil.inputStreamToByteArray(`in`)))
                    if (json.has("error")) {
                        info.append(": ").append(json.optString("error"))
                        Toast.makeText(
                            CommCareApplication.instance(), json.optString("error"),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            } catch (e: JSONException) {
                Logger.exception("Error parsing API error response", e)
                basePersonalIdOrConnectApiHandler.onFailure(
                    PersonalIdOrConnectApiErrorCodes.UNKNOWN_ERROR,
                    e
                )
                return
            } catch (e: IOException) {
                Logger.exception("Error parsing API error response", e)
                basePersonalIdOrConnectApiHandler.onFailure(
                    PersonalIdOrConnectApiErrorCodes.UNKNOWN_ERROR,
                    e
                )
                return
            }
        }
        basePersonalIdOrConnectApiHandler.onFailure(
            PersonalIdOrConnectApiErrorCodes.UNKNOWN_ERROR,
            Exception(info.toString())
        )
    }

    override fun processNetworkFailure() {
        basePersonalIdOrConnectApiHandler.onFailure(
            PersonalIdOrConnectApiErrorCodes.NETWORK_ERROR,
            null
        )
    }

    override fun processTokenUnavailableError() {
        basePersonalIdOrConnectApiHandler.onFailure(
            PersonalIdOrConnectApiErrorCodes.TOKEN_UNAVAILABLE_ERROR,
            null
        )
    }

    override fun processTokenRequestDeniedError() {
        basePersonalIdOrConnectApiHandler.onFailure(
            PersonalIdOrConnectApiErrorCodes.TOKEN_DENIED_ERROR,
            null
        )
    }

    override fun processOldApiError() {
        basePersonalIdOrConnectApiHandler.onFailure(
            PersonalIdOrConnectApiErrorCodes.OLD_API_ERROR,
            null
        )
    }


}