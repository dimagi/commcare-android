package org.commcare.tasks

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import org.commcare.CommCareApplication
import org.commcare.android.javarosa.AndroidLogEntry
import org.commcare.android.logging.ForceCloseLogEntry
import org.commcare.logging.XPathErrorEntry
import org.commcare.models.database.SqlStorage
import org.commcare.preferences.HiddenPreferences
import org.javarosa.core.model.utils.DateUtils
import java.util.Date
import java.util.Vector


// A Worker class used for deleting any logs older than 6 months
class DeleteLogs(appContext: Context, workerParams: WorkerParameters)
    : Worker(appContext, workerParams) {

    override fun doWork(): Result {
        val today = Date().time
        val sixMonthsAgo = Date(today - (180L * DateUtils.DAY_IN_MS));

        purge(CommCareApplication.instance().getUserStorage(AndroidLogEntry.STORAGE_KEY, AndroidLogEntry::class.java), sixMonthsAgo)
        purge(CommCareApplication.instance().getUserStorage(XPathErrorEntry.STORAGE_KEY, XPathErrorEntry::class.java), sixMonthsAgo)
        purge(CommCareApplication.instance().getGlobalStorage(ForceCloseLogEntry.STORAGE_KEY, ForceCloseLogEntry::class.java), sixMonthsAgo)
        purge(CommCareApplication.instance().getUserStorage(XPathErrorEntry.STORAGE_KEY, XPathErrorEntry::class.java), sixMonthsAgo)

        HiddenPreferences.updateLastLogDeletionTime()
        return Result.success()
    }

    private fun purge(logsStorage: SqlStorage<out AndroidLogEntry>, sixMonthsAgo: Date) {
        val toDeleteLogs = Vector<Int>()
        for (androidLogEntry in logsStorage) {
            if (androidLogEntry.time.before(sixMonthsAgo)) {
                toDeleteLogs.add(androidLogEntry.id)
            }
        }
        logsStorage.removeAll(toDeleteLogs)
    }

}