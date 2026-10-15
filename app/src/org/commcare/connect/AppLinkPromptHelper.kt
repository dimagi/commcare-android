package org.commcare.connect

import org.commcare.activities.CommCareActivity
import org.commcare.android.database.connect.models.ConnectLinkedAppRecord
import org.commcare.connect.database.ConnectAppDatabaseUtil
import org.commcare.connect.database.ConnectUserDatabaseUtil
import org.commcare.connect.network.personalId.ConnectSsoHelper
import org.commcare.connect.network.personalId.TokenExceptionHandler
import org.commcare.core.network.AuthInfo
import org.commcare.dalvik.R
import org.commcare.google.services.analytics.AnalyticsParamValue
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil
import org.commcare.personalId.PersonalIdAppStateEvaluator
import org.commcare.personalId.PersonalIdUnlocker
import org.commcare.personalId.UnlockPolicy
import org.commcare.views.dialogs.StandardAlertDialog
import java.util.Date
import java.util.concurrent.TimeUnit

/**
 * The dialogs that offer to link a CommCare app to PersonalID, or to unlink one.
 *
 * An unmanaged app is offered twice: once on first sight, then again after
 * [DAYS_TO_SECOND_OFFER]. After the second offer it is never offered again.
 */
object AppLinkPromptHelper {
    private const val DAYS_TO_SECOND_OFFER = 30L

    @JvmStatic
    fun checkPersonalIdLink(
        activity: CommCareActivity<*>,
        personalIdManagedLogin: Boolean,
        appId: String,
        username: String,
        password: String,
        callback: ConnectActivityCompleteListener,
    ) {
        when (PersonalIdAppStateEvaluator.evaluateAppState(appId, username)) {
            PersonalIdSessionManager.ConnectAppManagement.Unmanaged -> {
                promptToLinkUnmanagedApp(activity, appId, username, password, callback)
            }

            PersonalIdSessionManager.ConnectAppManagement.PersonalId -> {
                promptToDelinkPersonalIdApp(activity, appId, username, personalIdManagedLogin, callback)
            }

            PersonalIdSessionManager.ConnectAppManagement.Connect -> {
                callback.connectActivityComplete(true, null)
            }
        }
    }

    private fun promptToLinkUnmanagedApp(
        activity: CommCareActivity<*>,
        appId: String,
        username: String,
        password: String,
        callback: ConnectActivityCompleteListener,
    ) {
        var linkedApp = ConnectAppDatabaseUtil.getConnectLinkedAppRecord(appId, username)
        val offerCheck = evaluateLinkOffer(linkedApp)

        if (!offerCheck.shouldOffer) {
            callback.connectActivityComplete(false, null)
            return
        }

        if (linkedApp == null) {
            linkedApp = ConnectAppDatabaseUtil.storeApp(appId, username, false, "", false)
        }

        updateLinkOfferDate(linkedApp, offerCheck.isSecondOffer)
        showLinkDialog(activity, linkedApp, username, password, callback)
    }

    private data class OfferCheckResult(
        val shouldOffer: Boolean,
        val isSecondOffer: Boolean,
    )

    private fun evaluateLinkOffer(linkedApp: ConnectLinkedAppRecord?): OfferCheckResult {
        if (linkedApp == null) return OfferCheckResult(true, false)
        val firstOffer = linkedApp.linkOfferDate1 ?: return OfferCheckResult(true, false)
        if (linkedApp.linkOfferDate2 != null) return OfferCheckResult(false, true)

        val millis = Date().time - firstOffer.time
        val days = TimeUnit.DAYS.convert(millis, TimeUnit.MILLISECONDS)
        return OfferCheckResult(days >= DAYS_TO_SECOND_OFFER, false)
    }

    private fun updateLinkOfferDate(
        linkedApp: ConnectLinkedAppRecord,
        isSecondOffer: Boolean,
    ) {
        if (isSecondOffer) {
            linkedApp.linkOfferDate2 = Date()
        } else {
            linkedApp.linkOfferDate1 = Date()
        }
    }

