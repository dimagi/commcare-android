package org.commcare.connect.network.connectId.parser

import org.commcare.android.database.connect.models.PushNotificationRecord
import org.commcare.connect.network.base.BaseApiResponseParser
import org.json.JSONArray
import java.io.InputStream

/**
 * Parser for retrieving notification response
 */
class RetrieveNotificationsResponseParser<T>() : BaseApiResponseParser<T> {

    override fun parse(responseCode: Int, responseData: InputStream, anyInputObject: Any?): T {
        val jsonText = responseData.bufferedReader().use { it.readText() }
        val jsonArray = JSONArray(jsonText)
        val result = PushNotificationRecord.fromJsonArray(jsonArray)
        return result as T
    }
}
