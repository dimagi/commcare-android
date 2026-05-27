package org.commcare.connect

import android.content.Context
import android.content.Intent
import org.commcare.activities.CommCareActivity
import org.commcare.activities.connect.PersonalIdActivity
import org.commcare.connect.database.ConnectUserDatabaseUtil
import org.commcare.dalvik.BuildConfig
import org.commcare.dalvik.R
import org.commcare.personalId.PersonalIdPreferences
import org.commcare.views.dialogs.StandardAlertDialog
import java.util.Date
import java.util.concurrent.TimeUnit

object EmailOfferHelper {
    private const val DAYS_TO_SECOND_EMAIL_OFFER = 30L

    // Shortened on CCC Staging so QA can verify the second email offer without waiting the full window of 30 days.
    private const val DAYS_TO_SECOND_EMAIL_OFFER_STAGING = 0L

    @JvmStatic
    fun shouldOfferEmail(context: Context): Boolean {
        if (!ReleaseToggleHelper.isEmailOtpVerificationActive(context)) {
            return false
        }

        val user = ConnectUserDatabaseUtil.getUser(context)
        if (user.email != null) {
            return false
        }

        val count = PersonalIdPreferences.getEmailOfferCount(context)
        if (count != null && count >= 2) {
            return false
        }

        val lastOffer = PersonalIdPreferences.getLastEmailOfferDate(context) ?: return true
        val millis = Date().time - lastOffer.time
        val days = TimeUnit.DAYS.convert(millis, TimeUnit.MILLISECONDS)
        val secondOfferThreshold =
            if (BuildConfig.FLAVOR == "cccStaging") DAYS_TO_SECOND_EMAIL_OFFER_STAGING else DAYS_TO_SECOND_EMAIL_OFFER
        return days >= secondOfferThreshold
    }

    @JvmStatic
    fun checkEmailCollection(activity: CommCareActivity<*>) {
        if (!shouldOfferEmail(activity)) {
            return
        }

        // Increment count and record date BEFORE showing dialog (so the offer is recorded
        // even if the user dismisses the dialog by swiping away or backing out).
        val current = PersonalIdPreferences.getEmailOfferCount(activity)
        PersonalIdPreferences.setEmailOfferCount(activity, (current ?: 0) + 1)
        PersonalIdPreferences.setLastEmailOfferDate(activity, Date())

        showEmailOfferDialog(activity)
    }

    private fun showEmailOfferDialog(activity: CommCareActivity<*>) {
        val dialog =
            StandardAlertDialog(
                activity.getString(R.string.personalid_email_offer_title),
                activity.getString(R.string.personalid_email_offer_message),
            )
        dialog.setPositiveButton(activity.getString(R.string.personalid_email_offer_yes)) { _, _ ->
            activity.dismissAlertDialog()
            launchPersonalIdForEmailCollection(activity)
        }
        dialog.setNegativeButton(activity.getString(R.string.personalid_email_offer_no)) { _, _ ->
            activity.dismissAlertDialog()
        }
        activity.showAlertDialog(dialog)
    }

    private fun launchPersonalIdForEmailCollection(activity: CommCareActivity<*>) {
        val intent = Intent(activity, PersonalIdActivity::class.java)
        intent.putExtra(PersonalIdActivity.EXTRA_EXISTING_USER_EMAIL_FLOW, true)
        activity.startActivity(intent)
    }
}
