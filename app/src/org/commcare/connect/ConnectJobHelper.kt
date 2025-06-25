package org.commcare.connect

import android.content.Context
import android.widget.Toast
import org.commcare.android.database.connect.models.ConnectJobAssessmentRecord
import org.commcare.android.database.connect.models.ConnectJobDeliveryRecord
import org.commcare.android.database.connect.models.ConnectJobLearningRecord
import org.commcare.android.database.connect.models.ConnectJobPaymentRecord
import org.commcare.android.database.connect.models.ConnectJobRecord
import org.commcare.connect.database.ConnectJobUtils
import org.commcare.connect.database.ConnectUserDatabaseUtil
import org.commcare.connect.network.ApiConnect
import org.commcare.connect.network.ConnectNetworkHelper
import org.commcare.connect.network.IApiCallback
import org.commcare.dalvik.R
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil
import org.javarosa.core.io.StreamsUtil
import org.javarosa.core.model.utils.DateUtils
import org.javarosa.core.services.Logger
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.io.InputStream
import java.util.Date
import java.util.Locale

object ConnectJobHelper {
    var activeJob: ConnectJobRecord? = null

    fun setConnectJobForApp(context: Context?, appId: String?): ConnectJobRecord? {
        var job: ConnectJobRecord? = null
        val appRecord = ConnectJobUtils.getAppRecord(context, appId)
        if (appRecord != null) {
            job = ConnectJobUtils.getCompositeJob(context, appRecord.jobId)
        }
        activeJob = job
        return job
    }

    fun shouldShowJobStatus(context: Context?, appId: String?): Boolean {
        val record = ConnectJobUtils.getAppRecord(context, appId)
        if (record == null || activeJob == null) {
            return false
        }

        //Only time not to show is when we're in learn app but job is in delivery state
        return !record.isLearning || activeJob!!.status !== ConnectJobRecord.STATUS_DELIVERING
    }

    fun updateJobProgress(
        context: Context?,
        job: ConnectJobRecord,
        listener: ConnectManager.ConnectActivityCompleteListener
    ) {
        when (job.status) {
            ConnectJobRecord.STATUS_LEARNING -> {
                updateLearningProgress(context, job, listener)
            }

            ConnectJobRecord.STATUS_DELIVERING -> {
                updateDeliveryProgress(context, job, listener)
            }

            else -> {
                listener.connectActivityComplete(true)
            }
        }
    }

    fun updateLearningProgress(
        context: Context?,
        job: ConnectJobRecord,
        listener: ConnectManager.ConnectActivityCompleteListener
    ) {
        val user = ConnectUserDatabaseUtil.getUser(context)
        ApiConnect.getLearnProgress(context, user, job.jobId, object : IApiCallback {
            fun reportApiCall(success: Boolean) {
                FirebaseAnalyticsUtil.reportCccApiLearnProgress(success)
            }

            override fun processSuccess(responseCode: Int, responseData: InputStream) {
                try {
                    val responseAsString = String(StreamsUtil.inputStreamToByteArray(responseData))
                    if (responseAsString.length > 0) {
                        //Parse the JSON
                        val json = JSONObject(responseAsString)

                        var key = "completed_modules"
                        val modules = json.getJSONArray(key)
                        val learningRecords: MutableList<ConnectJobLearningRecord> =
                            ArrayList(modules.length())
                        for (i in 0..<modules.length()) {
                            val obj = modules[i] as JSONObject
                            val record = ConnectJobLearningRecord.fromJson(obj, job.jobId)
                            learningRecords.add(record)
                        }
                        job.learnings = learningRecords
                        job.completedLearningModules = learningRecords.size

                        key = "assessments"
                        val assessments = json.getJSONArray(key)
                        val assessmentRecords: MutableList<ConnectJobAssessmentRecord> =
                            ArrayList(assessments.length())
                        for (i in 0..<assessments.length()) {
                            val obj = assessments[i] as JSONObject
                            val record = ConnectJobAssessmentRecord.fromJson(obj, job.jobId)
                            assessmentRecords.add(record)
                        }
                        job.assessments = assessmentRecords

                        ConnectJobUtils.updateJobLearnProgress(context, job)
                    }
                } catch (e: JSONException) {
                    throw RuntimeException(e)
                } catch (e: IOException) {
                    Logger.exception("Parsing return from learn_progress request", e)
                }

                reportApiCall(true)
                listener.connectActivityComplete(true)
            }

            override fun processFailure(responseCode: Int, errorResponse: InputStream?) {
                Logger.log("ERROR", String.format(Locale.getDefault(), "Failed: %d", responseCode))
                reportApiCall(false)
                listener.connectActivityComplete(false)
            }

            override fun processNetworkFailure() {
                Logger.log("ERROR", "Failed (network)")
                reportApiCall(false)
                listener.connectActivityComplete(false)
            }

            override fun processTokenUnavailableError() {
                ConnectNetworkHelper.handleTokenUnavailableException(context)
                reportApiCall(false)
                listener.connectActivityComplete(false)
            }

            override fun processTokenRequestDeniedError() {
                ConnectNetworkHelper.handleTokenDeniedException()
                reportApiCall(false)
                listener.connectActivityComplete(false)
            }

            override fun processOldApiError() {
                ConnectNetworkHelper.showOutdatedApiError(context)
                reportApiCall(false)
                listener.connectActivityComplete(false)
            }
        })
    }

