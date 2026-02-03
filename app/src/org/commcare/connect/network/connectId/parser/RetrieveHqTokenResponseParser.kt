package org.commcare.connect.network.connectId.parser

import android.content.Context
import org.commcare.CommCareApplication
import org.commcare.connect.ConnectConstants.CONNECT_KEY_EXPIRES
import org.commcare.connect.ConnectConstants.CONNECT_KEY_TOKEN
import org.commcare.connect.database.ConnectDatabaseHelper
import org.commcare.connect.network.SsoToken
import org.commcare.connect.network.base.BaseApiResponseParser
import org.commcare.core.network.AuthInfo.TokenAuth
import org.javarosa.core.io.StreamsUtil
import org.json.JSONException
import org.json.JSONObject
import java.io.InputStream
import java.util.Date

class RetrieveHqTokenResponseParser<T>(
    val context: Context,
) : BaseApiResponseParser<T> {
    override fun parse(
        responseCode: Int,
        responseData: InputStream,
        anyInputObject: Any?,
    ): T {
        val hqUsername: String = anyInputObject as String
        try {
            val responseAsString =
                String(
                    StreamsUtil.inputStreamToByteArray(
                        responseData,
                    ),
                )
            val json = JSONObject(responseAsString)
            val token = json.getString(CONNECT_KEY_TOKEN)
            val expiration = Date()
            val seconds = if (json.has(CONNECT_KEY_EXPIRES)) json.getInt(CONNECT_KEY_EXPIRES) else 0
            expiration.setTime(expiration.getTime() + (seconds.toLong() * 1000))

            val seatedAppId = CommCareApplication.instance().getCurrentApp().getUniqueId()
            val ssoToken = SsoToken(token, expiration)
            ConnectDatabaseHelper.storeHqToken(context, seatedAppId, hqUsername, ssoToken)

            return TokenAuth(token) as T
        } catch (e: JSONException) {
            throw RuntimeException(e)
        }
    }
}
