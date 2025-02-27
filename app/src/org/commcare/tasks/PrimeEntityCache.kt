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

    lateinit var primeEntityCacheHelper : PrimeEntityCacheHelper

    /**
     * Attempts to prime the entity cache if an active session is detected.
     *
     * This method checks for an active session via the application. When active, it retrieves the cache helper
     * and initiates the cache priming process. If the operation completes successfully, a success result is returned.
     * Should any exception occur or if no active session is found, the method logs the error and returns a failure result.
     * In every case, the cache helper's state is reset.
     *
     * @return a success result if the cache is primed successfully; otherwise, a failure result.
     */
    override fun doWork(): Result {
        try {
            if (CommCareApplication.isSessionActive()) {
                primeEntityCacheHelper = CommCareApplication.instance().currentApp.primeEntityCacheHelper
                primeEntityCacheHelper.primeEntityCache()
                return Result.success()
            }
        } catch (e: Exception) {
            Logger.exception("Error while priming cache in worker", e)
        } finally {
            primeEntityCacheHelper.clearState()
        }
        return Result.failure()
    }

    /**
     * Called when the worker is stopped.
     *
     * Invokes cancel on the primeEntityCacheHelper to terminate any ongoing cache priming operations.
     */
    override fun onStopped() {
        primeEntityCacheHelper.cancel()
    }
}
