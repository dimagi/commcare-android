package org.commcare.connect

import android.content.Context
import android.content.Intent
import org.commcare.activities.CommCareActivity
import org.commcare.activities.connect.ConnectActivity
import org.commcare.activities.connect.ConnectMessagingActivity
import org.commcare.android.database.connect.models.ConnectJobRecord
import org.commcare.connect.ConnectConstants.GO_TO_JOB_STATUS
import org.commcare.connect.ConnectConstants.OPPORTUNITY_ID
import org.commcare.connect.ConnectConstants.SHOW_LAUNCH_BUTTON

object ConnectNavHelper {
    fun unlockAndGoToMessaging(activity: CommCareActivity<*>, listener: ConnectActivityCompleteListener) {
        val personalIdManager: PersonalIdManager = PersonalIdManager.getInstance()
        personalIdManager.init(activity)
        personalIdManager.unlockConnect(
            activity
        ) { success: Boolean ->
            if (success) {
                goToMessaging(activity)
            }
            listener.connectActivityComplete(success)
        }
    }

    fun goToMessaging(context: Context) {
        val i = Intent(context, ConnectMessagingActivity::class.java)
        context.startActivity(i)
    }

    fun unlockAndGoToConnectJobsList(activity: CommCareActivity<*>, listener: ConnectActivityCompleteListener) {
        val personalIdManager: PersonalIdManager = PersonalIdManager.getInstance()
        personalIdManager.init(activity)
        personalIdManager.unlockConnect(
            activity
        ) { success: Boolean ->
            if (success) {
                goToConnectJobsList(activity)
            }
            listener.connectActivityComplete(success)
        }
    }

    fun goToConnectJobsList(context: Context) {
        val i = Intent(context, ConnectActivity::class.java)
        context.startActivity(i)
    }

    fun goToActiveInfoForJob(context: Context, job: ConnectJobRecord, allowProgression: Boolean) {
        val i = Intent(context, ConnectActivity::class.java)
        i.putExtra(GO_TO_JOB_STATUS, true)
        i.putExtra(OPPORTUNITY_ID, job.jobId)
        i.putExtra(SHOW_LAUNCH_BUTTON, allowProgression)
        context.startActivity(i)
    }
}
