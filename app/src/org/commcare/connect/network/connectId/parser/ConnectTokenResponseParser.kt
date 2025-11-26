package org.commcare.connect.network.connectId.parser

import org.commcare.android.database.connect.models.ConnectUserRecord
import org.commcare.connect.ConnectConstants.CONNECT_KEY_EXPIRES
import org.commcare.connect.ConnectConstants.CONNECT_KEY_TOKEN
import org.commcare.connect.network.base.BaseApiResponseParser
import org.commcare.core.network.AuthInfo.TokenAuth
import org.javarosa.core.io.StreamsUtil
import org.json.JSONException
import org.json.JSONObject
import java.io.InputStream
import java.util.Date

class ConnectTokenResponseParser<T>() : BaseApiResponseParser<T> {
    override fun parse(responseCode: Int, responseData: InputStream, anyInputObject: Any?): T {
        try {
            responseData.use {
                val responseAsString = String(
                    StreamsUtil.inputStreamToByteArray(
                        responseData
                    )
                )

                val json = JSONObject(responseAsString)
                val token = json.getString(CONNECT_KEY_TOKEN)
                check(!token.isEmpty() && token != "null") { "$CONNECT_KEY_TOKEN cannot be null or empty" }
                val seconds = json.optInt(CONNECT_KEY_EXPIRES, 0)
                check(seconds >= 0) { "$CONNECT_KEY_EXPIRES cannot be negative" }
                val expiration = Date()
                expiration.time = expiration.time + (seconds.toLong() * 1000)
                (anyInputObject as ConnectUserRecord).updateConnectToken(token, expiration)
                return TokenAuth(token) as T
            }

        } catch (e: JSONException) {
            throw RuntimeException(e)
        }
    }
}
