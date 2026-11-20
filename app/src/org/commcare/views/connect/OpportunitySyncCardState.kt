package org.commcare.views.connect

import android.content.Context
import android.text.format.DateUtils
import org.commcare.connect.viewmodel.AppSyncStatus
import org.commcare.dalvik.R

/**
 * The sync card's state on Opportunity Home. Without an app session it shows the opportunity's own
 * [opportunityStatus]. With one, it shows App Home's status instead: the app's last sync and the forms
 * waiting to be sent, warning while any are waiting or a sync is overdue.
 */
fun opportunitySyncCardState(
    context: Context,
    opportunityStatus: CharSequence,
    opportunitySynced: Boolean,
    appStatus: AppSyncStatus?,
    now: Long = System.currentTimeMillis(),
): ConnectSyncStatusCard.State {
    val title = context.getString(R.string.connect_sync_card_press_to_sync)
    if (appStatus == null) {
        return ConnectSyncStatusCard.State(title, opportunityStatus, warning = !opportunitySynced)
    }
    val lastSynced =
        context.getString(R.string.connect_last_synced, relativeSyncTime(context, appStatus.lastSyncTime, now))
    val subtext =
        if (appStatus.showsUnsentForms) {
            lastSynced + "\n" + context.getString(R.string.connect_sync_card_forms_pending, appStatus.unsentForms)
        } else {
            lastSynced
        }
    return ConnectSyncStatusCard.State(
        title,
        subtext,
        warning = !opportunitySynced || appStatus.hasUnsentForms || appStatus.syncNeeded,
    )
}

private fun relativeSyncTime(
    context: Context,
    lastSyncTime: Long,
    now: Long,
): CharSequence =
    if (lastSyncTime == 0L) {
        context.getString(R.string.connect_never)
    } else {
        DateUtils.getRelativeTimeSpanString(lastSyncTime, now, DateUtils.MINUTE_IN_MILLIS)
    }
