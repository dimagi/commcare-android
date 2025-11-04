package org.commcare.pn.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import org.commcare.CommCareApplication
import org.commcare.connect.MessageManager
import org.commcare.connect.database.ConnectMessagingDatabaseHelper
import org.commcare.pn.workermanager.NotificationsSyncWorkerManager.Companion.schedulePushNotificationRetrievalWith

class MessagingChannelsKeySyncWorker(val context: Context, workerParams: WorkerParameters) :
    CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val existingChannels = ConnectMessagingDatabaseHelper.getMessagingChannels(context)
        var needReloadDueToMissingChannelKey = false
        existingChannels?.let {
            existingChannels.filter { it.consented && it.key.isEmpty() }.map {
                needReloadDueToMissingChannelKey = true
                MessageManager.getChannelEncryptionKey(context, it, null)
            }
        }
        if (needReloadDueToMissingChannelKey) {
            schedulePushNotificationRetrievalWith(CommCareApplication.instance(), 3000)
        }
        return Result.success()
    }
}