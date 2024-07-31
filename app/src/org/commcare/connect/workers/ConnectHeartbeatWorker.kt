package org.commcare.connect.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.commcare.connect.ConnectManager
import org.commcare.connect.network.ApiConnectId

class ConnectHeartbeatWorker(context: Context, workerParams: WorkerParameters) :
    CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        return withContext(Dispatchers.IO) {
            if (!ConnectManager.isConnectIdConfigured()) {
                return@withContext Result.failure()
            }

            //NOTE: Using trad'l code route instead until we can get the commented code to work
            //val connectNetworkService = ConnectNetworkServiceFactory.createConnectIdNetworkSerive()
            //val fcmToken = FirebaseMessagingUtil.getFCMToken();
            //val requestBody = HeartBeatBody(fcmToken)
            //val response = connectNetworkService.makeHeartbeatRequest(requestBody)!!.execute()
            //return@withContext if (response.isSuccessful) Result.success() else Result.failure()

            val result = ApiConnectId.makeHeartbeatRequestSync(applicationContext);
            return@withContext if (result.responseCode in 200..299) Result.success() else Result.failure()
        }
    }
}
