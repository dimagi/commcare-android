package org.commcare.pn.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.commcare.utils.PushNotificationApiHelper
import org.javarosa.core.services.Logger

class NotificationsRetrievalWorker(
    private val appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val MAX_RETRIES = 3
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        return@withContext try {
            val result = PushNotificationApiHelper.retrieveLatestPushNotifications(appContext)
            
            if (result.isSuccess) {
                val notifications = result.getOrNull()
                Logger.log("PushNotificationRetrievalWorker", 
                    "Successfully retrieved ${notifications?.size ?: 0} push notifications")
                Result.success()
            } else {
                val exception = result.exceptionOrNull()
                Logger.exception("PushNotificationRetrievalWorker failed", exception ?: Exception("Unknown error"))
                
                if (runAttemptCount < MAX_RETRIES) {
                    Result.retry()
                } else {
                    Result.failure()
                }
            }
        } catch (e: Exception) {
            Logger.exception("PushNotificationRetrievalWorker encountered unexpected error", e)
            Result.failure()
        }
    }
}
