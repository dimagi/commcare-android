package org.commcare.personalId

import org.commcare.activities.CommCareActivity
import org.commcare.connect.PersonalIdManager
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

/**
 * Helper class to manage the scheduling and display of backup code reminders for Personal ID.
 */
object PersonalIdBackupCodeReminderHelper {
    private val initialReminderInterval = 1.hours.inWholeMilliseconds
    private val firstReminderInterval = 3.days.inWholeMilliseconds
    private val recurringReminderInterval = 7.days.inWholeMilliseconds

    @JvmStatic
    fun initialize() {
        PersonalIdUserPreferences.setNextBackupCodeReminderDue(System.currentTimeMillis() + initialReminderInterval)
        PersonalIdUserPreferences.setBackupCodeReminderCount(0)
    }

    @JvmStatic
    fun isDue(): Boolean {
        val nextDue = PersonalIdUserPreferences.getNextBackupCodeReminderDue()
        return nextDue != -1L && System.currentTimeMillis() >= nextDue
    }

    @JvmStatic
    fun scheduleNext() {
        val count = PersonalIdUserPreferences.getBackupCodeReminderCount()
        val interval = if (count == 0) firstReminderInterval else recurringReminderInterval
        PersonalIdUserPreferences.setNextBackupCodeReminderDue(System.currentTimeMillis() + interval)
        PersonalIdUserPreferences.setBackupCodeReminderCount(count + 1)
    }

    @JvmStatic
    fun checkAndShowReminder(activity: CommCareActivity<*>) {
        if (PersonalIdManager.getInstance().isloggedIn() && isDue()) {
            BackupCodeReminderDialog.show(activity)
            scheduleNext()
        }
    }
}