    fun updateDeliveryProgress(
        context: Context?,
        job: ConnectJobRecord,
        listener: ConnectManager.ConnectActivityCompleteListener
    ) {
        val user = ConnectUserDatabaseUtil.getUser(context)
        ApiConnect.getDeliveries(context, user, job.jobId, object : IApiCallback {
            fun reportApiCall(success: Boolean) {
                FirebaseAnalyticsUtil.reportCccApiDeliveryProgress(success)
            }

            override fun processSuccess(responseCode: Int, responseData: InputStream) {
                var success = true
                try {
                    val responseAsString = String(StreamsUtil.inputStreamToByteArray(responseData))
                    if (responseAsString.length > 0) {
                        //Parse the JSON
                        val json = JSONObject(responseAsString)

                        var updatedJob = false
                        var key = "max_payments"
                        if (json.has(key)) {
                            job.maxVisits = json.getInt(key)
                            updatedJob = true
                        }

                        key = "end_date"
                        if (json.has(key)) {
                            job.projectEndDate = DateUtils.parseDate(json.getString(key))
                            updatedJob = true
                        }

                        key = "payment_accrued"
                        if (json.has(key)) {
                            job.paymentAccrued = json.getInt(key)
                            updatedJob = true
                        }

                        key = "is_user_suspended"
                        if (json.has(key)) {
                            job.isUserSuspended = json.getBoolean(key)
                            updatedJob = true
                        }

                        if (updatedJob) {
                            job.lastDeliveryUpdate = Date()
                            ConnectJobUtils.upsertJob(context, job)
                        }

                        val deliveries: MutableList<ConnectJobDeliveryRecord> =
                            ArrayList(json.length())
                        key = "deliveries"
                        if (json.has(key)) {
                            val array = json.getJSONArray(key)
                            for (i in 0..<array.length()) {
                                val obj = array[i] as JSONObject
                                deliveries.add(ConnectJobDeliveryRecord.fromJson(obj, job.jobId))
                            }

                            //Store retrieved deliveries
                            ConnectJobUtils.storeDeliveries(context, deliveries, job.jobId, true)

                            job.deliveries = deliveries
                        }

                        val payments: MutableList<ConnectJobPaymentRecord> = ArrayList()
                        key = "payments"
                        if (json.has(key)) {
                            val array = json.getJSONArray(key)
                            for (i in 0..<array.length()) {
                                val obj = array[i] as JSONObject
                                payments.add(ConnectJobPaymentRecord.fromJson(obj, job.jobId))
                            }

                            ConnectJobUtils.storePayments(context, payments, job.jobId, true)

                            job.payments = payments
                        }
                    }
                } catch (e: JSONException) {
                    throw RuntimeException(e)
                } catch (e: IOException) {
                    Logger.exception("Parsing return from delivery progress request", e)
                    success = false
                }

                reportApiCall(success)
                listener.connectActivityComplete(success)
            }

            override fun processFailure(responseCode: Int, errorResponse: InputStream?) {
                reportApiCall(false)
                listener.connectActivityComplete(false)
            }

            override fun processNetworkFailure() {
                reportApiCall(false)
                listener.connectActivityComplete(false)
            }

            override fun processTokenUnavailableError() {
                ConnectNetworkHelper.handleTokenUnavailableException(context)
                reportApiCall(false)
                listener.connectActivityComplete(false)
            }

            override fun processTokenRequestDeniedError() {
                ConnectNetworkHelper.handleTokenDeniedException()
                reportApiCall(false)
                listener.connectActivityComplete(false)
            }

            override fun processOldApiError() {
                ConnectNetworkHelper.showOutdatedApiError(context)
                reportApiCall(false)
                listener.connectActivityComplete(false)
            }
        })
    }

    fun updatePaymentConfirmed(
        context: Context?,
        payment: ConnectJobPaymentRecord,
        confirmed: Boolean,
        listener: ConnectManager.ConnectActivityCompleteListener
    ) {
        val user = ConnectUserDatabaseUtil.getUser(context)
        ApiConnect.setPaymentConfirmed(
            context,
            user,
            payment.paymentId,
            confirmed,
            object : IApiCallback {
                fun reportApiCall(success: Boolean) {
                    FirebaseAnalyticsUtil.reportCccApiPaymentConfirmation(success)
                }

                override fun processSuccess(responseCode: Int, responseData: InputStream) {
                    payment.confirmed = confirmed
                    ConnectJobUtils.storePayment(context, payment)

                    //No need to report to user
                    reportApiCall(true)
                    listener.connectActivityComplete(true)
                }

                override fun processFailure(responseCode: Int, errorResponse: InputStream?) {
                    Toast.makeText(
                        context,
                        R.string.connect_payment_confirm_failed,
                        Toast.LENGTH_SHORT
                    ).show()
                    reportApiCall(false)
                    listener.connectActivityComplete(false)
                }

                override fun processNetworkFailure() {
                    Toast.makeText(
                        context,
                        R.string.connect_payment_confirm_failed,
                        Toast.LENGTH_SHORT
                    ).show()
                    reportApiCall(false)
                    listener.connectActivityComplete(false)
                }

                override fun processTokenUnavailableError() {
                    ConnectNetworkHelper.handleTokenUnavailableException(context)
                    reportApiCall(false)
                    listener.connectActivityComplete(false)
                }

                override fun processTokenRequestDeniedError() {
                    ConnectNetworkHelper.handleTokenDeniedException()
                    reportApiCall(false)
                    listener.connectActivityComplete(false)
                }

                override fun processOldApiError() {
                    ConnectNetworkHelper.showOutdatedApiError(context)
                    reportApiCall(false)
                    listener.connectActivityComplete(false)
                }
            })
    }
}