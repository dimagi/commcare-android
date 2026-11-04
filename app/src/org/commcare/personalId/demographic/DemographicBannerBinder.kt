package org.commcare.personalId.demographic

import android.widget.Toast
import org.commcare.activities.CommCareActivity
import org.commcare.connect.ConnectActivityCompleteListener
import org.commcare.connect.ConnectNavHelper
import org.commcare.dalvik.R

/**
 * Shows the [DemographicPromptDialog] when [DemographicPromptState] says it is due, and acts on its
 * buttons. Every response to the prompt passes through here.
 */
object DemographicBannerBinder {
    fun showIfDue(
        activity: CommCareActivity<*>,
        state: DemographicPromptState = DemographicPromptState(),
    ) {
        if (activity.currentAlertDialog != null || !state.shouldShow()) {
            return
        }
        DemographicPromptDialog.show(
            activity,
            state.stage(),
            state.filledFieldCount(),
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
    }
}
