package org.commcare.personalId.profile

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import org.commcare.android.database.connect.models.ConnectUserRecord
import org.commcare.connect.database.ConnectUserDatabaseUtil
import org.commcare.fragments.personalId.AttemptTracker
import org.commcare.fragments.personalId.EmailHelper

/**
 * Holds the in-progress Edit Profile form state in a [SavedStateHandle] so typed values
 * survive rotation, process death, and the round-trip to email verification. The persisted
 * [ConnectUserRecord] stays the single source of truth for the original field values.
 */
class PersonalIdProfileEditViewModel(
    application: Application,
    private val savedStateHandle: SavedStateHandle,
) : AndroidViewModel(application) {
    val user: ConnectUserRecord = ConnectUserDatabaseUtil.getUser(application)
    val emailOtpTracker = AttemptTracker()

    val currentName: String
        get() = savedStateHandle[KEY_CURRENT_NAME] ?: user.name

    val currentEmail: String
        get() = savedStateHandle[KEY_CURRENT_EMAIL] ?: user.email.orEmpty()

    fun onNameChanged(name: String) {
        savedStateHandle[KEY_CURRENT_NAME] = name.trim()
    }

    fun onEmailChanged(email: String) {
        savedStateHandle[KEY_CURRENT_EMAIL] = email.trim()
    }

    fun onPhotoUpdated(photoBase64: String) {
        user.photo = photoBase64
    }

    fun isNameModified(): Boolean = currentName != user.name

    fun isEmailModified(): Boolean = currentEmail != user.email.orEmpty()

    fun isModified(): Boolean = isNameModified() || isEmailModified()

    fun isNameValid(): Boolean = currentName.isNotBlank()

    fun isEmailValid(): Boolean =
        if (currentEmail.isEmpty()) {
            // Clearing an existing email is not allowed.
            user.email.isNullOrEmpty()
        } else {
            EmailHelper.isValidEmail(currentEmail)
        }

    fun isEmailEmpty(): Boolean = currentEmail.isEmpty()

    fun canSave(): Boolean = isModified() && isNameValid() && isEmailValid()

    /**
     * Applies the saved name to a freshly-read record so fields committed by other flows
     * since this ViewModel was created (e.g. a photo update) are not clobbered.
     */
    fun commitNameToRecord() {
        user.name = currentName
        val storedUser = ConnectUserDatabaseUtil.getUser(getApplication())
        storedUser.name = currentName
        ConnectUserDatabaseUtil.storeUser(getApplication(), storedUser)
    }

    companion object {
        private const val KEY_CURRENT_NAME = "current_name"
        private const val KEY_CURRENT_EMAIL = "current_email"
    }
}
