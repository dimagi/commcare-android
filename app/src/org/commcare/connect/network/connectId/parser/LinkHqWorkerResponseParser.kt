package org.commcare.connect.network.connectId.parser

import android.content.Context
import org.commcare.android.database.connect.models.ConnectLinkedAppRecord
import org.commcare.connect.database.ConnectAppDatabaseUtil
import org.commcare.connect.network.base.BaseApiResponseParser
import java.io.InputStream

class LinkHqWorkerResponseParser<T>(
    val context: Context,
) : BaseApiResponseParser<T> {
    override fun parse(
        responseCode: Int,
        responseData: InputStream,
        anyInputObject: Any?,
    ): T {
        val appRecord: ConnectLinkedAppRecord = anyInputObject as ConnectLinkedAppRecord
        appRecord.setWorkerLinked(true)
        ConnectAppDatabaseUtil.storeApp(context, appRecord)
        return true as T
    }
}
