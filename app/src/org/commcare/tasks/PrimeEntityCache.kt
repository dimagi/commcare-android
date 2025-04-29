package org.commcare.tasks

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import org.commcare.CommCareApplication
import org.javarosa.core.services.Logger

/**
 * Android Worker to prime cache for entity screens
 */
class PrimeEntityCache(appContext: Context, workerParams: WorkerParameters) : Worker(appContext, workerParams) {

    var primeEntityCacheHelper : PrimeEntityCacheHelper? = null

    override fun doWork(): Result {
        try {
            if (CommCareApplication.isSessionActive()) {
                primeEntityCacheHelper = CommCareApplication.instance().currentApp.primeEntityCacheHelper
                primeEntityCacheHelper!!.primeEntityCache()
                return Result.success()
            }
        } catch (e: Exception) {
            Logger.exception("Error while priming cache in worker", e)
        } finally {
            primeEntityCacheHelper?.clearState()
        }
        return Result.failure()
    }

    override fun onStopped() {
        primeEntityCacheHelper?.cancel()
    }
}
