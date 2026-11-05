package org.commcare.personalId.demographic

import android.widget.Toast
import org.commcare.activities.CommCareActivity
import org.commcare.connect.ConnectActivityCompleteListener
import org.commcare.connect.ConnectNavHelper
import org.commcare.dalvik.R
import org.commcare.personalId.PersonalIdFeatureFlagChecker
import org.commcare.personalId.PersonalIdFeatureFlagChecker.FeatureFlag.Companion.DEMOGRAPHIC_PROMPT

/**
 * Shows the [DemographicPromptDialog] when [DemographicPromptState] says it is due, and acts on its
 * buttons. Every response to the prompt passes through here.
 */
object DemographicBannerBinder {
    fun showIfDue(
        activity: CommCareActivity<*>,
        state: DemographicPromptState = DemographicPromptState(),
    ): Boolean {
        if (!PersonalIdFeatureFlagChecker.isFeatureEnabled(DEMOGRAPHIC_PROMPT) || activity.currentAlertDialog != null) {
            return false
        }
        val completion = state.profileCompletion()
        if (!state.shouldShow(completion)) {
            return false
        }
        DemographicPromptDialog.show(
            activity,
            state.stage(),
            completion,
            onDecline = {
                state.recordDecline()
                Toast.makeText(activity, R.string.personalid_demographic_banner_snoozed, Toast.LENGTH_LONG).show()
            },
            onCompleteProfile = {
                state.recordCompleteTapped()
                ConnectNavHelper.unlockAndGoToProfile(
                    activity,
                    listener = ConnectActivityCompleteListener { _, _ -> },
                    openProfileEdit = true,
                )
            },
        )
        return true
    }
}