    private fun showLinkDialog(
        activity: CommCareActivity<*>,
        linkedApp: ConnectLinkedAppRecord,
        username: String,
        password: String,
        callback: ConnectActivityCompleteListener,
    ) {
        val dialog =
            StandardAlertDialog(
                activity.getString(R.string.personalid_link_app_title),
                activity.getString(R.string.personalid_link_app_message),
            )

        dialog.setPositiveButton(activity.getString(R.string.personalid_link_app_yes)) { _, _ ->
            activity.dismissAlertDialog()
            unlockAndLinkConnect(activity, linkedApp, username, password, callback)
        }

        dialog.setNegativeButton(activity.getString(R.string.personalid_link_app_no)) { _, _ ->
            activity.dismissAlertDialog()
            ConnectAppDatabaseUtil.storeApp(linkedApp)
            FirebaseAnalyticsUtil.reportPersonalIDLinking(
                linkedApp.appId,
                AnalyticsParamValue.FAILURE_USER_DENIED,
            )
            callback.connectActivityComplete(false, null)
        }

        activity.showAlertDialog(dialog)
    }

    private fun unlockAndLinkConnect(
        activity: CommCareActivity<*>,
        linkedApp: ConnectLinkedAppRecord,
        username: String,
        password: String,
        callback: ConnectActivityCompleteListener,
    ) {
        PersonalIdUnlocker.unlock(
            activity,
            UnlockPolicy.ALWAYS,
            object : ConnectActivityCompleteListener {
                override fun connectActivityComplete(
                    success: Boolean,
                    error: String?,
                ) {
                    if (!success) {
                        callback.connectActivityComplete(false, null)
                        FirebaseAnalyticsUtil.reportPersonalIDLinking(
                            linkedApp.appId,
                            AnalyticsParamValue.FAILURE_UNLOCK_FAILED,
                        )
                        return
                    }

                    linkedApp.linkToPersonalId(password)
                    FirebaseAnalyticsUtil.reportPersonalIDLinking(
                        linkedApp.appId,
                        AnalyticsParamValue.SYNC_SUCCESS,
                    )
                    ConnectAppDatabaseUtil.storeApp(linkedApp)

                    val user = ConnectUserDatabaseUtil.getUser()
                    ConnectSsoHelper.retrieveHqSsoToken(
                        activity,
                        user,
                        linkedApp,
                        username,
                        true,
                        object : ConnectSsoHelper.TokenCallback {
                            override fun tokenRetrieved(token: AuthInfo.TokenAuth) {
                                callback.connectActivityComplete(false, null)
                            }

                            override fun tokenUnavailable() {
                                TokenExceptionHandler.handleTokenUnavailableException(activity)
                                callback.connectActivityComplete(false, null)
                            }

                            override fun tokenRequestDenied() {
                                TokenExceptionHandler.handleTokenDeniedException()
                                callback.connectActivityComplete(false, null)
                            }
                        },
                    )
                }
            },
        )
    }

    private fun promptToDelinkPersonalIdApp(
        activity: CommCareActivity<*>,
        appId: String,
        username: String,
        personalIdManagedLogin: Boolean,
        callback: ConnectActivityCompleteListener,
    ) {
        // we only want to prompt when user chose non connect Id managed login
        if (personalIdManagedLogin) {
            callback.connectActivityComplete(false, null)
            return
        }

        val dialog =
            StandardAlertDialog(
                activity.getString(R.string.personalid_unlink_app_title),
                activity.getString(R.string.personalid_unlink_app_message),
            )

        dialog.setPositiveButton(activity.getString(R.string.personalid_link_app_yes)) { _, _ ->
            activity.dismissAlertDialog()
            PersonalIdUnlocker.unlock(
                activity,
                UnlockPolicy.ALWAYS,
                object : ConnectActivityCompleteListener {
                    override fun connectActivityComplete(
                        success: Boolean,
                        error: String?,
                    ) {
                        if (success) {
                            val linkedApp =
                                ConnectAppDatabaseUtil.getConnectLinkedAppRecord(appId, username)
                            if (linkedApp != null) {
                                linkedApp.severPersonalIdLink()
                                ConnectAppDatabaseUtil.storeApp(linkedApp)
                            }
                        }
                        callback.connectActivityComplete(false, null)
                    }
                },
            )
        }

        dialog.setNegativeButton(activity.getString(R.string.personalid_link_app_no)) { _, _ ->
            activity.dismissAlertDialog()
            callback.connectActivityComplete(false, null)
        }

        activity.showAlertDialog(dialog)
    }
}
