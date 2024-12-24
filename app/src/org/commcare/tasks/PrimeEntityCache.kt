package org.commcare.tasks

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import org.javarosa.core.services.Logger

class PrimeEntityCache(appContext: Context, workerParams: WorkerParameters)
    : Worker(appContext, workerParams)  {

    override fun doWork(): Result {
        try {
            PrimeEntityCacheHelper.getInstance().primeEntityCache()
            return Result.success()
        } catch (e: Exception) {
            Logger.exception("Error while priming cache in worker", e)
        } finally {
            PrimeEntityCacheHelper.getInstance().clearState();
        }
        return Result.failure()
    }

    override fun onStopped() {
        PrimeEntityCacheHelper.getInstance().cancel()
    }
}
