package org.commcare.home

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.google.android.play.core.install.model.InstallErrorCode
import org.commcare.CommCareApplication
import org.commcare.activities.UpdateActivity
import org.commcare.appupdate.AppUpdateController.IN_APP_UPDATE_REQUEST_CODE
import org.commcare.appupdate.AppUpdateControllerFactory
import org.commcare.appupdate.AppUpdateState
import org.commcare.appupdate.FlexibleAppUpdateController
import org.commcare.google.services.analytics.AnalyticsParamValue
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil
import org.commcare.heartbeat.UpdatePromptHelper
import org.commcare.preferences.HiddenPreferences
import org.commcare.util.LogTypes
import org.commcare.utils.ConnectivityStatus
import org.commcare.views.dialogs.StandardAlertDialog
import org.commcare.views.notifications.NotificationMessageFactory
import org.javarosa.core.services.Logger
import org.javarosa.core.services.locale.Localization

/**
 * Owns the home screen's Play Core binary update and CommCare content (CCZ) update.
 *
 * Registered once on the host lifecycle rather than per session, because rebuilding the
 * [FlexibleAppUpdateController] would leak its Play Core listener. Reads the session through
 * [session], which resolves the live session per call.
 */
class AppUpdateDelegate(
    private val host: HomeActivityHost,
    private val session: SeatedAppSession,
    private val networkAvailable: (Context) -> Boolean = { ConnectivityStatus.isNetworkAvailable(it) },
    private val controllerFactory: (Runnable, Context) -> FlexibleAppUpdateController =
        { callback, context -> AppUpdateControllerFactory.create(callback, context) },
) : DefaultLifecycleObserver {
    private var controller: FlexibleAppUpdateController? = null

    /** Whether the binary update should be offered as an explicit action instead of auto-starting. */
    var showCommCareUpdateMenu: Boolean = false
        private set

    override fun onCreate(owner: LifecycleOwner) {
        if (controller != null) {
            return
        }
        controller =
            controllerFactory(Runnable { handleAppUpdate() }, host.hostContext.applicationContext)
                .also { it.register() }
    }

    override fun onDestroy(owner: LifecycleOwner) {
        controller?.unregister()
        controller = null
    }

    /** Start the Play Core binary update flow. */
    fun startCommCareUpdate() {
        controller?.startUpdate(host.hostActivity)
    }

    /** Launch the CommCare content update screen. */
    fun launchUpdateActivity(autoProceedUpdateInstall: Boolean) {
        val intent =
            Intent(host.hostContext.applicationContext, UpdateActivity::class.java)
                .putExtra(UpdateActivity.KEY_PROCEED_AUTOMATICALLY, autoProceedUpdateInstall)
        host.hostActivity.startActivity(intent)
    }

    /** Prompt for a pending APK or CCZ update; returns whether the user was prompted. */
    fun promptForUpdateIfNeeded(skipOptionalUpdates: Boolean): Boolean =
        UpdatePromptHelper.promptForUpdateIfNeeded(host.hostActivity, skipOptionalUpdates)

    /** Handle the in-app update result; returns whether [requestCode] belonged to this delegate. */
    fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
    ): Boolean {
        if (requestCode != IN_APP_UPDATE_REQUEST_CODE) {
            return false
        }
        val availableVersion = controller?.availableVersionCode()
        if (resultCode == Activity.RESULT_CANCELED && availableVersion != null) {
            HiddenPreferences.incrementCommCareUpdateCancellationCounter(availableVersion.toString())
            session.hideInAppUpdate()
        }
        return true
    }

    private fun handleAppUpdate() {
        val controller = this.controller ?: return
        when (controller.status) {
            AppUpdateState.UNAVAILABLE -> {
                if (networkAvailable(host.hostContext)) {
                    session.hideInAppUpdate()
                } else {
                    Logger.log(LogTypes.TYPE_NETWORK, "No Internet")
                }
            }

            AppUpdateState.AVAILABLE -> {
                handleUpdateAvailable(controller)
            }

            AppUpdateState.DOWNLOADING -> {
                CommCareApplication.notificationManager().reportNotificationMessage(
                    NotificationMessageFactory.message(
                        NotificationMessageFactory.StockMessages.InApp_Update,
                        APP_UPDATE_NOTIFICATION,
                    ),
                )
                if (showCommCareUpdateMenu) {
                    showCommCareUpdateMenu = false
                    host.refreshActionSurface()
                }
            }

            AppUpdateState.DOWNLOADED -> {
                CommCareApplication.notificationManager().clearNotifications(APP_UPDATE_NOTIFICATION)
                showRestartDialog(controller)
                FirebaseAnalyticsUtil.reportInAppUpdateResult(
                    true,
                    AnalyticsParamValue.IN_APP_UPDATE_SUCCESS,
                )
            }

            AppUpdateState.FAILED -> {
                val errorReason = errorReasonFor(controller.errorCode)
                Logger.log(LogTypes.TYPE_CC_UPDATE, "CommCare In App Update failed because : $errorReason")
                CommCareApplication.notificationManager().clearNotifications(APP_UPDATE_NOTIFICATION)
                Toast.makeText(host.hostContext, Localization.get(errorReason), Toast.LENGTH_LONG).show()
                FirebaseAnalyticsUtil.reportInAppUpdateResult(false, errorReason)
            }
        }
    }

    /**
     * Re-checks `shouldShowInAppUpdate()` because [AppUpdateControllerFactory] only checks it at
     * construction, which a session-less host does before any session exists.
     */
    private fun handleUpdateAvailable(controller: FlexibleAppUpdateController) {
        if (!session.shouldShowInAppUpdate()) {
            return
        }
        val cancellations =
            HiddenPreferences.getCommCareUpdateCancellationCounter(controller.availableVersionCode().toString())
        if (cancellations > MAX_CC_UPDATE_CANCELLATION) {
            showCommCareUpdateMenu = true
            host.refreshActionSurface()
            return
        }
        startCommCareUpdate()
    }

    private fun showRestartDialog(controller: FlexibleAppUpdateController) {
        val dialog =
            StandardAlertDialog.getBasicAlertDialog(
                Localization.get("in.app.update.installed.title"),
                Localization.get("in.app.update.installed.detail"),
                null,
            )
        dialog.setPositiveButton(Localization.get("in.app.update.dialog.restart")) { shown, _ ->
            controller.completeUpdate()
            shown.dismiss()
        }
        dialog.setNegativeButton(Localization.get("in.app.update.dialog.cancel")) { shown, _ ->
            shown.dismiss()
        }
        host.showAlertDialog(dialog)
    }

    private fun errorReasonFor(errorCode: Int): String =
        when (errorCode) {
            InstallErrorCode.ERROR_INSTALL_NOT_ALLOWED -> "in.app.update.error.not.allowed"
            InstallErrorCode.NO_ERROR_PARTIALLY_ALLOWED -> "in.app.update.error.partially.allowed"
            InstallErrorCode.ERROR_PLAY_STORE_NOT_FOUND -> "in.app.update.error.playstore"
            InstallErrorCode.ERROR_INVALID_REQUEST -> "in.app.update.error.invalid.request"
            InstallErrorCode.ERROR_INTERNAL_ERROR -> "in.app.update.error.internal.error"
            else -> "in.app.update.error.unknown"
        }

    private companion object {
        const val APP_UPDATE_NOTIFICATION = "app_update_notification"
        const val MAX_CC_UPDATE_CANCELLATION = 3
    }
}
