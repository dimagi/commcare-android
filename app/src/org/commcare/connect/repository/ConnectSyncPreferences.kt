package org.commcare.connect.repository

import android.content.Context
import android.content.SharedPreferences
import java.util.Date

/**
 * Manages sync timestamps for Connect endpoints.
 * Stores per-endpoint last sync times and session start time for refresh policies.
 */
class ConnectSyncPreferences(
    context: Context,
) {
    companion object {
        private const val PREFS_NAME = "connect_sync_prefs"
        private const val KEY_SESSION_START = "session_start_time"
        private const val KEY_LAST_SYNC_PREFIX = "last_sync_"

        @Volatile
        private var instance: ConnectSyncPreferences? = null

        fun getInstance(context: Context): ConnectSyncPreferences =
            instance ?: synchronized(this) {
                instance ?: ConnectSyncPreferences(context.applicationContext).also {
                    instance = it
                }
            }
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE,
        )

    fun markSessionStart() {
        prefs
            .edit()
            .putLong(KEY_SESSION_START, Date().time)
            .apply()
    }

    fun getSessionStartTime(): Date {
        val timestamp = prefs.getLong(KEY_SESSION_START, Date().time)
        return Date(timestamp)
    }

    fun storeLastSyncTime(endpoint: String) {
        val key = KEY_LAST_SYNC_PREFIX + endpoint.replace("/", "_")
        prefs
            .edit()
            .putLong(key, Date().time)
            .apply()
    }

    fun getLastSyncTime(endpoint: String): Date? {
        val key = KEY_LAST_SYNC_PREFIX + endpoint.replace("/", "_")
        val timestamp = prefs.getLong(key, -1)
        return if (timestamp == -1L) null else Date(timestamp)
    }

    fun shouldRefresh(
        endpoint: String,
        policy: RefreshPolicy,
    ): Boolean {
        return when (policy) {
            RefreshPolicy.ALWAYS -> true

            is RefreshPolicy.SESSION_AND_TIME_BASED -> {
                val lastSync = getLastSyncTime(endpoint) ?: return true
                val sessionStart = getSessionStartTime()
                val isNewSession = lastSync.before(sessionStart)
                val ageMs = Date().time - lastSync.time
                val isStale = ageMs >= policy.timeThresholdMs
                isNewSession || isStale
            }

            else -> throw IllegalArgumentException("Unknown refresh policy: $policy")
        }
    }

    /**
     * Clears all sync data (for testing or logout).
     */
    fun clearAll() {
        prefs.edit().clear().apply()
        markSessionStart()
    }
}
