package org.commcare.utils

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import org.commcare.CommCareApplication

object PlaystoreUtils {

    fun launchPlayStore(context: Context, packageName: String) {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName")))
    }
}
