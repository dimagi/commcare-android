package org.commcare.connect

import android.content.Context
import android.widget.Toast
import org.commcare.CommCareApplication
import org.commcare.android.database.connect.models.ConnectJobPaymentRecord
import org.commcare.android.database.connect.models.ConnectJobRecord
import org.commcare.connect.database.ConnectJobUtils
import org.commcare.connect.database.ConnectUserDatabaseUtil
import org.commcare.connect.network.connect.ConnectApiHandler
import org.commcare.connect.network.connect.models.DeliveryAppProgressResponseModel
import org.commcare.connect.network.connect.models.LearningAppProgressResponseModel
import org.commcare.connect.network.connectId.PersonalIdApiErrorHandler
import org.commcare.google.services.analytics.AnalyticsParamValue.FINISH_DELIVERY
import org.commcare.google.services.analytics.AnalyticsParamValue.PAID_DELIVERY
import org.commcare.google.services.analytics.AnalyticsParamValue.START_DELIVERY
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil

object ConnectJobHelper {
    fun getJobForSeatedApp(context: Context): ConnectJobRecord? {
        val appId = CommCareApplication.instance().currentApp.uniqueId
        val appRecord = ConnectJobUtils.getAppRecord(context, appId) ?: return null

        return ConnectJobUtils.getCompositeJob(context, appRecord.jobId)
    }

    fun shouldShowJobStatus(context: Context?, appId: String?): Boolean {
        val record = ConnectJobUtils.getAppRecord(context, appId) ?: return false
        val job = ConnectJobUtils.getJobForApp(context, appId) ?: return false

        //Only time not to show is when we're in learn app but job is in delivery state
        return !record.isLearning || job.status != ConnectJobRecord.STATUS_DELIVERING
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
                if (job.passedAssessment()){
                    FirebaseAnalyticsUtil.reportCccApiLearnProgress(true)
                }
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
                val events = mutableSetOf<String?>()

                if (deliveryAppProgressResponseModel.updatedJob) {
                    events.add(START_DELIVERY)
                    ConnectJobUtils.upsertJob(context, job)
                }

                if (deliveryAppProgressResponseModel.hasDeliveries) {
                    if (job.getDeliveryProgressPercentage() == 100) {
                        events.add(FINISH_DELIVERY)
                    }
                    ConnectJobUtils.storeDeliveries(context, job.deliveries, job.jobId, true)
                }

                if (deliveryAppProgressResponseModel.hasPayment) {
                    if (job.payments.isNotEmpty()){
                        events.add(PAID_DELIVERY)
                    }
                    ConnectJobUtils.storePayments(context, job.payments, job.jobId, true)
                }

                events.forEach { event ->
                    FirebaseAnalyticsUtil.reportCccApiDeliveryProgress(true, event)
                }

                listener.connectActivityComplete(true)
            }

            override fun onFailure(errorCode: PersonalIdOrConnectApiErrorCodes, t: Throwable?) {
                FirebaseAnalyticsUtil.reportCccApiDeliveryProgress(false,null)
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
