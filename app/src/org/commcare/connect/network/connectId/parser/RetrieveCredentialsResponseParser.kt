package org.commcare.connect.network.connectId.parser

import org.commcare.android.database.connect.models.PersonalIdCredential
import org.commcare.connect.network.PersonalIdOrConnectApiResponseParser
import org.json.JSONArray
import java.io.InputStream

/**
 * Parser for retrieving credentials response
 */
class RetrieveCredentialsResponseParser<T>() : PersonalIdOrConnectApiResponseParser<T> {

    override fun parse(responseCode: Int, responseData: InputStream): T {
        val jsonArray = JSONArray(responseData)
        return PersonalIdCredential.fromJsonArray(jsonArray) as T
    }
}