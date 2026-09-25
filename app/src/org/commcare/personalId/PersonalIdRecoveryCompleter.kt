package org.commcare.personalId

import android.app.Activity
import org.commcare.CommCareNoficationManager
import org.commcare.android.database.connect.models.PersonalIdSessionData
import org.commcare.connect.PersonalIdManager
import org.commcare.dalvik.R
import org.commcare.google.services.analytics.AnalyticsParamValue
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil
import org.commcare.utils.NotificationUtil
import org.javarosa.core.model.utils.DateUtils

/**
 * Finalises account recovery.
 * This will be used from:
 *  PersonalIdBackupCodeFragment: whenever the user already has a valid email or the email toggle is inactive and the user is recovering the account
 *  PersonalIdEmailFragment: whenever the user presses skip and is recovering the account
 *  PersonalIdEmailValidationFragment: whenever it validates OTP / skip and the user is recovering the account
 */
object PersonalIdRecoveryCompleter {
    @JvmStatic
    fun finalizeAccountRecovery(
        activity: Activity,
        sessionData: PersonalIdSessionData,
    ) {
        PersonalIdManager.getInstance().onAccountConfigurationSuccess(sessionData)
        logRecoverySuccessResult()
        notifySecondDeviceLoginIfApplicable(activity, sessionData)
    }

    private fun logRecoverySuccessResult() {
        FirebaseAnalyticsUtil.reportPersonalIdAccountRecovered(
            true,
            AnalyticsParamValue.CCC_RECOVERY_METHOD_BACKUPCODE,
        )
    }

    private fun notifySecondDeviceLoginIfApplicable(
        activity: Activity,
        sessionData: PersonalIdSessionData,
    ) {
        val previousDevice = sessionData.previousDevice ?: return
        val titleId = R.string.personalid_second_device_login_title
        val message =
            if (sessionData.lastAccessed != null) {
                activity.getString(
                    R.string.personalid_second_device_login_message,
                    previousDevice,
                    DateUtils.getShortStringValue(sessionData.lastAccessed),
                )
            } else {
                activity.getString(
                    R.string.personalid_second_device_login_message_no_date,
                    previousDevice,
                )
            }
        NotificationUtil.showNotification(
            activity,
            CommCareNoficationManager.NOTIFICATION_CHANNEL_SERVER_COMMUNICATIONS_ID,
            titleId,
            activity.getString(titleId),
            message,
            null,
        )
    }
}
