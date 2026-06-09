package org.commcare.connect

import android.app.Activity
import android.content.Intent
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import org.commcare.CommCareApplication
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

/** The app a Connect launch is targeting; [isLearning] selects the learn vs delivery app type. */
internal data class LaunchTarget(
    val appId: String,
    val isLearning: Boolean,
)

/** Pure description of how the launch dialog should reflect a [LoginProgress], using localization keys. */
internal data class LaunchDialogState(
    val showSyncDialog: Boolean,
    val titleKey: String?,
    val messageKey: String?,
    val overrideMessage: String?,
    val percent: Int?,
)

internal object LaunchProgressMapper {
    fun map(progress: LoginProgress): LaunchDialogState {
        val syncing = progress.phase == LoginPhase.Syncing
        val titleKey: String?
        val messageKey: String?
        when (progress.phase) {
            LoginPhase.Seating -> {
                titleKey = "seating.app"
                messageKey = "seating.app"
            }
            LoginPhase.SigningIn -> {
                titleKey = "key.manage.title"
                messageKey = "key.manage.start"
            }
            // The sync dialog supplies its own title/message; only the percent is updated per progress.
            LoginPhase.Syncing -> {
                titleKey = null
                messageKey = null
            }
        }
        return LaunchDialogState(
            showSyncDialog = syncing,
            titleKey = titleKey,
            messageKey = messageKey,
            overrideMessage = progress.message,
            percent = if (syncing) progress.percent else null,
        )
    }
}

/**
 * Drives a silent Connect app launch from a fragment: shows a [CustomProgressDialog], runs
 * [ConnectAppLauncher], and routes the [LaunchOutcome] through [LaunchOutcomeRouter]. Shared by the
 * Connect surfaces that launch a seated app without showing LoginActivity.
 *
 * The dialog is dismissed automatically when the fragment's view is destroyed, so callers don't
 * have to manage cleanup themselves.
 */
class ConnectAppLaunchUiController
    @JvmOverloads
    constructor(
        private val fragment: Fragment,
        private val launcher: ConnectAppLauncher = ConnectAppLauncher(),
    ) {
        private var launchDialog: CustomProgressDialog? = null
        private var showingSyncDialog = false
        private var observedLifecycle: Lifecycle? = null

        // Registered at construction (fragment init, the only valid time) so a back-out of the launched
        // app's Home is delivered back here and can return the worker to the opportunities list.
        private val homeResultLauncher: ActivityResultLauncher<Intent> =
            fragment.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                onHomeResult(result)
            }

        fun launchLearningApp(appId: String) = launch(LaunchTarget(appId, isLearning = true))

        fun launchDeliveryApp(appId: String) = launch(LaunchTarget(appId, isLearning = false))

        private fun launch(target: LaunchTarget) {
            val activity = fragment.requireActivity()
            val owner = fragment.viewLifecycleOwner
            registerDialogCleanup(owner)
            showLaunchDialog(false)
            launcher.start(
                owner,
                activity,
                target.appId,
                target.isLearning,
                { progress -> activity.runOnUiThread { updateLaunchProgress(progress) } },
                { outcome -> handleLaunchOutcome(outcome, activity, target) },
            )
        }

        private fun updateLaunchProgress(progress: LoginProgress) {
            if (!fragment.isAdded) {
                return
            }

            val state = LaunchProgressMapper.map(progress)
            if (launchDialog == null || state.showSyncDialog != showingSyncDialog) {
                showLaunchDialog(state.showSyncDialog)
            }
            state.titleKey?.let { launchDialog?.updateTitle(Localization.get(it)) }
            state.messageKey?.let { launchDialog?.updateMessage(Localization.get(it)) }
            state.overrideMessage?.let { launchDialog?.updateMessage(it) }
            state.percent?.let { launchDialog?.updateProgressBar(it, PROGRESS_BAR_MAX) }
        }

        private fun handleLaunchOutcome(
            outcome: LaunchOutcome,
            activity: Activity,
            target: LaunchTarget,
        ) {
            if (!fragment.isAdded) {
                return
            }
            LaunchOutcomeRouter.dispatch(
                outcome,
                object : LaunchActions {
                    override fun dismissProgress() = dismissLaunchDialog()

                    override fun launchHome() = homeResultLauncher.launch(HomeScreenBaseActivity.buildHomeLaunchIntent(activity))

                    override fun handleTokenDenied() = TokenExceptionHandler.handleTokenDeniedException()

                    override fun recoverFromSeatFailure() {
                        val intent =
                            Intent(activity, DispatchActivity::class.java)
                                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        fragment.startActivity(intent)
                        activity.finish()
                    }

                    override fun fallBackToLegacyLaunch() {
                        ConnectAppUtils.launchApp(activity, target.isLearning, target.appId)
                    }

                    override fun reportFailure(reason: String) {
                        Logger.log(
                            LogTypes.TYPE_ERROR_WORKFLOW,
                            "Connect launch failed for app ${target.appId}: $reason",
                        )
                        FirebaseAnalyticsUtil.reportCccAppFailedAutoLogin(target.appId)
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
                    // Title and message intentionally share "seating.app" (carried over from LoginActivity);
                    // distinct copy is an open UX question tracked on the PR, not changed here.
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

        private fun onHomeResult(result: ActivityResult) {
            if (!fragment.isAdded) {
                return
            }
            // Backing out of the Connect-managed app Home (RESULT_CANCELED) ends the app session and
            // returns to the opportunities list. RESULT_OK (logout / app switch) keeps today's behavior.
            if (result.resultCode == Activity.RESULT_OK) {
                return
            }
            CommCareApplication.instance().closeUserSession()
            ConnectNavHelper.goToConnectJobsList(fragment.requireActivity(), clearTop = true)
        }

        private fun registerDialogCleanup(owner: LifecycleOwner) {
            if (observedLifecycle === owner.lifecycle) {
                return
            }
            observedLifecycle = owner.lifecycle
            owner.lifecycle.addObserver(
                object : DefaultLifecycleObserver {
                    override fun onDestroy(owner: LifecycleOwner) {
                        dismissLaunchDialog()
                        observedLifecycle = null
                    }
                },
            )
        }

        companion object {
            private const val LAUNCH_DIALOG_TAG = "connect_launch_progress"

            // Negative so it can't collide with the positive task ids CommCareActivity assigns to real tasks.
            private const val LAUNCH_DIALOG_TASK_ID = -10
            private const val PROGRESS_BAR_MAX = 100
        }
    }
