package org.commcare.connect.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import org.commcare.connect.PersonalIdSessionManager
import org.commcare.connect.database.ConnectUserDatabaseUtil
import org.commcare.connect.network.personalId.PersonalIdApiHandler
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class ConnectHeartbeatWorker(
    val context: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(context, workerParams) {
    override suspend fun doWork(): Result {
        val personalIdSessionManager = PersonalIdSessionManager.getInstance()
        if (!personalIdSessionManager.isloggedIn()) {
            return Result.failure()
        }
        val user = ConnectUserDatabaseUtil.getUser()

        return suspendCoroutine { continuation ->
            object : PersonalIdApiHandler<Boolean>() {
                override fun onFailure(
                    errorCode: PersonalIdOrConnectApiErrorCodes,
                    t: Throwable?,
                ) {
                    continuation.resume(Result.failure())
                }

                override fun onSuccess(success: Boolean) {
                    continuation.resume(Result.success())
                }
            }.heartbeatRequest(context, user)
        }
    }
}
