package org.commcare.connect.workers

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.commcare.connectId.ConnectIDManager
import org.commcare.connect.network.ApiConnectId
import org.javarosa.core.services.Logger

class ConnectHeartbeatWorker(context: Context, workerParams: WorkerParameters) :
    CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        return withContext(Dispatchers.IO) {
                    try {
                        if (!ConnectIDManager.isLoggedIN()) {
                            Log.w(TAG, "Connect ID not configured, skipping heartbeat")
                            return@withContext Result.failure()
                        }
                        //NOTE: Using trad'l code route instead until we can get the commented code to work
                        //val connectNetworkService = ConnectNetworkServiceFactory.createConnectIdNetworkSerive()
                        //val fcmToken = FirebaseMessagingUtil.getFCMToken();
                        //val requestBody = HeartBeatBody(fcmToken)
                        //val response = connectNetworkService.makeHeartbeatRequest(requestBody)!!.execute()
                        //return@withContext if (response.isSuccessful) Result.success() else Result.failure()

                        val result = ApiConnectId.makeHeartbeatRequestSync(applicationContext)
                        return@withContext if (result.responseCode in 200..299) {
                            Result.success()
                        } else {
                            Logger.log(TAG, "Heartbeat failed with response code: ${result.responseCode}")
                            Result.failure()
                        }
                    } catch (e: Exception) {
                        Logger.log(TAG, "Error during heartbeat: $e")
                        return@withContext Result.failure()
                    }


        }
    }
    private companion object {
        private const val TAG = "ConnectHeartbeatWorker"
    }
}
