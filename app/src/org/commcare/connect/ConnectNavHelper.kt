package org.commcare.connect

import android.content.Context
import android.content.Intent
import org.commcare.activities.CommCareActivity
import org.commcare.activities.PushNotificationActivity
import org.commcare.activities.connect.ConnectActivity
import org.commcare.activities.connect.ConnectMessagingActivity
import org.commcare.activities.connect.PersonalIdWorkHistoryActivity
import org.commcare.android.database.connect.models.ConnectJobRecord
import org.commcare.connect.ConnectConstants.GO_TO_JOB_STATUS
import org.commcare.connect.ConnectConstants.OPPORTUNITY_ID
import org.commcare.connect.ConnectConstants.SHOW_LAUNCH_BUTTON
import org.commcare.connect.database.ConnectUserDatabaseUtil

object ConnectNavHelper {
    private fun unlockAndGoTo(activity: CommCareActivity<*>, listener: ConnectActivityCompleteListener, navigationAction: (Context) -> Unit) {
        val personalIdManager: PersonalIdManager = PersonalIdManager.getInstance()
        personalIdManager.init(activity)
        personalIdManager.unlockConnect(
            activity
        ) { success: Boolean ->
            if (success) {
                navigationAction(activity)
            }
            listener.connectActivityComplete(success)
        }
    }

    fun unlockAndGoToMessaging(activity: CommCareActivity<*>, listener: ConnectActivityCompleteListener) {
        unlockAndGoTo(activity, listener, ::goToMessaging)
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

    fun unlockAndGoToCredentials(activity: CommCareActivity<*>, listener: ConnectActivityCompleteListener) {
        unlockAndGoTo(activity, listener, ::goToCredentials)
    }

    fun goToCredentials(context: Context) {
        val i = Intent(context, PersonalIdWorkHistoryActivity::class.java)
        context.startActivity(i)
    }

    fun unlockAndGoToConnectJobsList(activity: CommCareActivity<*>, listener: ConnectActivityCompleteListener) {
        unlockAndGoTo(activity, listener, ::goToConnectJobsList)
    }

    fun goToConnectJobsList(context: Context) {
        checkConnectAccess(context)
        val i = Intent(context, ConnectActivity::class.java)
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

    fun goToActiveInfoForJob(context: Context, job: ConnectJobRecord, allowProgression: Boolean) {
        checkConnectAccess(context)
        val i = Intent(context, ConnectActivity::class.java)
        i.putExtra(GO_TO_JOB_STATUS, true)
        i.putExtra(OPPORTUNITY_ID, job.jobId)
        i.putExtra(SHOW_LAUNCH_BUTTON, allowProgression)
        context.startActivity(i)
    }
}
