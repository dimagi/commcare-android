package org.commcare.connect.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.commcare.connect.ConnectIDManager
import org.commcare.connect.network.ApiConnectId
import org.commcare.connect.network.ConnectSsoHelper

class ConnectHeartbeatWorker(context: Context, workerParams: WorkerParameters) :
        CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        return withContext(Dispatchers.IO) {
            if (!ConnectIDManager.getInstance().isLoggedIN()) {
                return@withContext Result.failure()
            }

            //First, need to tell Connect we're starting learning so it can create a user on HQ
            val user = ConnectIDManager.getInstance().getUser(applicationContext)
            val auth = ConnectSsoHelper.retrieveConnectIdTokenSync(applicationContext, user)
            val result = ApiConnectId.makeHeartbeatRequestSync(applicationContext, auth)
            return@withContext if (result.responseCode in 200..299) Result.success() else Result.failure()
        }
    }
}
