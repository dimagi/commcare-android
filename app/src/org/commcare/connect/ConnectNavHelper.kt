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

    fun goToConnectJobsList(context: Context) {
        val i = Intent(context, ConnectActivity::class.java)
        context.startActivity(i)
    }

    fun goToActiveInfoForJob(context: Context, allowProgression: Boolean) {
        val i = Intent(context, ConnectActivity::class.java)
        i.putExtra("info", true)
        i.putExtra("buttons", allowProgression)
        context.startActivity(i)
    }
}
