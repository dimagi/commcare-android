package org.commcare.connect.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.commcare.android.database.connect.models.ConnectJobRecord
import org.commcare.android.database.connect.models.ConnectUserRecord
import org.commcare.connect.database.ConnectUserDatabaseUtil
import org.commcare.connect.database.JobStoreManager
import org.commcare.connect.network.ApiConnect
import org.commcare.connect.network.IApiCallback
import org.javarosa.core.io.StreamsUtil
import org.javarosa.core.services.Logger
import org.json.JSONArray
import org.json.JSONException
import java.io.InputStream
import kotlin.coroutines.resume


class ConnectOpportunitiesWorker(
        context: Context,
        workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val user = ConnectUserDatabaseUtil.getUser(applicationContext)

        val success = getConnectOpportunitiesSuspend(applicationContext, user)

        if (success) Result.success() else Result.retry()
    }

    private suspend fun getConnectOpportunitiesSuspend(
            context: Context,
            user: ConnectUserRecord
    ): Boolean = suspendCancellableCoroutine { cont ->
        ApiConnect.getConnectOpportunities(context, user, object : IApiCallback {
            override fun processSuccess(responseCode: Int, responseData: InputStream?) {
                try {
                    if (responseData == null) {
                        cont.resume(false)
                        return
                    }

                    val responseAsString = String(StreamsUtil.inputStreamToByteArray(responseData))
                    if (responseAsString.isNotEmpty()) {
                        val json = JSONArray(responseAsString)
                        val jobs = mutableListOf<ConnectJobRecord>()

                        for (i in 0 until json.length()) {
                            try {
                                val obj = json.getJSONObject(i)
                                val job = ConnectJobRecord.fromJson(obj)
                                jobs.add(job)
                            } catch (e: JSONException) {
                                Logger.exception("Parsing error", e)
                            }
                        }

                        JobStoreManager(context).storeJobs(context, jobs, true)
                    }
                    cont.resume(true)
                } catch (e: Exception) {
                    Logger.exception("Error during parsing or storing", e)
                    cont.resume(false)
                }
            }

            override fun processFailure(responseCode: Int, errorResponse: InputStream?) {
                Logger.log("ERROR", "Opportunities call failed: $responseCode")
                cont.resume(false)
            }

            override fun processNetworkFailure() {
                Logger.log("ERROR", "Network failure during opportunities call")
                cont.resume(false)
            }

            override fun processTokenUnavailableError() {
                Logger.log("ERROR", "Token unavailable during opportunities call")
                cont.resume(false)
            }

            override fun processTokenRequestDeniedError() {
                Logger.log("ERROR", "Token denied during opportunities call")
                cont.resume(false)
            }

            override fun processOldApiError() {
                Logger.log("ERROR", "Old API error during opportunities call")
                cont.resume(false)
            }
        })
    }
}

