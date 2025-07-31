package org.commcare.connect

import android.content.Context
import android.widget.Toast
import org.commcare.android.database.connect.models.ConnectJobPaymentRecord
import org.commcare.android.database.connect.models.ConnectJobRecord
import org.commcare.connect.database.ConnectJobUtils
import org.commcare.connect.database.ConnectUserDatabaseUtil
import org.commcare.connect.network.connect.ConnectApiHandler
import org.commcare.connect.network.connect.models.DeliveryAppProgressResponseModel
import org.commcare.connect.network.connect.models.LearningAppProgressResponseModel
import org.commcare.connect.network.connectId.PersonalIdApiErrorHandler
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil

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
        context: Context,
        job: ConnectJobRecord,
        listener: ConnectActivityCompleteListener
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
        context: Context,
        job: ConnectJobRecord,
        listener: ConnectActivityCompleteListener
    ) {
        val user = ConnectUserDatabaseUtil.getUser(context)
        object : ConnectApiHandler<LearningAppProgressResponseModel>() {
            override fun onSuccess(learningAppProgressResponseModel: LearningAppProgressResponseModel) {
                job.learnings = learningAppProgressResponseModel.connectJobLearningRecords
                job.completedLearningModules =
                    learningAppProgressResponseModel.connectJobLearningRecords.size
                job.assessments = learningAppProgressResponseModel.connectJobAssessmentRecords
                ConnectJobUtils.updateJobLearnProgress(context, job)
                FirebaseAnalyticsUtil.reportCccApiLearnProgress(true)
                listener.connectActivityComplete(true)
            }

            override fun onFailure(errorCode: PersonalIdOrConnectApiErrorCodes, t: Throwable?) {
                FirebaseAnalyticsUtil.reportCccApiLearnProgress(false)
                listener.connectActivityComplete(false)
            }
        }.getLearningAppProgress(context, user, job.jobId)
    }

    fun updateDeliveryProgress(
        context: Context,
        job: ConnectJobRecord,
        listener: ConnectActivityCompleteListener
    ) {
        val user = ConnectUserDatabaseUtil.getUser(context)
        object : ConnectApiHandler<DeliveryAppProgressResponseModel>() {
            override fun onSuccess(deliveryAppProgressResponseModel: DeliveryAppProgressResponseModel) {
                if (deliveryAppProgressResponseModel.updatedJob) {
                    ConnectJobUtils.upsertJob(context, job)
                }

                if (deliveryAppProgressResponseModel.hasDeliveries) {
                    ConnectJobUtils.storeDeliveries(context, job.deliveries, job.jobId, true)
                }

                if (deliveryAppProgressResponseModel.hasPayment) {
                    ConnectJobUtils.storePayments(context, job.payments, job.jobId, true)
                }

                FirebaseAnalyticsUtil.reportCccApiDeliveryProgress(true)
                listener.connectActivityComplete(true)
            }

            override fun onFailure(errorCode: PersonalIdOrConnectApiErrorCodes, t: Throwable?) {
                FirebaseAnalyticsUtil.reportCccApiDeliveryProgress(false)
                listener.connectActivityComplete(false)
            }
        }.getDeliveries(context, user, job)
    }

    fun updatePaymentConfirmed(
        context: Context,
        payment: ConnectJobPaymentRecord,
        confirmed: Boolean,
        listener: ConnectActivityCompleteListener
    ) {
        val user = ConnectUserDatabaseUtil.getUser(context)


        object : ConnectApiHandler<Boolean>() {
            override fun onSuccess(success: Boolean) {

                payment.confirmed = confirmed
                ConnectJobUtils.storePayment(context, payment)
                FirebaseAnalyticsUtil.reportCccApiPaymentConfirmation(true)
                listener.connectActivityComplete(true)
            }

            override fun onFailure(errorCode: PersonalIdOrConnectApiErrorCodes, t: Throwable?) {
                Toast.makeText(
                    context,
                    PersonalIdApiErrorHandler.handle(context, errorCode, t),
                    Toast.LENGTH_LONG
                ).show()
                FirebaseAnalyticsUtil.reportCccApiPaymentConfirmation(false)
                listener.connectActivityComplete(false)
            }
        }.setPaymentConfirmation(context, user, payment.paymentId, confirmed)
    }
}
