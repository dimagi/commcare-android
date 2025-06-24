package org.commcare.connect.network.connect

import android.content.Context
import org.commcare.connect.network.ApiPersonalId
import org.commcare.connect.network.IApiCallback
import org.commcare.connect.network.base.BasePersonalIdOrConnectApiCallback
import org.commcare.connect.network.base.BasePersonalIdOrConnectApiHandler
import org.commcare.connect.network.connect.parser.ConnectApiResponseParser
import org.commcare.connect.network.connect.parser.RetrieveCredentialsResponseParser
import org.javarosa.core.services.Logger
import org.json.JSONException
import java.io.IOException
import java.io.InputStream

/**
 * Base class for all connect api handlers
 */
open abstract class ConnectApiHandler<T> : BasePersonalIdOrConnectApiHandler<T>() {


    private fun createCallback(
        parser: ConnectApiResponseParser<T>
    ): IApiCallback {
        return object : BasePersonalIdOrConnectApiCallback<T>(this) {
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


    fun retrieveCredentials(context: Context, userName: String, password: String) {
        ApiPersonalId.retrieveCredentials(
            context, userName, password, createCallback(
                RetrieveCredentialsResponseParser<T>()
            )
        )
    }


}