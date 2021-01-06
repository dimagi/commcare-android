package org.commcare.heartbeat

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import org.commcare.CommCareApplication
import org.commcare.util.LogTypes
import org.javarosa.core.services.Logger
import java.io.IOException
import java.lang.IllegalStateException
import java.net.UnknownHostException

/**
 * @author $|-|!˅@M
 */
class HeartbeatWorker(context: Context, workerParams: WorkerParameters):
        Worker(context, workerParams) {
    private val requester = CommCareApplication.instance().heartbeatRequester
    override fun doWork(): Result {
        try {
            if (CommCareApplication.instance().session.isActive &&
                    !CommCareApplication.instance().session.heartbeatSucceededForSession()) {
                requester.makeRequest()
            }
        } catch (e: Exception) {
            // Encountered an unexpected issue, should just bail on this thread
            return when (e) {
                is UnknownHostException, is IllegalStateException, is IOException -> {
                    Result.retry()
                }
                else -> {
                    Logger.log(LogTypes.TYPE_ERROR_SERVER_COMMS,
                            "Encountered unexpected exception during heartbeat communications: "
                                    + e.message + ". Stopping the heartbeat thread.")
                    Result.failure()
                }
            }
        }
        return Result.success()
    }
}