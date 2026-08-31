package org.commcare.personalId

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import org.commcare.CommCareApplication
import java.util.Date

object PersonalIdUserPreferences {
    private const val PREFS_NAME = "personalid_prefs"
    private const val KEY_EMAIL_OFFER_COUNT = "email_offer_count"
    private const val KEY_LAST_EMAIL_OFFER_DATE = "last_email_offer_date"
    private const val KEY_BACKUP_CODE_FAILED_ATTEMPTS = "backup_code_failed_attempts"
    private const val KEY_BACKUP_CODE_LOCKOUT_START = "backup_code_lockout_start"
    private const val KEY_BACKUP_CODE_WINDOW_START = "backup_code_window_start"
    private const val BACKUP_CODE_LOCKOUT_DURATION_MS = 24 * 60 * 60 * 1000L

    private fun prefs(): SharedPreferences = CommCareApplication.instance().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Records a backup-code failure and returns the new failure count within the current 24-hour
     * window. If the previous failure window has expired, the count resets before incrementing.
     */
    fun recordBackupCodeFailure(): Int {
        val now = System.currentTimeMillis()
        val windowStart = prefs().getLong(KEY_BACKUP_CODE_WINDOW_START, -1L)
        val baseCount =
            if (windowStart == -1L || now - windowStart >= BACKUP_CODE_LOCKOUT_DURATION_MS) {
                prefs().edit {
                    putLong(KEY_BACKUP_CODE_WINDOW_START, now)
                    remove(KEY_BACKUP_CODE_LOCKOUT_START)
                }
                0
            } else {
                prefs().getInt(KEY_BACKUP_CODE_FAILED_ATTEMPTS, 0)
            }
        val next = baseCount + 1
        prefs().edit { putInt(KEY_BACKUP_CODE_FAILED_ATTEMPTS, next) }
        return next
    }

    fun triggerBackupCodeLockout() {
        prefs().edit { putLong(KEY_BACKUP_CODE_LOCKOUT_START, System.currentTimeMillis()) }
    }

    fun isBackupCodeLockedOut(): Boolean {
        val start = prefs().getLong(KEY_BACKUP_CODE_LOCKOUT_START, -1L)
        return start != -1L && System.currentTimeMillis() - start < BACKUP_CODE_LOCKOUT_DURATION_MS
    }

    fun clearBackupCodeLockout() {
        prefs().edit {
            remove(KEY_BACKUP_CODE_FAILED_ATTEMPTS)
            remove(KEY_BACKUP_CODE_LOCKOUT_START)
            remove(KEY_BACKUP_CODE_WINDOW_START)
        }
    }

    /**
     *  Email Offer Count (Int) — 0 = never offered, 1 = first offer shown, 2 = both offers shown
     */
    @JvmStatic
    fun getEmailOfferCount(): Int {
        val p = prefs()
        return p.getInt(KEY_EMAIL_OFFER_COUNT, 0)
    }

    @JvmStatic
    fun setEmailOfferCount(value: Int?) {
        prefs().edit {
            if (value == null) remove(KEY_EMAIL_OFFER_COUNT) else putInt(KEY_EMAIL_OFFER_COUNT, value)
        }
    }

    /**
     * - last Email Offer Date (Date) — when the most recent offer was shown
     * - null = never offered
     */
    @JvmStatic
    fun getLastEmailOfferDate(): Date? =
        prefs()
            .getLong(KEY_LAST_EMAIL_OFFER_DATE, -1L)
            .takeIf { it != -1L }
            ?.let(::Date)

    @JvmStatic
    fun setLastEmailOfferDate(value: Date?) {
        prefs().edit {
            if (value == null) {
                remove(KEY_LAST_EMAIL_OFFER_DATE)
            } else {
                putLong(KEY_LAST_EMAIL_OFFER_DATE, value.time)
            }
        }
    }

    /** Remove every PersonalID preference. Called on PersonalId logout. */
    @JvmStatic
    fun clear() {
        prefs().edit { clear() }
    }
}
