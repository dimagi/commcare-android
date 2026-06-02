package org.commcare.connect

import android.content.Context
import android.content.Intent
import org.commcare.activities.CommCareActivity
import org.commcare.activities.connect.PersonalIdActivity
import org.commcare.connect.database.ConnectUserDatabaseUtil
import org.commcare.dalvik.BuildConfig
import org.commcare.dalvik.R
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil
import org.commcare.personalId.PersonalIdUserPreferences
import org.commcare.views.dialogs.StandardAlertDialog
import java.util.Date
import java.util.concurrent.TimeUnit

object EmailOfferHelper {
    private const val DAYS_TO_SECOND_EMAIL_OFFER = 30L
    private const val EMAIL_OFFER_SCREEN = "EmailOfferDialog"

    private fun shouldOfferEmail(context: Context): Boolean {
        if (!PersonalIdManager.getInstance().isloggedIn()) {
            return false
        }

        if (!ReleaseToggleHelper.isEmailOtpVerificationActive(context)) {
            return false
        }

        val user = ConnectUserDatabaseUtil.getUser(context)
        if (user.email != null) {
            return false
        }

        val count = PersonalIdUserPreferences.getEmailOfferCount()
        if (count >= 2) {
            return false
        }

        val lastOffer = PersonalIdUserPreferences.getLastEmailOfferDate() ?: return true
        val millis = Date().time - lastOffer.time
        val days = TimeUnit.DAYS.convert(millis, TimeUnit.MILLISECONDS)
        return days >= DAYS_TO_SECOND_EMAIL_OFFER
    }

    @JvmStatic
    fun checkEmailCollection(activity: CommCareActivity<*>) {
        if (!shouldOfferEmail(activity)) {
            return
        }

        // Increment count and record date BEFORE showing dialog (so the offer is recorded
        // even if the user dismisses the dialog by swiping away or backing out).
        val current = PersonalIdUserPreferences.getEmailOfferCount()
        PersonalIdUserPreferences.setEmailOfferCount(current + 1)
        PersonalIdUserPreferences.setLastEmailOfferDate(Date())

        showEmailOfferDialog(activity)
    }

    private fun showEmailOfferDialog(activity: CommCareActivity<*>) {
        val dialog =
            StandardAlertDialog(
                activity.getString(R.string.personalid_email_offer_title),
                activity.getString(R.string.personalid_email_offer_message),
            )
        dialog.setPositiveButton(activity.getString(R.string.personalid_email_offer_yes)) { _, _ ->
            FirebaseAnalyticsUtil.reportPersonalIDContinueClicked(EMAIL_OFFER_SCREEN, null)
            activity.dismissAlertDialog()
            launchPersonalIdForEmailCollection(activity)
        }
        dialog.setNegativeButton(activity.getString(R.string.personalid_email_offer_no)) { _, _ ->
            FirebaseAnalyticsUtil.reportPersonalIDContinueClicked(EMAIL_OFFER_SCREEN, "skip")
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
