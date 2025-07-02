package org.commcare.connect.network.base

import org.commcare.connect.network.IApiCallback
import org.commcare.connect.network.base.BaseApiHandler.PersonalIdOrConnectApiErrorCodes


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
abstract class BaseApiCallback<T>(val baseApiHandler: BaseApiHandler<T>) :
    IApiCallback {


    override fun processFailure(responseCode: Int, errorResponse: InputStream?) {
        if (responseCode == 401) {
            baseApiHandler.onFailure(
                PersonalIdOrConnectApiErrorCodes.FAILED_AUTH_ERROR,
                null
            )
            return
        }

        if (responseCode == 403) {
            if (errorResponse != null){
                try {
                    errorResponse.use {
                        val json = JSONObject(String(StreamsUtil.inputStreamToByteArray(it), Charsets.UTF_8))
                        if (json.has("error_code")){
                            val errorCode = json.optString("error_code")
                            if (errorCode.equals("LOCKED_ACCOUNT", ignoreCase = true)) {
                                baseApiHandler.onFailure(
                                    PersonalIdOrConnectApiErrorCodes.ACCOUNT_LOCKED_ERROR,
                                    null
                                )
                                return
                            }
                        }
                    }
                } catch (e: Exception) {
                    Logger.exception("Error parsing LOCKED ACCOUNT", e)
                }
            }
            baseApiHandler.onFailure(PersonalIdOrConnectApiErrorCodes.FORBIDDEN_ERROR, null)
            return
        }

        if (responseCode == 429 || responseCode == 503) {
            baseApiHandler.onFailure(
                PersonalIdOrConnectApiErrorCodes.RATE_LIMIT_EXCEEDED_ERROR,
                null
            )
            return
        }

        if (responseCode == 500) {
            baseApiHandler.onFailure(
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
                        JSONObject(String(StreamsUtil.inputStreamToByteArray(`in`), Charsets.UTF_8))
                    if (json.has("error")) {
                        val errorMessage = json.optString("error")
                        info.append(": ").append(errorMessage)
                        baseApiHandler.onFailure(
                            PersonalIdOrConnectApiErrorCodes.UNKNOWN_ERROR,
                            Exception(errorMessage)
                        )
                        return
                    }
                }
            } catch (e: JSONException) {
                Logger.exception("Error parsing API error response", e)
                baseApiHandler.onFailure(
                    PersonalIdOrConnectApiErrorCodes.UNKNOWN_ERROR,
                    e
                )
                return
            } catch (e: IOException) {
                Logger.exception("Error parsing API error response", e)
                baseApiHandler.onFailure(
                    PersonalIdOrConnectApiErrorCodes.UNKNOWN_ERROR,
                    e
                )
                return
            }
        }
        baseApiHandler.onFailure(
            PersonalIdOrConnectApiErrorCodes.UNKNOWN_ERROR,
            Exception(info.toString())
        )
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