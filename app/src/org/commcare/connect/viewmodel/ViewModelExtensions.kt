package org.commcare.connect.viewmodel

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import org.commcare.connect.repository.DataState

fun <T> ViewModel.collectInto(
    flow: Flow<DataState<T>>,
    liveData: MutableLiveData<DataState<T>>,
): Job =
    viewModelScope.launch {
        flow
            .catch { exception ->
                liveData.value = DataState.Error.from(exception)
            }.collect { dataState ->
                liveData.value = dataState
            }
    }
