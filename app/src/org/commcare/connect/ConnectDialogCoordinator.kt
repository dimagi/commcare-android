package org.commcare.connect

import org.commcare.activities.CommCareActivity
import org.commcare.personalId.PersonalIdBackupCodeReminderHelper
import org.commcare.personalId.demographic.DemographicBannerBinder

object ConnectDialogCoordinator {
    @JvmStatic
    fun showDueDialog(activity: CommCareActivity<*>): Boolean =
        PersonalIdBackupCodeReminderHelper.checkAndShowReminder(activity) ||
            DemographicBannerBinder.showIfDue(activity)
}
