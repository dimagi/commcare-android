package org.commcare.preferences

import androidx.core.content.edit
import androidx.preference.PreferenceManager
import org.commcare.CommCareApplication

object LocationPreferences {
    private const val KEY_LAST_ACCEPTED_LOCATION = "cc-last-accepted-location"
    private const val KEY_LAST_ACCEPTED_LOCATION_TIMESTAMP = "cc-last-accepted-location-timestamp"

    fun getLastAcceptedLocation(): String? =
        PreferenceManager
            .getDefaultSharedPreferences(CommCareApplication.instance())
            .getString(KEY_LAST_ACCEPTED_LOCATION, null)

    fun setLastAcceptedLocation(locationString: String) {
        PreferenceManager.getDefaultSharedPreferences(CommCareApplication.instance()).edit {
            putString(KEY_LAST_ACCEPTED_LOCATION, locationString)
        }
    }

    fun getLastAcceptedLocationTimestamp(): Long =
        PreferenceManager
            .getDefaultSharedPreferences(CommCareApplication.instance())
            .getLong(KEY_LAST_ACCEPTED_LOCATION_TIMESTAMP, 0L)

    fun setLastAcceptedLocationTimestamp(timestamp: Long) {
        PreferenceManager.getDefaultSharedPreferences(CommCareApplication.instance()).edit {
            putLong(KEY_LAST_ACCEPTED_LOCATION_TIMESTAMP, timestamp)
        }
    }
}
