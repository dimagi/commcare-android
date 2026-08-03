package org.commcare.connect

import android.content.Context
import org.commcare.AppUtils
import org.commcare.CommCareApplication
import org.commcare.android.database.connect.models.ConnectJobRecord
import org.commcare.connect.database.ConnectJobUtils
import org.commcare.connect.database.ConnectUserDatabaseUtil
import org.commcare.connect.network.PersonalIdOrConnectApiErrorHandler
import org.commcare.connect.network.base.BaseApiHandler.PersonalIdOrConnectApiErrorCodes
import org.commcare.connect.network.connect.ConnectApiHandler
import org.commcare.dalvik.R
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil

/**
 * Claims an opportunity so the worker can start delivering, skipping the server call if the job is
 * already claimed.
 *
 * Callers navigate from [OnClaimedListener], which reports whether the delivery app is installed.
 */
class ConnectJobClaimController(
    private val context: Context,
) {
    fun interface OnClaimedListener {
        fun onClaimed(deliveryAppInstalled: Boolean)
    }

    fun interface OnErrorListener {
        fun onError(message: String)
    }

    fun claimIfNeededAndProceed(
        job: ConnectJobRecord,
        onClaimed: OnClaimedListener,
        onError: OnErrorListener,
    ) {
        val deliveryAppInstalled = AppUtils.isAppInstalled(job.deliveryAppInfo.appId)

        if (job.status == ConnectJobRecord.STATUS_DELIVERING) {
            proceed(job, deliveryAppInstalled, onClaimed)
            return
        }

        val user = ConnectUserDatabaseUtil.getUser(context)

        object : ConnectApiHandler<Boolean>() {
            override fun onSuccess(success: Boolean) {
                FirebaseAnalyticsUtil.reportCccApiClaimJob(true)
                proceed(job, deliveryAppInstalled, onClaimed)
            }

            override fun onFailure(
                errorCode: PersonalIdOrConnectApiErrorCodes,
                t: Throwable?,
            ) {
                FirebaseAnalyticsUtil.reportCccApiClaimJob(false)
                onError.onError(errorMessage(errorCode, t))
            }
        }.claimJob(context, user, job.jobUUID)
    }

    private fun proceed(
        job: ConnectJobRecord,
        deliveryAppInstalled: Boolean,
        onClaimed: OnClaimedListener,
    ) {
        job.status = ConnectJobRecord.STATUS_DELIVERING
        ConnectJobUtils.upsertJob(job)
        CommCareApplication.instance().closeUserSession()
        onClaimed.onClaimed(deliveryAppInstalled)
    }

    private fun errorMessage(
        errorCode: PersonalIdOrConnectApiErrorCodes,
        t: Throwable?,
    ): String =
        if (errorCode == PersonalIdOrConnectApiErrorCodes.BAD_REQUEST_ERROR) {
            context.getString(R.string.recovery_unable_to_claim_opportunity)
        } else {
            PersonalIdOrConnectApiErrorHandler.handle(context, errorCode, t)
        }
}
