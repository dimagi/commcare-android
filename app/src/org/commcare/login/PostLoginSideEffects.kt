package org.commcare.login

import android.content.Context
import kotlinx.coroutines.suspendCancellableCoroutine
import org.commcare.CommCareApplication
import org.commcare.activities.LoginActivity
import org.commcare.connect.ConnectActivityCompleteListener
import org.commcare.connect.ConnectJobHelper
import org.commcare.connect.PersonalIdManager
import org.commcare.connect.database.ConnectJobUtils
import org.commcare.utils.CrashUtil

internal open class PostLoginSideEffects(
    private val context: Context,
    private val personalIdManager: PersonalIdManager = PersonalIdManager.getInstance(),
) {
    open suspend fun runOnSuccess(username: String): PostLoginOutcome {
        CrashUtil.registerUserData()
        CommCareApplication
            .notificationManager()
            .clearNotifications(LoginActivity.NOTIFICATION_MESSAGE_LOGIN)

        if (!personalIdManager.isloggedIn()) {
            return PostLoginOutcome(redirectToConnectOpportunityInfo = false)
        }

        val appId = CommCareApplication.instance().currentApp.uniqueId
        val job = ConnectJobUtils.getJobForApp(context, appId)
        CommCareApplication.instance().setConnectJobIdForAnalytics(job)

        personalIdManager.updateAppAccess(context, appId, username)

        if (job == null) {
            return PostLoginOutcome(redirectToConnectOpportunityInfo = false)
        }

        val updated =
            suspendCancellableCoroutine { continuation ->
                val listener =
                    object : ConnectActivityCompleteListener {
                        override fun connectActivityComplete(
                            success: Boolean,
                            error: String?,
                        ) {
                            continuation.resumeOnce(success)
                        }
                    }
                ConnectJobHelper.updateJobProgress(context, job, listener)
            }

        return PostLoginOutcome(
            redirectToConnectOpportunityInfo = updated && job.isUserSuspended,
        )
    }
}
