package org.commcare.pn.helper

import android.content.Context
import android.content.Intent
import org.commcare.activities.connect.ConnectActivity
import org.commcare.activities.connect.ConnectMessagingActivity
import org.commcare.connect.ConnectConstants.OPENED_FROM_NOTIFICATION_OR_INVITE
import org.commcare.connect.ConnectConstants.OPPORTUNITY_UUID
import org.commcare.connect.ConnectNavHelper
import org.commcare.connect.ConnectOpportunityHomeState
import org.commcare.connect.PersonalIdSessionManager
import org.commcare.connect.database.ConnectJobUtils
import org.commcare.personalId.PersonalIdUserPreferences
import org.commcare.startup.LandingScreen
import org.commcare.startup.LandingStateProvider

/** Builds the task a notification or deep link opens, so hitting the Back button from its target moves into the app. */
object NotificationTaskStack {
    /**
     * Returns the intents to pass to `startActivities`, bottom first, in a new cleared task. A Messaging target,
     * or an opportunity target showing its intro, gets the Connect home from
     * [NotificationHomeResolver] beneath it while PersonalID is signed in; any other target is the only screen.
     * Connect targets are marked [OPENED_FROM_NOTIFICATION_OR_INVITE].
     */
    @JvmStatic
    fun build(
        context: Context,
        targetIntent: Intent,
    ): Array<Intent> {
        val targetClassName = targetIntent.component?.className
        if (targetClassName == ConnectActivity::class.java.name ||
            targetClassName == ConnectMessagingActivity::class.java.name
        ) {
            targetIntent.putExtra(OPENED_FROM_NOTIFICATION_OR_INVITE, true)
        }

        val homeIntent =
            when {
                !PersonalIdSessionManager.getInstance().isloggedIn() -> null
                targetClassName == ConnectMessagingActivity::class.java.name -> buildHomeIntent(context, null)
                targetClassName == ConnectActivity::class.java.name && opensOpportunityIntro(targetIntent) ->
                    buildHomeIntent(context, targetIntent.getStringExtra(OPPORTUNITY_UUID))
                else -> null
            }

        if (homeIntent == null) {
            return arrayOf(targetIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        }

        return arrayOf(homeIntent, targetIntent)
    }

    private fun opensOpportunityIntro(targetIntent: Intent): Boolean {
        val opportunityUuid = targetIntent.getStringExtra(OPPORTUNITY_UUID) ?: return false
        val job = ConnectJobUtils.getCompositeJob(opportunityUuid)
        return job == null || ConnectOpportunityHomeState.isIntro(job)
    }

    private fun buildHomeIntent(
        context: Context,
        targetOpportunityUuid: String?,
    ): Intent {
        val homeScreen =
            NotificationHomeResolver.resolve(
                PersonalIdUserPreferences.getLastAccessedOpportunityUuid(),
                LandingStateProvider.getOpportunityLandingInfos(),
                targetOpportunityUuid,
            )

        return if (homeScreen is LandingScreen.OpportunityHome) {
            ConnectNavHelper.buildOpportunityHomeTaskRootIntent(context, homeScreen.opportunityUuid)
        } else {
            Intent(context, ConnectActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
    }
}
