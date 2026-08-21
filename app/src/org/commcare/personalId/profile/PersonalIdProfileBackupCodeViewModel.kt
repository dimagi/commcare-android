package org.commcare.personalId.profile

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel

class PersonalIdProfileBackupCodeViewModel(
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    var failedAttempts: Int
        get() = savedStateHandle[KEY_FAILED_ATTEMPTS] ?: 0
        set(value) {
            savedStateHandle[KEY_FAILED_ATTEMPTS] = value
        }

    fun reset() {
        failedAttempts = 0
    }

    companion object {
        private const val KEY_FAILED_ATTEMPTS = "failed_attempts"
    }
}
