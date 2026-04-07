package org.commcare.connect.network.connectId.parser

import android.content.Context
import org.commcare.android.database.connect.models.ConnectMessagingChannelRecord
import org.commcare.connect.database.ConnectMessagingDatabaseHelper
import org.commcare.connect.network.base.BaseApiResponseParser
import org.javarosa.core.io.StreamsUtil
import org.json.JSONException
import org.json.JSONObject
import java.io.InputStream

class RetrieveChannelEncryptionKeyResponseParser<T>(
    val context: Context,
) : BaseApiResponseParser<T> {
    override fun parse(
        responseCode: Int,
        responseData: InputStream,
        anyInputObject: Any?,
    ): T {
        try {
            val channel = anyInputObject as ConnectMessagingChannelRecord
            val responseAsString =
                String(
                    StreamsUtil.inputStreamToByteArray(responseData),
                )
            if (responseAsString.isNotEmpty()) {
                val json = JSONObject(responseAsString)
                channel.setKey(json.getString("key"))
                ConnectMessagingDatabaseHelper.storeMessagingChannel(context, channel)
            }
        } catch (e: JSONException) {
            throw RuntimeException(e)
        }
        return true as T
    }
}
