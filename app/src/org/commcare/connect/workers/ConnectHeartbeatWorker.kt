package org.commcare.connect.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.commcare.connect.PersonalIdManager
import org.commcare.connect.network.ApiPersonalId
import org.commcare.connect.network.ConnectSsoHelper

class ConnectHeartbeatWorker(context: Context, workerParams: WorkerParameters) :
        CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        return withContext(Dispatchers.IO) {
            if (!PersonalIdManager.getInstance().isloggedIn()) {
                return@withContext Result.failure()
            }

            //First, need to tell Connect we're starting learning so it can create a user on HQ
            val user = PersonalIdManager.getInstance().getUser(applicationContext)
            val auth = ConnectSsoHelper.retrieveConnectIdTokenSync(applicationContext, user)
            val result = ApiPersonalId.makeHeartbeatRequestSync(applicationContext, auth)
            return@withContext if (result.responseCode in 200..299) Result.success() else Result.failure()
        }
    }
}
