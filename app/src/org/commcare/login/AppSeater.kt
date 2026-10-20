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

    object Failed : SeatResult()
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
        fun start(
            lifecycleOwner: LifecycleOwner,
            appId: String,
            listener: LoginProgressListener,
            onComplete: Runnable,
        ): Job =
            lifecycleOwner.lifecycleScope.launch {
                seatIfNeeded(appId, listener)
                onComplete.run()
            }

        suspend fun seatIfNeeded(
            appId: String,
            listener: LoginProgressListener,
        ): SeatResult {
            listener.onProgress(LoginProgress(LoginPhase.Seating))
            val record = recordLookup(appId) ?: return SeatResult.Failed
            val resourceState = withContext(ioDispatcher) { seatApp(record) }

            return if (resourceState == CommCareApplication.STATE_CORRUPTED) {
                SeatResult.Failed
            } else {
                SeatResult.Success
            }
        }
    }
