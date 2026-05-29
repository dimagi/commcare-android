package org.commcare.login

import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.commcare.CommCareApp
import org.commcare.CommCareApplication
import org.commcare.android.database.global.models.ApplicationRecord
import org.commcare.utils.MultipleAppsUtil

sealed class SeatResult {
    object Success : SeatResult()

    data class Failed(
        val reason: SeatFailure,
    ) : SeatResult()
}

enum class SeatFailure {
    APP_NOT_FOUND,
    CORRUPTED,
}

class AppSeater
    @JvmOverloads
    constructor(
        private val recordLookup: (String) -> ApplicationRecord? = { MultipleAppsUtil.getAppById(it) },
        private val seatApp: (ApplicationRecord) -> Int = { record ->
            val app = CommCareApp(record)
            CommCareApplication.instance().initializeAppResources(app)
            app.appResourceState
        },
        private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    ) {
        fun interface SeatResultCallback {
            fun onResult(result: SeatResult)
        }

        fun start(
            lifecycleOwner: LifecycleOwner,
            appId: String,
            sink: LoginProgressSink,
            callback: SeatResultCallback,
        ): Job =
            lifecycleOwner.lifecycleScope.launch {
                callback.onResult(seatIfNeeded(appId, sink))
            }

        suspend fun seatIfNeeded(
            appId: String,
            sink: LoginProgressSink,
        ): SeatResult {
            sink.onProgress(LoginProgress(LoginPhase.Seating))
            val record = recordLookup(appId) ?: return SeatResult.Failed(SeatFailure.APP_NOT_FOUND)
            val resourceState = withContext(ioDispatcher) { seatApp(record) }
            return if (resourceState == CommCareApplication.STATE_CORRUPTED) {
                SeatResult.Failed(SeatFailure.CORRUPTED)
            } else {
                SeatResult.Success
            }
        }
    }
