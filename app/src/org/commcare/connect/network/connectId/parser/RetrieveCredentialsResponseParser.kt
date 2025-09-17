package org.commcare.connect.network.connectId.parser

import android.content.Context
import org.commcare.android.database.connect.models.PersonalIdWorkHistory
import org.commcare.connect.database.ConnectAppDatabaseUtil.storeCredentialDataInTable
import org.commcare.connect.network.base.BaseApiResponseParser
import org.json.JSONObject
import java.io.InputStream

/**
 * Parser for retrieving credentials response
 */
class RetrieveCredentialsResponseParser<T>(private val context: Context) : BaseApiResponseParser<T> {

    override fun parse(responseCode: Int, responseData: InputStream, anyInputObject: Any?): T {
        val jsonText = responseData.bufferedReader().use { it.readText() }
        val jsonObject = JSONObject(jsonText)
        val jsonArray = jsonObject.getJSONArray("credentials")
        val result = PersonalIdWorkHistory.fromJsonArray(jsonArray)
        storeCredentialDataInTable(context, result)
        return result as T
    }
}
