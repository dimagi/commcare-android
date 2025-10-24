package org.commcare.pn.workermanager

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import org.commcare.pn.workers.NotificationsRetrievalWorker
import java.util.concurrent.TimeUnit

/**
 * Manager class for scheduling periodic notification retrieval
 */
object NotificationsRetrievalWorkerManager {
    
    private const val PUSH_NOTIFICATION_RETRIEVAL_WORKER_TAG = "push_notification_retrieval_worker"
    private const val PERIODICITY_FOR_RETRIEVAL_IN_HOURS = 1L
    private const val BACKOFF_DELAY_FOR_RETRIEVAL_RETRY = 10 * 60 * 1000L // 10 minutes
    private const val PUSH_NOTIFICATION_RETRIEVAL_REQUEST_NAME = "push_notification_retrieval_periodic_request"
    
    /**
     * Schedules periodic push notification retrieval to run every hour
     * @param context Application context
     */
    fun schedulePeriodicPushNotificationRetrieval(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()

        val retrievalRequest = PeriodicWorkRequest.Builder(
            NotificationsRetrievalWorker::class.java,
            PERIODICITY_FOR_RETRIEVAL_IN_HOURS,
            TimeUnit.HOURS
        )
            .addTag(PUSH_NOTIFICATION_RETRIEVAL_WORKER_TAG)
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                BACKOFF_DELAY_FOR_RETRIEVAL_RETRY,
                TimeUnit.MILLISECONDS
            )
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PUSH_NOTIFICATION_RETRIEVAL_REQUEST_NAME,
            ExistingPeriodicWorkPolicy.REPLACE,
            retrievalRequest
        )
    }
    
    /**
     * Cancels the periodic push notification retrieval work
     * @param context Application context
     */
    fun cancelPeriodicPushNotificationRetrieval(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(PUSH_NOTIFICATION_RETRIEVAL_REQUEST_NAME)
    }
}
