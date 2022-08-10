package org.commcare.utils

import android.content.pm.PackageManager
import org.commcare.CommCareApplication

open class AndroidPackageUtils {

    open fun isApkInstalled(packageName: String): Boolean {
        return try {
            val packageManager = CommCareApplication.instance().packageManager
            packageManager.getPackageInfo(packageName, PackageManager.GET_ACTIVITIES)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }
}
