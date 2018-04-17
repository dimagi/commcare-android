package org.commcare.logging

// Represents different possible DataChangeLogTypes that can be logged to DataChangeLogger
sealed class DataChangeLogType(val message: String) {

    class CommCareInstall : DataChangeLogType("CommCare installed")
    class CommCareUpdate : DataChangeLogType("CommCare updated")
    class CommCareAppInstall : DataChangeLogType("App installed")
    class CommCareAppUninstall(val appName : String,val appVersion : Int) : DataChangeLogType("Uninstalling App")
    class CommCareAppUpdateStaged : DataChangeLogType("App update staged and starting install")
    class CommCareAppUpdated : DataChangeLogType("App updated")
    class DbUpgradeStart(dbName: String, oldVersion: Int, newVersion: Int) : DataChangeLogType("Starting ${dbName} DB upgrade from ${oldVersion} to ${newVersion}")
    class DbUpgradeComplete(dbName: String, oldVersion: Int, newVersion: Int) : DataChangeLogType("Completed ${dbName} DB upgrade from ${oldVersion} to ${newVersion}")
    class ClearUserData : DataChangeLogType("Clearing user data")
}