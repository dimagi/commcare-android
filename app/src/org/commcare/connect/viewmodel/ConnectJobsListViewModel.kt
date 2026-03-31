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
import org.commcare.connect.repository.RefreshPolicy

class ConnectJobsListViewModel(
    application: Application,
) : AndroidViewModel(application) {
    @VisibleForTesting
    internal var repository: ConnectRepository = ConnectRepository.getInstance(application)

    private val _opportunities = MutableLiveData<DataState<List<ConnectJobRecord>>>()
    val opportunities: LiveData<DataState<List<ConnectJobRecord>>> = _opportunities

    private var getOpportunitiesJob: Job? = null

    fun loadOpportunities(forceRefresh: Boolean = false) {
        getOpportunitiesJob?.cancel()
        getOpportunitiesJob =
            collectInto(
                flow = repository.getOpportunities(forceRefresh),
                liveData = _opportunities,
            )
    }
}
