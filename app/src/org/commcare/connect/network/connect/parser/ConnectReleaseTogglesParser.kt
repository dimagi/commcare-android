package org.commcare.connect.network.connect.parser

import android.content.Context
import org.commcare.android.database.connect.models.ConnectReleaseToggleRecord
import org.commcare.connect.database.ConnectAppDatabaseUtil
import org.commcare.connect.network.base.BaseApiResponseParser
import org.json.JSONObject
import java.io.InputStream

class ConnectReleaseTogglesParser : BaseApiResponseParser<List<ConnectReleaseToggleRecord>> {
    override fun parse(
        responseCode: Int,
        responseData: InputStream,
        anyInputObject: Any?,
    ): List<ConnectReleaseToggleRecord> {
        val responseJsonString = responseData.bufferedReader().use { it.readText() }
        val responseJson = JSONObject(responseJsonString)
        val togglesJson = responseJson.getJSONObject("toggles")

        return ConnectReleaseToggleRecord.releaseTogglesFromJson(togglesJson).also {
            ConnectAppDatabaseUtil.storeReleaseToggles(anyInputObject as Context, it)
        }
    }
}
