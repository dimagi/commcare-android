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

class ConnectDeliveryProgressViewModel(
    application: Application,
) : AndroidViewModel(application) {
    @VisibleForTesting
    internal var repository: ConnectRepository = ConnectRepository.getInstance(application)

    private val _deliveryProgress = MutableLiveData<DataState<ConnectJobRecord>>()
    val deliveryProgress: LiveData<DataState<ConnectJobRecord>> = _deliveryProgress

    private var loadDeliveryProgressJob: Job? = null

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
}
