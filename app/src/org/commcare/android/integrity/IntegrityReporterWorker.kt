package org.commcare.android.integrity

import android.content.Context
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.android.play.core.integrity.StandardIntegrityException
import com.google.common.base.Strings
import org.commcare.android.database.connect.models.PersonalIdSessionData
import org.commcare.android.integrity.IntegrityTokenApiRequestHelper.Companion.fetchIntegrityToken
import org.commcare.android.logging.ReportingUtils
import org.commcare.connect.network.connectId.PersonalIdApiHandler
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class IntegrityReporterWorker(appContext: Context, workerParams: WorkerParameters) : CoroutineWorker(appContext, workerParams) {
    companion object {
        const val KEY_REQUEST_ID = "request_id"
        const val INTEGRITY_REQUEST_ID: String = "integrity-request-id"

        @JvmStatic
        fun launch(context: Context, requestId: String) {
            val constraints: Constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()
            val inputData = Data.Builder()
                .putString(KEY_REQUEST_ID, requestId)
                .build()
            val workRequest = OneTimeWorkRequest.Builder(IntegrityReporterWorker::class.java)
                .setConstraints(constraints)
                .setInputData(inputData)
                .build()
            WorkManager.getInstance(context).enqueue(workRequest)
        }
    }

    override suspend fun doWork(): Result {
        val context = applicationContext
        val requestId = inputData.getString(KEY_REQUEST_ID) ?: return Result.failure()
        if(Strings.isNullOrEmpty(requestId)) {
            return Result.failure()
        }

        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        val alreadyProcessed = preferences.getBoolean(getIntegrityRequestIdKey(requestId), false)
        if (alreadyProcessed) {
            return Result.success()
        }

        val body = HashMap<String, String>()
        body["request_id"] = requestId
        body["cc_device_id"] = ReportingUtils.getDeviceId()

        val jsonBody = org.json.JSONObject(body as Map<*, *>).toString()
        val requestHash = org.commcare.utils.HashUtils.computeHash(jsonBody, org.commcare.utils.HashUtils.HashAlgorithm.SHA256)
        val tokenResult = fetchIntegrityToken(requestHash)
        val (integrityToken, hash) = tokenResult.getOrElse {
            val errorCode: String = if(it is StandardIntegrityException) {
                it.errorCode.toString();
            } else {
                it.message ?: "Unknown error"
            }

            FirebaseAnalyticsUtil.reportPersonalIdIntegritySubmission(requestId, errorCode)

            body["device_error"] = errorCode
            makeReportIntegrityCall(context, null, null, body, requestId)

            return Result.failure()
        }

        val success = makeReportIntegrityCall(context, integrityToken, hash, body, requestId)
        if (success) {
            //Store requestID so we don't process it again
            preferences.edit {
                putBoolean(getIntegrityRequestIdKey(requestId), true)
            }
        }

        return if (success) Result.success() else Result.failure()
    }

    private suspend fun makeReportIntegrityCall(
        context: Context,
        integrityToken: String?,
        requestHash: String?,
        body: Map<String, String>,
        requestId: String
    ): Boolean = suspendCoroutine { cont ->
        val handler = object : PersonalIdApiHandler<PersonalIdSessionData?>() {
            override fun onSuccess(data: PersonalIdSessionData?) {
                cont.resume(true)
            }
            override fun onFailure(errorCode: PersonalIdOrConnectApiErrorCodes, t: Throwable?) {
                FirebaseAnalyticsUtil.reportPersonalIdIntegritySubmission(requestId, "SendError")
                cont.resume(false)
            }
        }
        handler.makeIntegrityReportCall(context, requestId, body, integrityToken, requestHash)
    }

    private fun getIntegrityRequestIdKey(requestId: String): String {
        return INTEGRITY_REQUEST_ID + "_" + requestId
    }
}