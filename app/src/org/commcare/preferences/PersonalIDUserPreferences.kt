package org.commcare.preferences

import android.content.Context
import androidx.core.content.edit
import org.commcare.CommCareApplication

class PersonalIDUserPreferences {
    companion object {
        private const val PREF_NAME = "personalid_user_prefs"
        private const val KEY_LAST_PHOTO_UPLOAD_FAILED = "last_photo_upload_failed"

        private val prefs =
            CommCareApplication.instance().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

        fun setLastPhotoUploadFailed(failed: Boolean) {
            prefs.edit { putBoolean(KEY_LAST_PHOTO_UPLOAD_FAILED, failed) }
        }

        fun resetLastPhotoUploadFailed() {
            setLastPhotoUploadFailed(false)
        }

        fun getLastPhotoUploadFailed(): Boolean =
            prefs.getBoolean(KEY_LAST_PHOTO_UPLOAD_FAILED, false)
    }
}
