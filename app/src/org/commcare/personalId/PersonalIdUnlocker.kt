package org.commcare.personalId

import androidx.annotation.VisibleForTesting
import org.commcare.activities.CommCareActivity
import org.commcare.connect.PersonalIdManager

private const val SESSION_UNLOCK_THRESHOLD_MS = 10 * 60 * 1000L // 10 minutes

object PersonalIdUnlocker {
    @VisibleForTesting
    internal var lastUnlockTime: Long? = null

    /**
     * Attempts a Personal ID biometric/PIN unlock, applying [policy] to decide
     * whether the prompt is actually shown. Always invokes [callback] with the result.
     *
     * [UnlockPolicy.ALWAYS] — always prompts (existing behaviour, no session bypass).
     * [UnlockPolicy.SESSION_WITH_TIME_THRESHOLD] — skips prompt if unlocked within
     *     the last 10 minutes; caller is responsible for first checking that the app
     *     is Personal ID managed before calling this.
     */
    fun unlock(
        activity: CommCareActivity<*>,
        policy: UnlockPolicy,
        callback: PersonalIdManager.ConnectActivityCompleteListener,
    ) {
        when (policy) {
            UnlockPolicy.ALWAYS -> performUnlock(activity, callback)
            UnlockPolicy.SESSION_WITH_TIME_THRESHOLD -> {
                if (requiresUnlockForSession()) {
                    performUnlock(activity, callback)
                } else {
                    callback.connectActivityComplete(true)
                }
            }
        }
    }

    private fun performUnlock(
        activity: CommCareActivity<*>,
        callback: PersonalIdManager.ConnectActivityCompleteListener,
    ) {
        PersonalIdManager.getInstance().unlockConnect(activity) { success ->
            if (success) lastUnlockTime = System.currentTimeMillis()
            callback.connectActivityComplete(success)
        }
    }

    @VisibleForTesting
    internal fun requiresUnlockForSession(): Boolean {
        val last = lastUnlockTime ?: return true
        return System.currentTimeMillis() - last > SESSION_UNLOCK_THRESHOLD_MS
    }

    /**
     * Resets session unlock state. Call on every app process start and on
     * Personal ID logout so stale unlock timestamps are never inherited.
     */
    fun resetSession() {
        lastUnlockTime = null
    }
}

enum class UnlockPolicy {
    /** Always prompts for biometric/PIN regardless of prior unlock in this session. */
    ALWAYS,

    /**
     * Skips the prompt if the user unlocked within [SESSION_UNLOCK_THRESHOLD_MS].
     * The caller must first verify the app is Personal ID managed before using this policy.
     */
    SESSION_WITH_TIME_THRESHOLD,
}
