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

class ConnectDeliveryHomeViewModel(
    application: Application,
) : AndroidViewModel(application) {
    @VisibleForTesting
    internal var repository: ConnectRepository = ConnectRepository.getInstance(application)

    private val _deliveryProgress = MutableLiveData<DataState<ConnectJobRecord>>()
    val deliveryProgress: LiveData<DataState<ConnectJobRecord>> = _deliveryProgress

    private val _learningProgress = MutableLiveData<DataState<ConnectJobRecord>>()
    val learningProgress: LiveData<DataState<ConnectJobRecord>> = _learningProgress

    private var loadDeliveryProgressJob: Job? = null
    private var loadLearningProgressJob: Job? = null

    fun loadDeliveryProgress(
        opportunity: ConnectJobRecord,
        forceRefresh: Boolean = false,
    ) {
        loadDeliveryProgressJob?.cancel()
        loadDeliveryProgressJob =
            collectInto(
                flow = repository.getDeliveryProgress(opportunity, forceRefresh, RefreshPolicy.ALWAYS),
                liveData = _deliveryProgress,
            )
    }

    /**
     * Learning is complete by the time an opportunity is delivering, but the records proving it only
     * reach a device that ran the learn sync — so a device that never did has to ask for them.
     */
    fun loadLearningProgress(opportunity: ConnectJobRecord) {
        loadLearningProgressJob?.cancel()
        loadLearningProgressJob =
            collectInto(
                flow = repository.getLearningProgress(opportunity, false, RefreshPolicy.ALWAYS),
                liveData = _learningProgress,
            )
    }
}
