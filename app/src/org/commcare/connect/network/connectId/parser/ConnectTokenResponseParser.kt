package org.commcare.connect.network.connectId.parser

import org.commcare.android.database.connect.models.ConnectUserRecord
import org.commcare.connect.ConnectConstants
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
                var key = ConnectConstants.CONNECT_KEY_TOKEN
                val token = json.getString(key)
                val expiration = Date()
                key = ConnectConstants.CONNECT_KEY_EXPIRES
                val seconds = if (json.has(key)) json.getInt(key) else 0
                expiration.time = expiration.time + (seconds.toLong() * 1000)
                (anyInputObject as ConnectUserRecord).updateConnectToken(token, expiration)
                return TokenAuth(token) as T
            }

        } catch (e: JSONException) {
            throw RuntimeException(e)
        }
    }
}