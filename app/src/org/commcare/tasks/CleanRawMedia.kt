package org.commcare.tasks

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import org.commcare.CommCareApplication
import org.commcare.android.database.user.models.FormRecord
import org.commcare.utils.CrashUtil
import java.io.File
import java.lang.Exception

class CleanRawMedia(appContext: Context, workerParams: WorkerParameters)
    : Worker(appContext, workerParams) {

    override fun doWork(): Result {
        try {
            val storage = CommCareApplication.instance().getUserStorage(FormRecord::class.java)
            for (formRecord in storage) {
                File(formRecord.filePath).parent?.let {
                    val rawDirPath = "$it/raw"
                    File(rawDirPath).deleteRecursively()
                }
            }
        } catch (e: Exception) {
            CrashUtil.reportException(e)
            return Result.failure()
        }
        return Result.success()
    }
}