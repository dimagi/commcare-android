package org.commcare.services

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.commcare.preferences.HiddenPreferences

class SessionManager(
private val context: Context
) {
    companion object {
        private const val PREF_SESSION = "session_data"
        private const val SESSION_EXPIRATION_ATTR = "session_expiration"
    }

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var sessionJob: Job? = null

    private val prefs by lazy {
        context.getSharedPreferences(PREF_SESSION, Context.MODE_PRIVATE)
    }

    fun startSession() {
        val expirationTime = System.currentTimeMillis() + getSessionLength()
        saveExpiration(expirationTime)
        startTimer(expirationTime)
    }

    private fun getSessionLength() = (HiddenPreferences.getLoginDuration() * 1000).toLong()

    fun cancelSession() {
        sessionJob?.cancel()
        clearExpiration()
    }

    fun isSessionExpired(): Boolean {
        val expiration = getExpiration()
        return expiration > 0 && System.currentTimeMillis() >= expiration
    }

    fun restoreSessionIfNeeded() {
        val expiration = getExpiration()
        if (expiration > 0) {
            startTimer(expiration)
        }
    }

    private fun startTimer(expirationTime: Long) {
        sessionJob?.cancel()

        val remaining = expirationTime - System.currentTimeMillis()
        if (remaining <= 0) {
            expireSession()
            return
        }

        sessionJob = appScope.launch {
            delay(remaining)
            expireSession()
        }
    }

    private fun expireSession() {
        clearExpiration()

        // CommCare Session cleanup
    }

    private fun saveExpiration(time: Long) {
        prefs.edit().putLong(SESSION_EXPIRATION_ATTR, time).apply()
    }

    private fun getExpiration(): Long {
        return prefs.getLong(SESSION_EXPIRATION_ATTR, 0L)
    }

    private fun clearExpiration() {
        prefs.edit().remove(SESSION_EXPIRATION_ATTR).apply()
    }
}
