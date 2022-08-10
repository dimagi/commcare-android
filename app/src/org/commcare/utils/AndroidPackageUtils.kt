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

    fun getPackageName(id: String): String? {
        return try {
            val pm = CommCareApplication.instance().packageManager
            return pm.getApplicationLabel(pm.getApplicationInfo(id, 0)).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
    }
}
