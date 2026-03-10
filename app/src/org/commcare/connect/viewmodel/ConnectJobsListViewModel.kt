package org.commcare.connect.viewmodel

import android.app.Application
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
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

    fun loadOpportunities(forceRefresh: Boolean = false) {
        collectInto(
            flow = repository.getOpportunities(forceRefresh, RefreshPolicy.SESSION_AND_TIME_BASED(60_000)),
            liveData = _opportunities,
        )
    }
}
