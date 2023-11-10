package org.commcare.connect.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.commcare.activities.connect.ConnectManager
import org.commcare.connect.network.ConnectNetworkServiceFactory
import org.commcare.connect.network.HeartBeatBody
import org.commcare.utils.FirebaseMessagingUtil

class ConnectHeartbeatWorker(context: Context, workerParams: WorkerParameters) :
    CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        return withContext(Dispatchers.IO) {
            if (!ConnectManager.isUnlocked()) {
                return@withContext Result.failure()
            }
            val connectNetworkService = ConnectNetworkServiceFactory.createConnectIdNetworkSerive()
            val fcmToken = FirebaseMessagingUtil.getFCMToken()
            val requestBody = HeartBeatBody(fcmToken)
            val response = connectNetworkService.makeHeartbeatRequest(requestBody)!!.execute()
            return@withContext if (response.isSuccessful) Result.success() else Result.failure()
        }
    }
}
