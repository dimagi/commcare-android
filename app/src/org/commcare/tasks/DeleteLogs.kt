package org.commcare.tasks

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import org.commcare.CommCareApplication
import org.commcare.android.javarosa.AndroidLogEntry
import org.commcare.android.javarosa.DeviceReportRecord
import org.commcare.android.logging.ForceCloseLogEntry
import org.commcare.logging.XPathErrorEntry
import org.commcare.models.database.SqlStorage
import org.commcare.preferences.HiddenPreferences
import org.javarosa.core.model.utils.DateUtils
import java.io.File
import java.util.Date
import java.util.Vector


// A Worker class used for deleting any logs older than 3 months
class DeleteLogs(appContext: Context, workerParams: WorkerParameters)
    : Worker(appContext, workerParams) {

    override fun doWork(): Result {
        val today = Date().time
        val threeMonthsAgo = Date(today - (90L * DateUtils.DAY_IN_MS));

        // Clear logs older than 3 month stored in DB
        purge(CommCareApplication.instance().getUserStorage(AndroidLogEntry.STORAGE_KEY, AndroidLogEntry::class.java), threeMonthsAgo)
        purge(CommCareApplication.instance().getUserStorage(XPathErrorEntry.STORAGE_KEY, XPathErrorEntry::class.java), threeMonthsAgo)
        purge(CommCareApplication.instance().getGlobalStorage(ForceCloseLogEntry.STORAGE_KEY, ForceCloseLogEntry::class.java), threeMonthsAgo)
        purge(CommCareApplication.instance().getUserStorage(XPathErrorEntry.STORAGE_KEY, XPathErrorEntry::class.java), threeMonthsAgo)

        // Clear logs older than 3 month which have been serialized to disk
        val deviceReportRecordStorage = CommCareApplication.instance().getUserStorage(DeviceReportRecord::class.java)
        val reportsToDelete = Vector<Int>()
        for (deviceReportRecord in deviceReportRecordStorage) {
            val reportFile = File(deviceReportRecord.filePath)
            if (reportFile.lastModified() < threeMonthsAgo.time) {
                reportFile.delete()
                reportsToDelete.addElement(deviceReportRecord.id)
            }
        }
        deviceReportRecordStorage.removeAll(reportsToDelete)

        HiddenPreferences.updateLastLogDeletionTime()
        return Result.success()
    }

    private fun purge(logsStorage: SqlStorage<out AndroidLogEntry>, deleteBeforeDate: Date) {
        val toDeleteLogs = Vector<Int>()
        for (androidLogEntry in logsStorage) {
            if (androidLogEntry.time.before(deleteBeforeDate)) {
                toDeleteLogs.add(androidLogEntry.id)
            }
        }
        logsStorage.removeAll(toDeleteLogs)
    }

}