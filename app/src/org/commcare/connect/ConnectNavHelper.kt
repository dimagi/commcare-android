package org.commcare.connect

import android.content.Context
import android.content.Intent
import org.commcare.activities.connect.ConnectActivity
import org.commcare.activities.connect.ConnectMessagingActivity
import org.commcare.connect.ConnectConstants.GO_TO_JOB_STATUS
import org.commcare.connect.ConnectConstants.SHOW_LAUNCH_BUTTON

object ConnectNavHelper {
    fun goToMessaging(context: Context) {
        val i = Intent(context, ConnectMessagingActivity::class.java)
        context.startActivity(i)
    }

    fun goToConnectJobsList(context: Context) {
        val i = Intent(context, ConnectActivity::class.java)
        context.startActivity(i)
    }

    fun goToActiveInfoForJob(context: Context, allowProgression: Boolean) {
        val i = Intent(context, ConnectActivity::class.java)
        i.putExtra(GO_TO_JOB_STATUS, true)
        i.putExtra(SHOW_LAUNCH_BUTTON, allowProgression)
        context.startActivity(i)
    }
}
