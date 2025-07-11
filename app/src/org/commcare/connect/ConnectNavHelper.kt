package org.commcare.connect

import android.app.Activity
import android.content.Context
import android.content.Intent
import org.commcare.activities.connect.ConnectActivity
import org.commcare.activities.connect.ConnectMessagingActivity

object ConnectNavHelper {
    fun goToMessaging(context: Context) {
        val i = Intent(context, ConnectMessagingActivity::class.java)
        context.startActivity(i)
    }

    fun goToConnectJobsList(parent: Context) {
        val i = Intent(parent, ConnectActivity::class.java)
        parent.startActivity(i)
    }

    fun goToActiveInfoForJob(activity: Activity, allowProgression: Boolean) {
        val i = Intent(activity, ConnectActivity::class.java)
        i.putExtra("info", true)
        i.putExtra("buttons", allowProgression)
        activity.startActivity(i)
    }
}