package org.commcare.connect.viewmodel

import androidx.annotation.VisibleForTesting
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.commcare.preferences.HiddenPreferences
import org.commcare.utils.SyncDetailCalculations
import org.javarosa.core.services.Logger

/** The sync status App Home showed for the seated app: its last sync and the forms not yet sent. */
data class AppSyncStatus(
    val lastSyncTime: Long,
    val unsentForms: Int,
    val syncNeeded: Boolean,
    val showZeroUnsentForms: Boolean = false,
) {
    val hasUnsentForms get() = unsentForms > 0

    /** App Home listed the unsent forms only when there were some, unless the app asks to show zero too. */
    val showsUnsentForms get() = hasUnsentForms || showZeroUnsentForms

    companion object {
        /** Reads the logged-in user's status; touches user storage, so call it off the main thread. */
        fun read(): AppSyncStatus {
            val lastSyncTime = SyncDetailCalculations.getLastSyncTime()
            val unsentForms = SyncDetailCalculations.getNumUnsentForms()
            return AppSyncStatus(
                lastSyncTime = lastSyncTime,
                unsentForms = unsentForms,
                syncNeeded = SyncDetailCalculations.isSyncStronglyNeeded(unsentForms, lastSyncTime),
                showZeroUnsentForms = HiddenPreferences.shouldShowUnsentFormsWhenZero(),
            )
        }
    }
}

/**
 * Holds the seated app's [AppSyncStatus] for Opportunity Home, which took over App Home's sync button.
 * Null while no app session belongs to the opportunity on screen.
 */
class ConnectAppSyncStatusViewModel(
    private val readStatus: () -> AppSyncStatus = AppSyncStatus::read,
    private val ioDispatcher: CoroutineDispatcher = defaultIoDispatcher,
) : ViewModel() {
    private val _status = MutableLiveData<AppSyncStatus?>(null)
    val status: LiveData<AppSyncStatus?> = _status

    private var loadJob: Job? = null

    /** Re-reads the status, or clears it when [appSessionAvailable] is false. */
    fun reload(appSessionAvailable: Boolean) {
        loadJob?.cancel()
        if (!appSessionAvailable) {
            _status.value = null
            return
        }
        loadJob =
            viewModelScope.launch {
                try {
                    _status.value = withContext(ioDispatcher) { readStatus() }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    Logger.exception("Failed to read the app's sync status", e)
                }
            }
    }

    companion object {
        /** Where reads run for instances the activity creates; tests swap in their own dispatcher. */
        @VisibleForTesting
        var defaultIoDispatcher: CoroutineDispatcher = Dispatchers.IO

        /** The one instance shared by every Connect screen in [activity]. */
        @JvmStatic
        fun of(activity: FragmentActivity): ConnectAppSyncStatusViewModel =
            ViewModelProvider(activity)[ConnectAppSyncStatusViewModel::class.java]
    }
}
