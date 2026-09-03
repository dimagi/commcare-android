package org.commcare.personalId

object PersonalIdReminderHelper {
    private const val FIRST_INTERVAL_MS = 60 * 60 * 1000L
    private const val SECOND_INTERVAL_MS = 3 * 24 * 60 * 60 * 1000L
    private const val RECURRING_INTERVAL_MS = 7 * 24 * 60 * 60 * 1000L

    @JvmStatic
    fun initialize() {
        PersonalIdUserPreferences.setNextReminderDue(System.currentTimeMillis() + FIRST_INTERVAL_MS)
        PersonalIdUserPreferences.setReminderStage(0)
    }

    @JvmStatic
    fun isDue(): Boolean {
        val nextDue = PersonalIdUserPreferences.getNextReminderDue()
        return nextDue != -1L && System.currentTimeMillis() >= nextDue
    }

    @JvmStatic
    fun scheduleNext() {
        val stage = PersonalIdUserPreferences.getReminderStage()
        val interval = if (stage == 0) SECOND_INTERVAL_MS else RECURRING_INTERVAL_MS
        PersonalIdUserPreferences.setNextReminderDue(System.currentTimeMillis() + interval)
        PersonalIdUserPreferences.setReminderStage(1)
    }

    @JvmStatic
    fun clear() {
        PersonalIdUserPreferences.clearReminderState()
    }
}
