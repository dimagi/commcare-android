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

    private fun prefs(): SharedPreferences = CommCareApplication.instance().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

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
