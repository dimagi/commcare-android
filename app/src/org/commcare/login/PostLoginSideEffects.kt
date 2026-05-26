package org.commcare.login

import kotlinx.coroutines.suspendCancellableCoroutine
import org.commcare.CommCareApplication
import org.commcare.activities.CommCareActivity
import org.commcare.activities.LoginActivity
import org.commcare.android.database.connect.models.ConnectJobRecord
import org.commcare.connect.ConnectActivityCompleteListener
import org.commcare.connect.ConnectJobHelper
import org.commcare.connect.PersonalIdManager
import org.commcare.connect.database.ConnectJobUtils
import org.commcare.utils.CrashUtil
import kotlin.coroutines.resume

internal open class PostLoginSideEffects(
    private val personalIdManager: PersonalIdManager = PersonalIdManager.getInstance(),
) {
    open suspend fun runOnSuccess(
        activity: CommCareActivity<*>,
        username: String,
    ): PostLoginOutcome {
        CrashUtil.registerUserData()
        CommCareApplication
            .notificationManager()
            .clearNotifications(LoginActivity.NOTIFICATION_MESSAGE_LOGIN)

        if (!personalIdManager.isloggedIn()) {
            return PostLoginOutcome(redirectToConnectOpportunityInfo = false)
        }

        val appId = CommCareApplication.instance().currentApp.uniqueId
        val job = ConnectJobUtils.getJobForApp(activity, appId)
        CommCareApplication.instance().setConnectJobIdForAnalytics(job)

        if (job == null) {
            return PostLoginOutcome(redirectToConnectOpportunityInfo = false)
        }

        personalIdManager.updateAppAccess(activity, appId, username)

        val updated =
            suspendCancellableCoroutine { continuation ->
                val listener =
                    object : ConnectActivityCompleteListener {
                        override fun connectActivityComplete(
                            success: Boolean,
                            error: String?,
                        ) {
                            if (!continuation.isCompleted) continuation.resume(success)
                        }
                    }
                ConnectJobHelper.updateJobProgress(activity, job, listener)
            }

        return PostLoginOutcome(
            redirectToConnectOpportunityInfo = updated && job.isUserSuspended,
        )
    }
}
