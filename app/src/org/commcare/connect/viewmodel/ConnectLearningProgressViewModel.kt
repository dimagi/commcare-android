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
    internal var repository: ConnectRepository = ConnectRepository.getInstance()

    private val _learningProgress = MutableLiveData<DataState<ConnectJobRecord>>()
    val learningProgress: LiveData<DataState<ConnectJobRecord>> = _learningProgress

    private val _claimJob = MutableLiveData<DataState<Unit>>()
    val claimJob: LiveData<DataState<Unit>> = _claimJob

    private var loadLearnProgressJob: Job? = null
    private var claimJobCoroutine: Job? = null

    fun loadLearningProgress(
        opportunity: ConnectJobRecord,
        forceRefresh: Boolean = false,
    ) {
        loadLearnProgressJob?.cancel()
        loadLearnProgressJob =
            collectInto(
                flow = repository.getLearningProgress(opportunity, forceRefresh, RefreshPolicy.ALWAYS),
                liveData = _learningProgress,
            )
    }

    fun claimJob(job: ConnectJobRecord) {
        if (job.status == ConnectJobRecord.STATUS_DELIVERING) {
            _claimJob.value = DataState.Success(Unit)
            return
        }
        claimJobCoroutine?.cancel()
        claimJobCoroutine =
            collectInto(
                flow = repository.claimJob(job),
                liveData = _claimJob,
            )
    }
}
