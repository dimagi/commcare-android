package org.commcare.connect

import android.content.Context
import android.content.Intent
import org.commcare.activities.CommCareActivity
import org.commcare.activities.PushNotificationActivity
import org.commcare.activities.connect.ConnectActivity
import org.commcare.activities.connect.ConnectMessagingActivity
import org.commcare.activities.connect.PersonalIdProfileActivity
import org.commcare.activities.connect.PersonalIdWorkHistoryActivity
import org.commcare.android.database.connect.models.ConnectJobRecord
import org.commcare.connect.ConnectConstants.GO_TO_JOB_STATUS
import org.commcare.connect.ConnectConstants.OPPORTUNITY_UUID
import org.commcare.connect.ConnectConstants.SHOW_LAUNCH_BUTTON
import org.commcare.connect.database.ConnectUserDatabaseUtil
import org.commcare.personalId.PersonalIdUnlocker
import org.commcare.personalId.UnlockPolicy

object ConnectNavHelper {
    private fun unlockAndGoTo(
        activity: CommCareActivity<*>,
        policy: UnlockPolicy,
        listener: ConnectActivityCompleteListener,
        navigationAction: (Context) -> Unit,
    ) {
        PersonalIdUnlocker.unlock(activity, policy) { success ->
            if (success) {
                navigationAction(activity)
            }
            listener.connectActivityComplete(success)
        }
    }

    fun unlockAndGoToMessaging(
        activity: CommCareActivity<*>,
        policy: UnlockPolicy = UnlockPolicy.SESSION_WITH_TIME_THRESHOLD,
        listener: ConnectActivityCompleteListener,
    ) {
        unlockAndGoTo(activity, policy, listener, ::goToMessaging)
    }

    fun goToMessaging(context: Context) {
        val i = Intent(context, ConnectMessagingActivity::class.java)
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
    ) {
        unlockAndGoTo(activity, policy, listener, ::goToProfile)
    }

    private fun goToProfile(context: Context) {
        val i = Intent(context, PersonalIdProfileActivity::class.java)
        context.startActivity(i)
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
        if (!ConnectUserDatabaseUtil.hasConnectAccess(context)) {
            throw IllegalStateException("Cannot navigate to Connect Jobs List without access")
        }
    }

    fun goToConnectJobsListChecked(context: Context) {
        if (ConnectUserDatabaseUtil.hasConnectAccess(context)) {
            goToConnectJobsList(context)
        }
    }

    fun goToActiveInfoForJob(
        context: Context,
        job: ConnectJobRecord,
        allowProgression: Boolean,
    ) {
        checkConnectAccess(context)
        val i = Intent(context, ConnectActivity::class.java)
        i.putExtra(GO_TO_JOB_STATUS, true)
        i.putExtra(OPPORTUNITY_UUID, job.jobUUID)
        i.putExtra(SHOW_LAUNCH_BUTTON, allowProgression)
        context.startActivity(i)
    }
}
