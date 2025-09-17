package org.commcare.android.database.connect.models

import org.commcare.android.storage.framework.Persisted
import org.commcare.models.framework.Persisting
import org.commcare.modern.database.Table
import org.commcare.modern.models.MetaField
import java.io.Serializable

@Table(ConnectMessage.STORAGE_KEY)
class ConnectMessage : Persisted(), Serializable {

    @Persisting(1)
    @MetaField(META_MESSAGE_ID)
    var messageId: Int = 0

    @Persisting(2)
    @MetaField(META_STATUS)
    var status: String = ""

    @Persisting(3)
    @MetaField(META_CIPHERTEXT)
    var ciphertext: String = ""

    @Persisting(4)
    @MetaField(META_TAG)
    var tag: String = ""

    @Persisting(5)
    @MetaField(META_TIMESTAMP)
    var timestamp: String = ""

    @Persisting(6)
    @MetaField(META_NONCE)
    var nonce: String = ""

    @Persisting(7)
    @MetaField(META_CHANNEL)
    var channel: Int = 0

    @Persisting(8)
    @MetaField(META_CHANNEL_NAME)
    var channelName: String = ""

    companion object {
        const val STORAGE_KEY = "connect_message"

        const val META_MESSAGE_ID = "message_id"
        const val META_STATUS = "status"
        const val META_CIPHERTEXT = "ciphertext"
        const val META_TAG = "tag"
        const val META_TIMESTAMP = "timestamp"
        const val META_NONCE = "nonce"
        const val META_CHANNEL = "channel"
        const val META_CHANNEL_NAME = "channel_name"
    }
}