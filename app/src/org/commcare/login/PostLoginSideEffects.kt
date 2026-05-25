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

/**
 * Runs the deterministic side-effects that fire after every successful login.
 *
 * Excludes the UI-prompting branch (personalIdManager.checkPersonalIdLink). That branch
 * fires when PersonalID is logged in but no Connect job is associated with the app, and
 * it remains in LoginActivity until a later phase introduces a UI-routing layer.
 */
internal class PostLoginSideEffects(
    private val personalIdManager: PersonalIdManager = PersonalIdManager.getInstance(),
) {
    /**
     * @param activity the hosting CommCareActivity. Required because
     *   PersonalIdManager.updateAppAccess takes an activity (existing signature).
     */
    suspend fun runOnSuccess(
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

        val appId: String = CommCareApplication.instance().currentApp.uniqueId
        val job: ConnectJobRecord? = ConnectJobUtils.getJobForApp(activity, appId)
        CommCareApplication.instance().setConnectJobIdForAnalytics(job)

        if (job == null) {
            // The check-link branch is UI-bound; LoginActivity continues to drive it.
            return PostLoginOutcome(redirectToConnectOpportunityInfo = false)
        }

        personalIdManager.updateAppAccess(activity, appId, username)

        val updated =
            suspendCancellableCoroutine<Boolean> { cont ->
                val listener =
                    object : ConnectActivityCompleteListener {
                        override fun connectActivityComplete(
                            success: Boolean,
                            error: String?,
                        ) {
                            if (!cont.isCompleted) cont.resume(success)
                        }
                    }
                ConnectJobHelper.updateJobProgress(activity, job, listener)
            }

        return PostLoginOutcome(
            redirectToConnectOpportunityInfo = updated && job.isUserSuspended,
        )
    }
}
