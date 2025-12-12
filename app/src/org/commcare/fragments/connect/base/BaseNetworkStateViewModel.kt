package org.commcare.fragments.connect.base

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.launch

open class BaseNetworkStateViewModel : ViewModel() {
    sealed class NetworkState {
        object Loading : NetworkState()

        data class Success(
            val state: Boolean,
        ) : NetworkState()

        data class Error(
            val exception: Exception,
        ) : NetworkState()
    }

    protected val networkStateLiveData = MutableLiveData<NetworkState>()
    val networkState: LiveData<NetworkState> = networkStateLiveData

    protected val handler =
        CoroutineExceptionHandler { _, exception ->
            networkStateLiveData.value = NetworkState.Error(if (exception is Exception) exception else Exception(exception))
        }

    protected fun launchTask(task: suspend () -> Unit) {
        viewModelScope.launch(handler) {
            networkStateLiveData.value = NetworkState.Loading
            task()
        }
    }
}
