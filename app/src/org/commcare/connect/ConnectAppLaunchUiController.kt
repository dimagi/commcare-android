package org.commcare.connect

import android.content.Intent
import androidx.fragment.app.Fragment
import org.commcare.activities.DispatchActivity
import org.commcare.activities.HomeScreenBaseActivity
import org.commcare.connect.network.TokenExceptionHandler
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil
import org.commcare.login.LoginPhase
import org.commcare.login.LoginProgress
import org.commcare.util.LogTypes
import org.commcare.views.dialogs.CustomProgressDialog
import org.javarosa.core.services.Logger
import org.javarosa.core.services.locale.Localization

/**
 * Drives a silent Connect app launch from a fragment: shows a [CustomProgressDialog], runs
 * [ConnectAppLauncher], and routes the [LaunchOutcome] through [LaunchOutcomeRouter]. Shared by the
 * Connect surfaces that launch a seated app without showing LoginActivity.
 *
 * Hosting fragments must forward their `onDestroyView` to [cleanup] so the dialog is dismissed.
 */
class ConnectAppLaunchUiController(
    private val fragment: Fragment,
) {
    private var launchDialog: CustomProgressDialog? = null
    private var showingSyncDialog = false

    fun launch(
        isLearning: Boolean,
        appId: String,
    ) {
        val activity = fragment.requireActivity()
        showLaunchDialog(false)
        ConnectAppLauncher().start(
            fragment.viewLifecycleOwner,
            activity,
            appId,
            isLearning,
            { progress -> activity.runOnUiThread { updateLaunchProgress(progress) } },
            { outcome -> handleLaunchOutcome(outcome, isLearning, appId) },
        )
    }

    fun cleanup() {
        dismissLaunchDialog()
    }

    private fun updateLaunchProgress(progress: LoginProgress) {
        if (!fragment.isAdded) {
            return
        }

        val syncing = progress.phase == LoginPhase.Syncing
        if (launchDialog == null || syncing != showingSyncDialog) {
            showLaunchDialog(syncing)
        }
        when (progress.phase) {
            LoginPhase.Seating -> {
                launchDialog?.updateTitle(Localization.get("seating.app"))
                launchDialog?.updateMessage(Localization.get("seating.app"))
            }
            LoginPhase.SigningIn -> {
                launchDialog?.updateTitle(Localization.get("key.manage.title"))
                launchDialog?.updateMessage(Localization.get("key.manage.start"))
            }
            LoginPhase.Syncing -> {}
        }
        progress.message?.let { launchDialog?.updateMessage(it) }
        val percent = progress.percent
        if (syncing && percent != null) {
            launchDialog?.updateProgressBar(percent, PROGRESS_BAR_MAX)
        }
    }

    private fun handleLaunchOutcome(
        outcome: LaunchOutcome,
        isLearning: Boolean,
        appId: String,
    ) {
        val activity = fragment.requireActivity()
        LaunchOutcomeRouter.dispatch(
            outcome,
            object : LaunchActions {
                override fun dismissProgress() = dismissLaunchDialog()

                override fun launchHome() = HomeScreenBaseActivity.launchHome(activity)

                override fun handleTokenDenied() = TokenExceptionHandler.handleTokenDeniedException()

                override fun recoverFromSeatFailure() {
                    val intent =
                        Intent(activity, DispatchActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    fragment.startActivity(intent)
                    activity.finish()
                }

                override fun fallBackToLegacyLaunch() {
                    ConnectAppUtils.launchApp(activity, isLearning, appId)
                }

                override fun reportFailure(reason: String) {
                    Logger.log(
                        LogTypes.TYPE_ERROR_WORKFLOW,
                        "Connect launch failed for app $appId: $reason",
                    )
                    FirebaseAnalyticsUtil.reportCccAppFailedAutoLogin(appId)
                }
            },
        )
    }

    private fun showLaunchDialog(syncing: Boolean) {
        if (fragment.childFragmentManager.isStateSaved) {
            return
        }
        dismissLaunchDialog()
        launchDialog =
            if (syncing) {
                CustomProgressDialog
                    .newInstance(
                        Localization.get("sync.communicating.title"),
                        Localization.get("sync.progress.starting"),
                        LAUNCH_DIALOG_TASK_ID,
                    ).apply { addProgressBar() }
            } else {
                CustomProgressDialog.newInstance(
                    Localization.get("seating.app"),
                    Localization.get("seating.app"),
                    LAUNCH_DIALOG_TASK_ID,
                )
            }
        showingSyncDialog = syncing
        launchDialog?.showNow(fragment.childFragmentManager, LAUNCH_DIALOG_TAG)
    }

    private fun dismissLaunchDialog() {
        launchDialog?.let {
            if (it.isAdded) {
                it.dismissAllowingStateLoss()
            }
        }
        launchDialog = null
    }

    companion object {
        private const val LAUNCH_DIALOG_TAG = "connect_launch_progress"

        // Negative so it can't collide with the positive task ids CommCareActivity assigns to real tasks.
        private const val LAUNCH_DIALOG_TASK_ID = -10
        private const val PROGRESS_BAR_MAX = 100
    }
}
