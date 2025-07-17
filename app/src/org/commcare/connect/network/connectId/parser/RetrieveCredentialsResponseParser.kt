package org.commcare.connect.network.connectId.parser

import android.content.Context
import org.commcare.android.database.connect.models.PersonalIdCredential
import org.commcare.connect.database.ConnectAppDatabaseUtil.storeCredentialDataInTable
import org.commcare.connect.network.base.BaseApiResponseParser
import org.json.JSONArray
import java.io.InputStream

/**
 * Parser for retrieving credentials response
 */
class RetrieveCredentialsResponseParser<T>(private val context: Context) : BaseApiResponseParser<T> {

    override fun parse(responseCode: Int, responseData: InputStream): T {
        val jsonArray = JSONArray(responseData)
        val result = PersonalIdCredential.fromJsonArray(jsonArray)
        storeCredentialDataInTable(context, result.validCredentials)
        return result as T
    }
}