package org.commcare.home

import org.commcare.CommCareApplication
import org.commcare.activities.DriftHelper
import org.commcare.preferences.HiddenPreferences
import org.commcare.views.dialogs.StandardAlertDialog
import org.javarosa.core.services.locale.Localization

/**
 * The checks that run once, on the first home launch after login. Each step belongs to the
 * delegate that owns its feature; this class owns their order, the demo warning, the drift
 * warning and the one-time login flags.
 */
class LaunchChecksDelegate(
    private val host: HomeActivityHost,
    private val sessionLaunch: SessionLaunchDelegate,
    private val sessionNavigation: SessionNavigationDelegate,
    private val sync: SyncDelegate,
    private val appUpdate: AppUpdateDelegate,
    private val pin: PinDelegate,
) {
    /**
     * Run the post-login launch checks. The order is deliberate: demo mode skips everything else,
     * form and session restoration come before any sync that could invalidate them, a post-update
     * sync includes the plain form send, and the PIN and drift checks run only once nothing else
     * has taken over.
     *
     * @return true when a step claimed the launch by starting another screen or a blocking task
     */
    fun run(): Boolean =
        try {
            runInOrder()
        } finally {
            clearOneTimeLoginActionFlags()
        }

    private fun runInOrder(): Boolean {
        if (host.isDemoUser()) {
            showDemoModeWarning()
            return false
        }
        if (sessionLaunch.prepareUpdateInfoForm()) {
            sessionNavigation.startNextSessionStepSafe()
            return true
        }
        if (sessionLaunch.tryRestoringFormFromSessionExpiration()) {
            return true
        }
        if (sessionLaunch.prepareSessionRestore()) {
            sessionNavigation.startNextSessionStep()
            return true
        }
        if (sync.runPostUpdateSyncIfNeeded()) {
            return true
        }
        if (sync.runPendingSyncRequestIfNeeded()) {
            return true
        }
        if (appUpdate.promptForUpdateIfNeeded(false)) {
            return true
        }
        pin.checkLaunchConditions()
        checkForDrift()
        return false
    }

    /**
     * Whatever the launch checks did, the actions behind these flags happen on the first launch
     * after login or not at all.
     */
    private fun clearOneTimeLoginActionFlags() {
        HiddenPreferences.setPostUpdateSyncNeeded(false)
        HiddenPreferences.clearInterruptedSSD()
        HiddenPreferences.clearPendingSyncRequest(
            CommCareApplication
                .instance()
                .session.loggedInUser.username,
        )
    }

    private fun showDemoModeWarning() {
        val dialog =
            StandardAlertDialog.getBasicAlertDialogWithIcon(
                Localization.get("demo.mode.warning.title"),
                Localization.get("demo.mode.warning.main"),
                android.R.drawable.ic_dialog_info,
                null,
            )
        dialog.setEmphasizedMessage(Localization.get("demo.mode.warning.emphasized"))
        host.showAlertDialog(dialog)
    }

    /** Warn while the device clock drifts from the server's, at most once a day. */
    private fun checkForDrift() {
        if (DriftHelper.shouldShowDriftWarning() && DriftHelper.getCurrentDrift() != 0L) {
            host.showAlertDialog(DriftHelper.getDriftDialog(host.hostContext))
            DriftHelper.updateLastDriftWarningTime()
        }
    }
}
