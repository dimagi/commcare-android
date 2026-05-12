package org.commcare.personalId

import android.content.Context
import android.os.SystemClock
import android.widget.Toast
import androidx.annotation.VisibleForTesting
import androidx.biometric.BiometricPrompt
import org.commcare.activities.CommCareActivity
import org.commcare.android.database.connect.models.PersonalIdSessionData
import org.commcare.android.security.AndroidKeyStore
import org.commcare.connect.PersonalIdManager
import org.commcare.connect.database.ConnectUserDatabaseUtil
import org.commcare.dalvik.BuildConfig
import org.commcare.dalvik.R
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil
import org.commcare.utils.BiometricsHelper
import org.commcare.utils.EncryptionKeyProvider
import org.javarosa.core.services.Logger
import kotlin.time.Duration.Companion.minutes

private val SESSION_UNLOCK_THRESHOLD_MS = 10.minutes.inWholeMilliseconds
internal const val BIOMETRIC_INVALIDATION_KEY = "biometric-invalidation-key"

/** Middleware to manage Personal ID unlock prompts with session-based bypass logic. */
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
            UnlockPolicy.ALWAYS -> {
                performUnlock(activity, callback)
            }

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
        val personalIdManager = PersonalIdManager.getInstance()
        if (BuildConfig.IS_QA_AUTOMATION) {
            lastUnlockTime = SystemClock.elapsedRealtime()
            personalIdManager.userUnlockedPersonalId()
            callback.connectActivityComplete(true)
            return
        }

        logBiometricInvalidations(activity)
        val bioManager = personalIdManager.getBiometricManager(activity)
        val user = ConnectUserDatabaseUtil.getUser(activity)

        val callbacks =
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationError(
                    errorCode: Int,
                    errString: CharSequence,
                ) = callback.connectActivityComplete(false)

                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    lastUnlockTime = SystemClock.elapsedRealtime()
                    personalIdManager.userUnlockedPersonalId()
                    callback.connectActivityComplete(true)
                }

                override fun onAuthenticationFailed() = callback.connectActivityComplete(false)
            }

        when {
            BiometricsHelper.isFingerprintConfigured(activity, bioManager) -> {
                val allowOtherOptions =
                    BiometricsHelper.isPinConfigured(activity, bioManager) &&
                        PersonalIdSessionData.PIN == user.requiredLock
                BiometricsHelper.authenticateFingerprint(activity, bioManager, callbacks, allowOtherOptions)
            }

            BiometricsHelper.isPinConfigured(activity, bioManager) &&
                PersonalIdSessionData.PIN == user.requiredLock -> {
                BiometricsHelper.authenticatePin(activity, bioManager, callbacks)
            }

            else -> {
                callback.connectActivityComplete(false)
                Logger.exception(
                    "No unlock method available when trying to unlock PersonalId",
                    Exception("No unlock option"),
                )
                Toast
                    .makeText(
                        activity,
                        activity.getString(R.string.connect_unlock_unavailable),
                        Toast.LENGTH_SHORT,
                    ).show()
            }
        }
    }

    private fun logBiometricInvalidations(activity: Context) {
        if (!AndroidKeyStore.doesKeyExist(BIOMETRIC_INVALIDATION_KEY)) return
        val provider = EncryptionKeyProvider(activity, true, BIOMETRIC_INVALIDATION_KEY)
        if (!provider.isKeyValid) {
            FirebaseAnalyticsUtil.reportBiometricInvalidated()
            provider.deleteKey()
            provider.getKeyForEncryption()
        }
    }

    @VisibleForTesting
    internal fun requiresUnlockForSession(): Boolean {
        val last = lastUnlockTime ?: return true
        return SystemClock.elapsedRealtime() - last >= SESSION_UNLOCK_THRESHOLD_MS
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
