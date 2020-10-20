package org.commcare.tasks

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import org.commcare.CommCareApplication
import org.commcare.android.database.user.models.FormRecord
import org.commcare.utils.CrashUtil
import org.commcare.utils.StorageUtils.getUnsentCompleteOrSavedFormIdsForCurrentApp
import java.io.File

class CleanRawMedia(appContext: Context, workerParams: WorkerParameters)
    : Worker(appContext, workerParams) {

    override fun doWork(): Result {
        val storage = CommCareApplication.instance().getUserStorage(FormRecord::class.java)
        val recordsToRemove = getUnsentCompleteOrSavedFormIdsForCurrentApp(storage)
        for (formId in recordsToRemove) {
            try {
                val filePath = storage.getMetaDataFieldForRecord(formId, FormRecord.META_FILE_PATH)
                File(filePath).parent?.let {
                    val rawDirPath = "$it/raw"
                    File(rawDirPath).deleteRecursively()
                }
            } catch (e: Exception) {
                CrashUtil.reportException(e)
            }
        }
        return Result.success()
    }
}