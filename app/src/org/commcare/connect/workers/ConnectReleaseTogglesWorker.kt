package org.commcare.connect.workers

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import org.commcare.android.database.connect.models.ConnectReleaseToggleRecord
import org.commcare.connect.PersonalIdManager
import org.commcare.connect.network.connectId.PersonalIdApiHandler
import org.javarosa.core.services.Logger
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class ConnectReleaseTogglesWorker(
    val context: Context,
    workerParameters: WorkerParameters,
) : CoroutineWorker(context, workerParameters) {
    override suspend fun doWork(): Result {
        val personalIdManager = PersonalIdManager.getInstance()
        if (!personalIdManager.isloggedIn()) {
            return Result.failure()
        }

        val user = personalIdManager.getUser(context)

        return suspendCoroutine { continuation ->
            object : PersonalIdApiHandler<List<ConnectReleaseToggleRecord>>() {
                override fun onFailure(
                    errorCode: PersonalIdOrConnectApiErrorCodes,
                    t: Throwable?,
                ) {
                    Logger.exception("Failed to get feature release toggles in background!", t)

                    if (errorCode == PersonalIdOrConnectApiErrorCodes.NETWORK_ERROR) {
                        continuation.resume(Result.retry())
                    } else {
                        continuation.resume(Result.failure())
                    }
                }

                override fun onSuccess(data: List<ConnectReleaseToggleRecord>) {
                    continuation.resume(Result.success())
                }
            }.getReleaseToggles(context, user.userId, user.password)
        }
    }

    companion object {
        private const val WORK_NAME = "connect_release_toggles_fetch_worker"

        fun scheduleOneTimeFetch(context: Context) {
            val constraints =
                Constraints
                    .Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .setRequiresBatteryNotLow(true)
                    .build()

            val workRequest =
                OneTimeWorkRequest
                    .Builder(ConnectReleaseTogglesWorker::class.java)
                    .setConstraints(constraints)
                    .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.KEEP,
                workRequest,
            )
        }
    }
}
