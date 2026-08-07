package org.commcare.connect.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import kotlinx.coroutines.Job
import org.commcare.android.database.connect.models.ConnectUserRecord
import org.commcare.connect.repository.ConnectRepository
import org.commcare.connect.repository.DataState

class ConnectJobIntroViewModel(
    application: Application,
    private val repository: ConnectRepository = ConnectRepository.getInstance(application),
) : AndroidViewModel(application) {
    private val _startLearning = MutableLiveData<DataState<Unit>>()
    val startLearning: LiveData<DataState<Unit>> = _startLearning

    private var startLearningJob: Job? = null

    fun startLearning(
        user: ConnectUserRecord,
        jobUUID: String,
    ) {
        startLearningJob?.cancel()
        startLearningJob =
            collectInto(
                flow = repository.startLearning(user, jobUUID),
                liveData = _startLearning,
            )
    }
}
