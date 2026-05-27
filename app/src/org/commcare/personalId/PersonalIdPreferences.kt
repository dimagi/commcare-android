package org.commcare.personalId

import android.content.Context
import android.content.SharedPreferences
import java.util.Date

/**
 * SharedPreferences-backed store for the three flags that accompany `ConnectUserRecord.email`:
 *   - emailOfferCount (Int) — 0 = never offered, 1 = first offer shown, 2 = both offers shown
 *   - lastEmailOfferDate (Date) — when the most recent offer was shown
 *
 *
 * On PersonalID logout, call [clear] to wipe every key in this prefs file.
 */
object PersonalIdPreferences {
    private const val PREFS_NAME = "personalid_prefs"
    private const val KEY_EMAIL_VERIFIED = "email_verified"
    private const val KEY_EMAIL_OFFER_COUNT = "email_offer_count"
    private const val KEY_LAST_EMAIL_OFFER_DATE = "last_email_offer_date"

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    @JvmStatic
    fun getEmailOfferCount(context: Context): Int? {
        val p = prefs(context)
        return if (p.contains(KEY_EMAIL_OFFER_COUNT)) p.getInt(KEY_EMAIL_OFFER_COUNT, 0) else null
    }

    @JvmStatic
    fun setEmailOfferCount(
        context: Context,
        value: Int?,
    ) {
        prefs(context)
            .edit()
            .apply {
                if (value == null) remove(KEY_EMAIL_OFFER_COUNT) else putInt(KEY_EMAIL_OFFER_COUNT, value)
            }.apply()
    }

    @JvmStatic
    fun getLastEmailOfferDate(context: Context): Date? {
        val p = prefs(context)
        return if (p.contains(KEY_LAST_EMAIL_OFFER_DATE)) Date(p.getLong(KEY_LAST_EMAIL_OFFER_DATE, 0L)) else null
    }

    @JvmStatic
    fun setLastEmailOfferDate(
        context: Context,
        value: Date?,
    ) {
        prefs(context)
            .edit()
            .apply {
                if (value == null) remove(KEY_LAST_EMAIL_OFFER_DATE) else putLong(KEY_LAST_EMAIL_OFFER_DATE, value.time)
            }.apply()
    }

    /** Remove every PersonalID preference. Call on logout. */
    @JvmStatic
    fun clear(context: Context) {
        prefs(context).edit().clear().apply()
    }
}
