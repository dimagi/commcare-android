package org.commcare.tasks

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import org.commcare.CommCareApplication
import org.commcare.engine.cases.CaseUtils
import org.commcare.models.database.user.models.CommCareEntityStorageCache
import org.javarosa.core.services.Logger

class EntityCacheInvalidationWorker(context: Context, workerParams: WorkerParameters) :
    CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        try {
            val entityStorageCache: CommCareEntityStorageCache = CommCareEntityStorageCache("case")
            if (!entityStorageCache.isEmpty) {
                entityStorageCache.processShallowRecords()
            }
        } catch (e: Exception) {
            Logger.exception("Error encountered while invalidating entity cache", e)
            return Result.failure()
        } finally {
            // we want to schedule the Prime worker irrespective of result of this task
            if (CommCareApplication.isSessionActive()) {
                PrimeEntityCacheHelper.schedulePrimeEntityCacheWorker()
            }
        }
        return Result.success()
    }
}
