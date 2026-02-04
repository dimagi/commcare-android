package org.commcare.connect

import android.content.Context
import android.widget.Toast
import org.commcare.CommCareApplication
import org.commcare.android.database.connect.models.ConnectJobRecord
import org.commcare.connect.database.ConnectJobUtils
import org.commcare.connect.database.ConnectUserDatabaseUtil
import org.commcare.connect.network.PersonalIdOrConnectApiErrorHandler
import org.commcare.connect.network.connect.ConnectApiHandler
import org.commcare.connect.network.connect.models.ConnectOpportunitiesResponseModel
import org.commcare.connect.network.connect.models.ConnectPaymentConfirmationModel
import org.commcare.connect.network.connect.models.DeliveryAppProgressResponseModel
import org.commcare.connect.network.connect.models.LearningAppProgressResponseModel
import org.commcare.google.services.analytics.AnalyticsParamValue.FINISH_DELIVERY
import org.commcare.google.services.analytics.AnalyticsParamValue.PAID_DELIVERY
import org.commcare.google.services.analytics.AnalyticsParamValue.START_DELIVERY
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil
import org.commcare.interfaces.base.BaseConnectView

object ConnectJobHelper {
    fun getJobForSeatedApp(context: Context): ConnectJobRecord? {
        val appId = CommCareApplication.instance().currentApp.uniqueId
        val appRecord = ConnectJobUtils.getAppRecord(context, appId) ?: return null

        return ConnectJobUtils.getCompositeJob(context, appRecord.jobUUID)
    }

    fun shouldShowJobStatus(
        context: Context?,
        appId: String?,
    ): Boolean {
        val record = ConnectJobUtils.getAppRecord(context, appId) ?: return false
        val job = ConnectJobUtils.getJobForApp(context, appId) ?: return false

        // Only time not to show is when we're in learn app but job is in delivery state
        return !record.isLearning || job.status != ConnectJobRecord.STATUS_DELIVERING
    }

    fun updateJobProgress(
        context: Context,
        job: ConnectJobRecord,
        showLoading: Boolean? = null,
        baseConnectView: BaseConnectView? = null,
        listener: ConnectActivityCompleteListener,
    ) {
        when (job.status) {
            ConnectJobRecord.STATUS_LEARNING -> {
                updateLearningProgress(context, job, listener)
            }

            ConnectJobRecord.STATUS_DELIVERING -> {
                updateDeliveryProgress(context, job, showLoading, baseConnectView, listener)
            }

            else -> {
                listener.connectActivityComplete(true)
            }
        }
    }

    fun updateLearningProgress(
        context: Context,
        job: ConnectJobRecord,
        listener: ConnectActivityCompleteListener,
    ) {
        val user = ConnectUserDatabaseUtil.getUser(context)
        object : ConnectApiHandler<LearningAppProgressResponseModel>() {
            override fun onSuccess(learningAppProgressResponseModel: LearningAppProgressResponseModel) {
                job.learnings = learningAppProgressResponseModel.connectJobLearningRecords
                job.completedLearningModules =
                    learningAppProgressResponseModel.connectJobLearningRecords.size
                job.assessments = learningAppProgressResponseModel.connectJobAssessmentRecords
                ConnectJobUtils.updateJobLearnProgress(context, job)
                if (job.passedAssessment()) {
                    FirebaseAnalyticsUtil.reportCccApiLearnProgress(true)
                }
                listener.connectActivityComplete(true)
            }

            override fun onFailure(
                errorCode: PersonalIdOrConnectApiErrorCodes,
                t: Throwable?,
            ) {
                FirebaseAnalyticsUtil.reportCccApiLearnProgress(false)
                listener.connectActivityComplete(false)
            }
        }.getLearningAppProgress(context, user, job)
    }

    fun updateDeliveryProgress(
        context: Context,
        job: ConnectJobRecord,
        showLoading: Boolean? = null,
        baseConnectView: BaseConnectView? = null,
        listener: ConnectActivityCompleteListener,
    ) {
        val user = ConnectUserDatabaseUtil.getUser(context)
        object : ConnectApiHandler<DeliveryAppProgressResponseModel>(showLoading, baseConnectView) {
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
                    ConnectJobUtils.storeDeliveries(context, job.deliveries, job.jobUUID, true)
                }

                if (deliveryAppProgressResponseModel.hasPayment) {
                    if (job.payments.isNotEmpty()) {
                        events.add(PAID_DELIVERY)
                    }
                    ConnectJobUtils.storePayments(context, job.payments, job.jobUUID, true)
                }

                events.forEach { event ->
                    FirebaseAnalyticsUtil.reportCccApiDeliveryProgress(true, event)
                }

                listener.connectActivityComplete(true)
            }

            override fun onFailure(
                errorCode: PersonalIdOrConnectApiErrorCodes,
                t: Throwable?,
            ) {
                FirebaseAnalyticsUtil.reportCccApiDeliveryProgress(false, null)
                listener.connectActivityComplete(false)
            }
        }.getDeliveries(context, user, job)
    }

    fun updatePaymentsConfirmed(
        context: Context,
        paymentConfirmations: List<ConnectPaymentConfirmationModel>,
        listener: ConnectActivityCompleteListener,
    ) {
        val user = ConnectUserDatabaseUtil.getUser(context)

        object : ConnectApiHandler<Boolean>() {
            override fun onSuccess(data: Boolean) {
                for (paymentConfirmation in paymentConfirmations) {
                    paymentConfirmation.payment.confirmed = paymentConfirmation.toConfirm
                    ConnectJobUtils.storePayment(context, paymentConfirmation.payment)
                }

                FirebaseAnalyticsUtil.reportCccApiPaymentConfirmation(true)
                listener.connectActivityComplete(true)
            }

            override fun onFailure(
                errorCode: PersonalIdOrConnectApiErrorCodes,
                t: Throwable?,
            ) {
                Toast
                    .makeText(
                        context,
                        PersonalIdOrConnectApiErrorHandler.handle(context, errorCode, t),
                        Toast.LENGTH_LONG,
                    ).show()
                FirebaseAnalyticsUtil.reportCccApiPaymentConfirmation(false)
                listener.connectActivityComplete(false)
            }
        }.setPaymentConfirmations(context, user, paymentConfirmations)
    }

    fun retrieveOpportunities(
        context: Context,
        listener: ConnectActivityCompleteListener,
    ) {
        val user = ConnectUserDatabaseUtil.getUser(context)
        object : ConnectApiHandler<ConnectOpportunitiesResponseModel?>() {
            override fun onFailure(
                errorCode: PersonalIdOrConnectApiErrorCodes,
                t: Throwable?,
            ) {
                listener.connectActivityComplete(false)
            }

            override fun onSuccess(data: ConnectOpportunitiesResponseModel?) {
                listener.connectActivityComplete(true)
            }
        }.getConnectOpportunities(context, user!!)
    }
}
