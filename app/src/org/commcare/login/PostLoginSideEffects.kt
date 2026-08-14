package org.commcare.login

import android.content.Context
import org.commcare.CommCareApplication
import org.commcare.activities.LoginActivity
import org.commcare.connect.ConnectAppUtils
import org.commcare.connect.PersonalIdManager
import org.commcare.connect.database.ConnectJobUtils
import org.commcare.connect.repository.ConnectRepository
import org.commcare.utils.CrashUtil

/**
 * Runs the deterministic post-success chain (analytics, notification clears, Connect job update)
 * and returns the [PostLoginOutcome] routing signals.
 */
internal class PostLoginSideEffects(
    private val context: Context,
    private val personalIdManager: PersonalIdManager = PersonalIdManager.getInstance(),
    private val repository: ConnectRepository = ConnectRepository.getInstance(context),
) {
    suspend fun runOnSuccess(username: String): PostLoginOutcome {
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

        if (job == null) {
            return PostLoginOutcome(
                redirectToConnectOpportunityInfo = false,
                needsPersonalIdLinkCheck = true,
            )
        }

        ConnectAppUtils.updateLastAccessed(context, appId, username)

        repository.syncJobProgress(job).collect {}

        return PostLoginOutcome(
            redirectToConnectOpportunityInfo = job.isUserSuspended,
        )
    }
}
