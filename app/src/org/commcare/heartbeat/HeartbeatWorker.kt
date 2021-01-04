package org.commcare.heartbeat

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import org.commcare.CommCareApplication
import org.commcare.util.LogTypes
import org.javarosa.core.services.Logger

/**
 * @author $|-|!˅@M
 */
class HeartbeatWorker(context: Context, workerParams: WorkerParameters):
        Worker(context, workerParams) {
    private val requester = CommCareApplication.instance().heartbeatRequester
    override fun doWork(): Result {
        try {
            if (!CommCareApplication.instance().session.heartbeatSucceededForSession()) {
                requester.makeRequest()
            }
        } catch (e: Exception) {
            // Encountered an unexpected issue, should just bail on this thread
            Logger.log(LogTypes.TYPE_ERROR_SERVER_COMMS,
                    "Encountered unexpected exception during heartbeat communications: "
                            + e.message + ". Stopping the heartbeat thread.")
            return Result.failure()
        }
        return Result.success()
    }
}