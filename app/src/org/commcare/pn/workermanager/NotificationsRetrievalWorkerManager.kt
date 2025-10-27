package org.commcare.pn.workermanager

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import org.commcare.connect.PersonalIdManager
import org.commcare.pn.workers.NotificationsRetrievalWorker
import java.util.concurrent.TimeUnit

/**
 * Manager class for scheduling periodic notification retrieval
 */
object NotificationsRetrievalWorkerManager {

    private const val PUSH_NOTIFICATION_RETRIEVAL_WORKER_TAG = "push_notification_retrieval_worker"
    private const val PERIODICITY_FOR_RETRIEVAL_IN_HOURS = 1L
    private const val BACKOFF_DELAY_FOR_RETRIEVAL_RETRY = 10 * 60 * 1000L // 10 minutes
    private const val NOTIFICATION_RETRIEVAL_PERIODIC_REQUEST_NAME = "notification_retrieval_periodic_request"
    private const val NOTIFICATION_RETRIEVAL_ONE_TIME_REQUEST_NAME = "notification_retrieval_one_time_request"

    /**
     * Schedules periodic push notification retrieval to run every hour only if Personal ID login is present
     * @param context Application context
     */
    @JvmStatic
    fun schedulePeriodicPushNotificationRetrievalChecked(context: Context) {
        val perosnalIdManager = PersonalIdManager.getInstance()
        perosnalIdManager.init(context)
        if (perosnalIdManager.isloggedIn()) {
            schedulePeriodicPushNotificationRetrieval(context)
        }
    }

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
            NOTIFICATION_RETRIEVAL_PERIODIC_REQUEST_NAME,
            ExistingPeriodicWorkPolicy.REPLACE,
            retrievalRequest
        )
    }

    /**
     * Schedules immediate notification retrieval as a one time task
     * @param context Application context
     */
    fun scheduleImmediateNotificationRetrieval(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val retrievalRequest = OneTimeWorkRequest.Builder(NotificationsRetrievalWorker::class.java)
            .addTag(PUSH_NOTIFICATION_RETRIEVAL_WORKER_TAG)
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                BACKOFF_DELAY_FOR_RETRIEVAL_RETRY,
                TimeUnit.MILLISECONDS
            )
            .build()

        // Use REPLACE policy to ensure only one notification retrieval runs
        // even when multiple sync works complete concurrently
        WorkManager.getInstance(context).enqueueUniqueWork(
            NOTIFICATION_RETRIEVAL_ONE_TIME_REQUEST_NAME,
            ExistingWorkPolicy.REPLACE,
            retrievalRequest
        )
    }

    /**
     * Cancels the periodic push notification retrieval work
     * @param context Application context
     */
    fun cancelPeriodicPushNotificationRetrieval(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(
            NOTIFICATION_RETRIEVAL_PERIODIC_REQUEST_NAME
        )
    }
}
