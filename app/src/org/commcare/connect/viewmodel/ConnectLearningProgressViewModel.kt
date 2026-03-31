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

class ConnectLearningProgressViewModel(
    application: Application,
) : AndroidViewModel(application) {
    @VisibleForTesting
    internal var repository: ConnectRepository = ConnectRepository.getInstance(application)

    private val _learningProgress = MutableLiveData<DataState<ConnectJobRecord>>()
    val learningProgress: LiveData<DataState<ConnectJobRecord>> = _learningProgress

    private var loadLearnProgressJob: Job? = null

    fun loadLearningProgress(
        job: ConnectJobRecord,
        forceRefresh: Boolean = false,
    ) {
        loadLearnProgressJob?.cancel()
        loadLearnProgressJob =
            collectInto(
                flow = repository.getLearningProgress(job, forceRefresh, RefreshPolicy.ALWAYS),
                liveData = _learningProgress,
            )
    }
}
