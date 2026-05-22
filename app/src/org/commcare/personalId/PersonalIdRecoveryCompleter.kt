package org.commcare.personalId

import android.app.Activity
import org.commcare.CommCareNoficationManager
import org.commcare.android.database.connect.models.ConnectUserRecord
import org.commcare.android.database.connect.models.PersonalIdSessionData
import org.commcare.connect.database.ConnectDatabaseHelper
import org.commcare.connect.database.ConnectUserDatabaseUtil
import org.commcare.dalvik.R
import org.commcare.google.services.analytics.AnalyticsParamValue
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil
import org.commcare.utils.NotificationUtil
import org.javarosa.core.model.utils.DateUtils
import java.util.Date

/**
 * Finalises account recovery.
 * Mirrors the logic previously inlined in PersonalIdBackupCodeFragment.handleSuccessfulRecovery().
 * This will be used from:
 *  PersonalIdBackupCodeFragment: whenever the user already has a valid email and there is no need to show the email entry fragment (TODO https://dimagi.atlassian.net/browse/CCCT-2407).
 *  PersonalIdEmailFragment: whenever the user presses skip and is recovering the account
 *  PersonalIdEmailValidationFragment: whenever it validates OTP / skip and the user is recovering the account (TODO https://dimagi.atlassian.net/browse/CCCT-2378).
 * Comments here will be updated as and when ToDo tickets are done.
 */
object PersonalIdRecoveryCompleter {
    @JvmStatic
    fun finalizeAccountRecovery(
        activity: Activity,
        sessionData: PersonalIdSessionData,
    ) {
        ConnectDatabaseHelper.handleReceivedDbPassphrase(activity, sessionData.dbKey)

        val user =
            ConnectUserRecord(
                sessionData.phoneNumber,
                sessionData.personalId,
                sessionData.oauthPassword,
                sessionData.userName,
                sessionData.backupCode,
                Date(),
                sessionData.photoBase64,
                sessionData.demoUser ?: false,
                sessionData.requiredLock,
                sessionData.invitedUser,
            )
        user.email = sessionData.email
        ConnectUserDatabaseUtil.storeUser(activity, user)

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
