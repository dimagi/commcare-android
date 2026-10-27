package org.commcare.connect

import android.content.Context
import android.content.Intent
import org.commcare.activities.CommCareActivity
import org.commcare.activities.PushNotificationActivity
import org.commcare.activities.connect.ConnectActivity
import org.commcare.activities.connect.ConnectMessagingActivity
import org.commcare.activities.connect.PersonalIdActivity
import org.commcare.activities.connect.PersonalIdWorkHistoryActivity
import org.commcare.android.database.connect.models.ConnectJobRecord
import org.commcare.connect.ConnectConstants.CCC_DEST_OPPORTUNITY_LIST
import org.commcare.connect.ConnectConstants.CCC_GENERIC_OPPORTUNITY
import org.commcare.connect.ConnectConstants.FROM_STARTUP_ROUTING
import org.commcare.connect.ConnectConstants.GO_TO_JOB_STATUS
import org.commcare.connect.ConnectConstants.OPPORTUNITY_UUID
import org.commcare.connect.ConnectConstants.REDIRECT_ACTION
import org.commcare.connect.database.ConnectUserDatabaseUtil
import org.commcare.personalId.PersonalIdUnlocker
import org.commcare.personalId.PersonalIdUserPreferences
import org.commcare.personalId.UnlockPolicy
import org.commcare.personalId.profile.PersonalIdProfileActivity

object ConnectNavHelper {
    @JvmStatic
    fun launchPersonalId(
        parent: CommCareActivity<*>,
        requestCode: Int,
    ) {
        parent.startActivityForResult(Intent(parent, PersonalIdActivity::class.java), requestCode)
    }

    private fun unlockAndGoTo(
        activity: CommCareActivity<*>,
        policy: UnlockPolicy,
        listener: ConnectActivityCompleteListener,
        navigationAction: (Context) -> Unit,
    ) {
        PersonalIdUnlocker.unlock(
            activity,
            policy,
        ) { success, _ ->
            if (success) {
                navigationAction(activity)
            }
            listener.connectActivityComplete(success, null)
        }
    }

    fun unlockAndGoToMessaging(
        activity: CommCareActivity<*>,
        policy: UnlockPolicy = UnlockPolicy.SESSION_WITH_TIME_THRESHOLD,
        channelId: String? = null,
        listener: ConnectActivityCompleteListener,
    ) {
        unlockAndGoTo(activity, policy, listener) { context -> goToMessaging(context, channelId) }
    }

    fun goToMessaging(
        context: Context,
        channelId: String? = null,
    ) {
        val i = Intent(context, ConnectMessagingActivity::class.java)
        if (!channelId.isNullOrEmpty()) {
            i.putExtra(ConnectMessagingActivity.CHANNEL_ID, channelId)
        }
        context.startActivity(i)
    }

    @JvmStatic
    fun goToNotification(context: Context) {
        val i = Intent(context, PushNotificationActivity::class.java)
        context.startActivity(i)
    }

    fun unlockAndGoToWorkHistory(
        activity: CommCareActivity<*>,
        policy: UnlockPolicy = UnlockPolicy.SESSION_WITH_TIME_THRESHOLD,
        listener: ConnectActivityCompleteListener,
    ) {
        unlockAndGoTo(activity, policy, listener, ::goToWorkHistory)
    }

    fun goToWorkHistory(context: Context) {
        val i = Intent(context, PersonalIdWorkHistoryActivity::class.java)
        context.startActivity(i)
    }

    fun unlockAndGoToProfile(
        activity: CommCareActivity<*>,
        policy: UnlockPolicy = UnlockPolicy.ALWAYS,
        listener: ConnectActivityCompleteListener,
        initiateBackupCodeRecovery: Boolean = false,
    ) {
        unlockAndGoTo(
            activity,
            policy,
            listener,
            { context -> goToProfile(context, initiateBackupCodeRecovery) },
        )
    }

    fun goToProfile(
        context: Context,
        initiateBackupCodeRecovery: Boolean = false,
        pendingBackupCode: Boolean = false,
    ) {
        val i = Intent(context, PersonalIdProfileActivity::class.java)
        if (initiateBackupCodeRecovery) {
            i.putExtra(PersonalIdProfileActivity.EXTRA_INITIATE_BACKUP_CODE_RECOVERY, true)
        }
        if (pendingBackupCode) {
            i.putExtra(PersonalIdProfileActivity.EXTRA_PENDING_BACKUP_CODE, true)
        }
        context.startActivity(i)
    }

    @JvmStatic
    fun goToProfileForPendingBackupCode(context: Context) {
        PersonalIdUserPreferences.setPendingBackupCode(false)
        goToProfile(context, pendingBackupCode = true)
    }

    fun unlockAndGoToConnectJobsList(
        activity: CommCareActivity<*>,
        policy: UnlockPolicy = UnlockPolicy.SESSION_WITH_TIME_THRESHOLD,
        listener: ConnectActivityCompleteListener,
    ) {
        unlockAndGoTo(activity, policy, listener, ::goToConnectJobsList)
    }

    @JvmOverloads
    fun goToConnectJobsList(
        context: Context,
        clearTop: Boolean = false,
    ) {
        checkConnectAccess(context)
        val i = Intent(context, ConnectActivity::class.java)
        if (clearTop) {
            // Drop any Connect/app screens stacked above the opportunities list so back lands there cleanly.
            i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        context.startActivity(i)
    }

    private fun checkConnectAccess(context: Context) {
        if (!ConnectUserDatabaseUtil.hasConnectAccess()) {
            throw IllegalStateException("Cannot navigate to Connect Jobs List without access")
        }
    }

    fun goToConnectJobsListChecked(context: Context) {
        if (ConnectUserDatabaseUtil.hasConnectAccess()) {
            goToConnectJobsList(context)
        }
    }

    /** Builds the intent that opens [opportunityUuid]'s Opportunity Home, or the Opportunity List when it is null. */
    @JvmStatic
    fun buildStartupLandingIntent(
        context: Context,
        opportunityUuid: String?,
    ): Intent {
        checkConnectAccess(context)

        val redirectAction = if (opportunityUuid != null) CCC_GENERIC_OPPORTUNITY else CCC_DEST_OPPORTUNITY_LIST
        val intent = Intent(context, ConnectActivity::class.java)

        intent.putExtra(REDIRECT_ACTION, redirectAction)
        intent.putExtra(FROM_STARTUP_ROUTING, true)
        opportunityUuid?.let { intent.putExtra(OPPORTUNITY_UUID, it) }

        return intent
    }

    fun goToActiveInfoForJob(
        context: Context,
        job: ConnectJobRecord,
    ) {
        checkConnectAccess(context)
        val i = Intent(context, ConnectActivity::class.java)
        i.putExtra(GO_TO_JOB_STATUS, true)
        i.putExtra(OPPORTUNITY_UUID, job.jobUUID)
        context.startActivity(i)
    }
}
