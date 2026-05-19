package org.commcare.connect

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.commcare.CommCareApplication
import org.commcare.android.database.connect.models.ConnectJobRecord
import org.commcare.connect.database.ConnectJobUtils
import org.commcare.connect.database.ConnectUserDatabaseUtil
import org.commcare.connect.network.PersonalIdOrConnectApiErrorHandler
import org.commcare.connect.network.connect.ConnectApiHandler
import org.commcare.connect.network.connect.ConnectNetworkClient
import org.commcare.connect.network.connect.models.ConnectPaymentConfirmationModel
import org.commcare.connect.network.connect.models.applyToJob
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
        val user = ConnectUserDatabaseUtil.getUser(context)!!
        CoroutineScope(Dispatchers.IO).launch {
            val result = ConnectNetworkClient.getInstance().getLearningProgress(user, job)
            result.fold(
                onSuccess = { model ->
                    model.applyToJob(job, context)
                    if (job.passedAssessment()) {
                        FirebaseAnalyticsUtil.reportCccApiLearnProgress(true)
                    }
                    withContext(Dispatchers.Main) {
                        listener.connectActivityComplete(true)
                    }
                },
                onFailure = {
                    FirebaseAnalyticsUtil.reportCccApiLearnProgress(false)
                    withContext(Dispatchers.Main) {
                        listener.connectActivityComplete(false)
                    }
                },
            )
        }
    }

    fun updateDeliveryProgress(
        context: Context,
        job: ConnectJobRecord,
        showLoading: Boolean? = null,
        baseConnectView: BaseConnectView? = null,
        listener: ConnectActivityCompleteListener,
    ) {
        val user = ConnectUserDatabaseUtil.getUser(context)!!
        CoroutineScope(Dispatchers.IO).launch {
            val result = ConnectNetworkClient.getInstance().getDeliveryProgress(user, job)
            result.fold(
                onSuccess = { model ->
                    val events = mutableSetOf<String?>()
                    if (model.updatedJob) {
                        events.add(START_DELIVERY)
                    }
                    if (model.hasDeliveries && job.getDeliveryProgressPercentage() == 100) {
                        events.add(FINISH_DELIVERY)
                    }
                    if (model.hasPayment && job.payments.isNotEmpty()) {
                        events.add(PAID_DELIVERY)
                    }

                    model.applyToJob(job, context)

                    events.forEach { event ->
                        FirebaseAnalyticsUtil.reportCccApiDeliveryProgress(true, event)
                    }
                    withContext(Dispatchers.Main) {
                        listener.connectActivityComplete(true)
                    }
                },
                onFailure = {
                    FirebaseAnalyticsUtil.reportCccApiDeliveryProgress(false, null)
                    withContext(Dispatchers.Main) {
                        listener.connectActivityComplete(false)
                    }
                },
            )
        }
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
                val error = PersonalIdOrConnectApiErrorHandler.handle(context, errorCode, t)
                FirebaseAnalyticsUtil.reportCccApiPaymentConfirmation(false)
                listener.connectActivityComplete(false, error)
            }
        }.setPaymentConfirmations(context, user, paymentConfirmations)
    }

    fun retrieveOpportunities(
        context: Context,
        listener: ConnectActivityCompleteListener,
    ) {
        val user = ConnectUserDatabaseUtil.getUser(context)
        object : ConnectApiHandler<List<ConnectJobRecord>>() {
            override fun onFailure(
                errorCode: PersonalIdOrConnectApiErrorCodes,
                t: Throwable?,
            ) {
                listener.connectActivityComplete(false)
            }

            override fun onSuccess(data: List<ConnectJobRecord>) {
                listener.connectActivityComplete(true)
            }
        }.getConnectOpportunities(context, user!!)
    }
}
