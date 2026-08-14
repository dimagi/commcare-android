package org.commcare.connect

import android.content.Context
import org.commcare.CommCareApplication
import org.commcare.android.database.connect.models.ConnectJobRecord
import org.commcare.connect.database.ConnectJobUtils

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

    fun resolveGenericOpportunityDestination(
        currentAction: String?,
        job: ConnectJobRecord?,
        paymentUuid: String?,
    ): String? {
        if (ConnectConstants.CCC_GENERIC_OPPORTUNITY != currentAction || job == null) {
            return currentAction
        }
        return when (job.status) {
            ConnectJobRecord.STATUS_DELIVERING -> {
                if (!paymentUuid.isNullOrEmpty()) {
                    ConnectConstants.CCC_DEST_PAYMENTS
                } else {
                    ConnectConstants.CCC_DEST_DELIVERY_PROGRESS
                }
            }

            ConnectJobRecord.STATUS_LEARNING -> {
                ConnectConstants.CCC_DEST_LEARN_PROGRESS
            }

            ConnectJobRecord.STATUS_AVAILABLE,
            ConnectJobRecord.STATUS_AVAILABLE_NEW,
            -> {
                ConnectConstants.CCC_DEST_OPPORTUNITY_SUMMARY_PAGE
            }

            else -> {
                currentAction
            }
        }
    }
}
