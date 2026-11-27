package org.commcare.connect.viewmodel

import android.app.Application
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import kotlinx.coroutines.Job
import org.commcare.android.database.connect.models.ConnectJobRecord
import org.commcare.connect.repository.ConnectRepository
import org.commcare.connect.repository.DataState

/** Keeps Opportunity Home's opportunity refresh alive through rotation. */
class ConnectOpportunityHomeViewModel(
    application: Application,
) : AndroidViewModel(application) {
    @VisibleForTesting
    internal var repository: ConnectRepository = ConnectRepository.getInstance()

    private val _opportunities = MutableLiveData<DataState<List<ConnectJobRecord>>>()
    val opportunities: LiveData<DataState<List<ConnectJobRecord>>> = _opportunities

    private var refreshJob: Job? = null

    /** Fetches only when the repository's refresh policy finds the cache stale, and never twice at once. */
    fun refreshOpportunities() {
        if (refreshJob?.isActive == true) {
            return
        }
        refreshJob = collectInto(repository.getOpportunities(), _opportunities)
    }
}
