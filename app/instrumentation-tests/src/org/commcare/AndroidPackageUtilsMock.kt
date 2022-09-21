package org.commcare

import org.commcare.utils.AndroidPackageUtils

class AndroidPackageUtilsMock : AndroidPackageUtils() {

    val installedList = ArrayList<String>()

    override fun isApkInstalled(packageName: String): Boolean {
        return !installedList.find { it == packageName }.isNullOrBlank()
    }

    fun addInstalledPackage(packageName: String) {
        installedList.add(packageName)
    }
}
