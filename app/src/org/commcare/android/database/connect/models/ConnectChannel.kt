package org.commcare.android.database.connect.models

import org.commcare.android.storage.framework.Persisted
import org.commcare.models.framework.Persisting
import org.commcare.modern.database.Table
import org.commcare.modern.models.MetaField
import java.io.Serializable

@Table(ConnectChannel.STORAGE_KEY)
class ConnectChannel : Persisted(), Serializable {

    @Persisting(1)
    @MetaField(META_CHANNEL_ID)
    var channelId: Int = 0

    @Persisting(2)
    @MetaField(META_CHANNEL_NAME)
    var channelName: String = ""

    companion object {
        const val STORAGE_KEY = "connect_channel"

        const val META_CHANNEL_ID = "channel_id"
        const val META_CHANNEL_NAME = "channel_name"
    }
}