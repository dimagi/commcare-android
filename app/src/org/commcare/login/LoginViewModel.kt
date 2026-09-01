package org.commcare.login

import android.app.Application
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Owns the in-flight login pipeline on behalf of `LoginActivity`.
 */
class LoginViewModel(
    application: Application,
) : AndroidViewModel(application) {
    @VisibleForTesting
    internal var performLogin: suspend (LoginRequest, LoginProgressListener) -> LoginResult =
        { request, listener -> LoginController(getApplication()).performLogin(request, listener) }

    private val _progress = MutableLiveData<LoginProgress?>()
    val progress: LiveData<LoginProgress?> = _progress

    private val _result = MutableLiveData<LoginResult?>()
    val result: LiveData<LoginResult?> = _result

    private var loginJob: Job? = null

    fun start(request: LoginRequest) {
        _progress.postValue(null)
        _result.value = null

        loginJob =
            viewModelScope.launch {
                val outcome = performLogin(request) { progress -> _progress.postValue(progress) }

                loginJob = null
                _progress.postValue(null)
                _result.value = outcome
            }
    }

    fun consumeResult() {
        _result.value = null
    }

    fun cancelLogin(): Boolean {
        val job = loginJob ?: return false

        loginJob = null
        _progress.postValue(null)
        job.cancel(CancellationException("Login cancelled by user"))
        return true
    }
}
