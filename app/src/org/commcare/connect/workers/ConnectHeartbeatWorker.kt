package org.commcare.connect.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import org.commcare.connect.PersonalIdManager
import org.commcare.connect.network.connectId.PersonalIdApiHandler
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class ConnectHeartbeatWorker(val context: Context, workerParams: WorkerParameters) :
        CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val personalIdManager = PersonalIdManager.getInstance()
        if (!personalIdManager.isloggedIn()) {
            return Result.failure()
        }
        val user = personalIdManager.getUser(applicationContext)

        return suspendCoroutine { continuation ->
            object : PersonalIdApiHandler<Boolean>() {
                override fun onFailure(errorCode: PersonalIdOrConnectApiErrorCodes, t: Throwable?) {
                    continuation.resume(Result.failure())
                }

                override fun onSuccess(success: Boolean) {
                    continuation.resume(Result.success())
                }
            }.heartbeatRequest(context, user)
        }
    }
}
