package org.commcare.logging

// Represents different possible DataChangeLog that can be logged to DataChangeLogger
sealed class DataChangeLog(val message: String) {

    class CommCareInstall : DataChangeLog("CommCare installed")
    class CommCareUpdate : DataChangeLog("CommCare updated")
    class CommCareAppInstall : DataChangeLog("App installed")
    class CommCareAppUninstall(val appName : String,val appVersion : Int) : DataChangeLog("Uninstalling App")
    class CommCareAppUpdateStaged : DataChangeLog("App update staged and starting install")
    class CommCareAppUpdated : DataChangeLog("App updated")
    class DbUpgradeStart(dbName: String, oldVersion: Int, newVersion: Int) : DataChangeLog("Starting ${dbName} DB upgrade from ${oldVersion} to ${newVersion}")
    class DbUpgradeComplete(dbName: String, oldVersion: Int, newVersion: Int) : DataChangeLog("Completed ${dbName} DB upgrade from ${oldVersion} to ${newVersion}")
    class WipeUserSandbox : DataChangeLog("Wiping user sandbox")
}