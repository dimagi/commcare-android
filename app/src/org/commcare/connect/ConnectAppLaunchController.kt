package org.commcare.connect

import android.app.Activity
import android.content.Intent
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import org.commcare.activities.DispatchActivity
import org.commcare.activities.HomeScreenBaseActivity
import org.commcare.activities.LoginActivity
import org.commcare.dalvik.R
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil
import org.commcare.login.LoginPhase
import org.commcare.login.LoginProgress
import org.commcare.util.LogTypes
import org.commcare.views.dialogs.CustomProgressDialog
import org.javarosa.core.services.Logger

/** The app a Connect launch is targeting; [isLearning] selects the learn vs delivery app type. */
internal data class LaunchTarget(
    val appId: String,
    val isLearning: Boolean,
)

/**
 * How the launch dialog should render a [LoginProgress]: [titleRes]/[messageRes] are string
 * resources; [overrideMessage] is an already-localized runtime message that supersedes [messageRes].
 */
internal data class LaunchDialogState(
    val showSyncDialog: Boolean,
    @StringRes val titleRes: Int,
    @StringRes val messageRes: Int,
    val overrideMessage: String?,
    val percent: Int?,
)

/** Maps each [LoginProgress] phase to the [LaunchDialogState] the launch dialog should show for it. */
internal object LaunchProgressMapper {
    fun map(progress: LoginProgress): LaunchDialogState {
        val syncing = progress.phase == LoginPhase.Syncing
        val messageRes =
            when (progress.phase) {
                LoginPhase.Seating -> R.string.connect_app_launch_dialog_seating_message
                LoginPhase.SigningIn -> R.string.connect_app_launch_dialog_signing_in_message
                LoginPhase.Syncing -> R.string.connect_app_launch_dialog_syncing_message
            }
        return LaunchDialogState(
            showSyncDialog = syncing,
            titleRes = R.string.connect_app_launch_dialog_title,
            messageRes = messageRes,
            overrideMessage = progress.message,
            percent = if (syncing) progress.percent else null,
        )
    }
}

/**
 * Drives a Connect app launch from a fragment without showing LoginActivity: shows a
 * [CustomProgressDialog], runs [ConnectAppLauncher], and routes the [LaunchOutcome] through
 * [LaunchOutcomeRouter].
 *
 * The dialog is dismissed automatically when the fragment's view is destroyed, so callers don't
 * have to manage cleanup themselves.
 */
class ConnectAppLaunchController
    @JvmOverloads
    constructor(
        private val fragment: Fragment,
        private val launcher: ConnectAppLauncher = ConnectAppLauncher(),
    ) {
        private var launchDialog: CustomProgressDialog? = null
        private var showingSyncDialog = false
        private var observedLifecycle: Lifecycle? = null

        // Registered at construction (fragment init, the only valid time) so the launched app's Home
        // result is delivered back here.
        private val homeResultLauncher: ActivityResultLauncher<Intent> =
            fragment.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                onHomeResult(result)
            }

        fun launchApp(
            appId: String,
            isLearning: Boolean,
        ) = launch(LaunchTarget(appId, isLearning))

        private fun launch(target: LaunchTarget) {
            val activity = fragment.requireActivity()
            val owner = fragment.viewLifecycleOwner
            registerDialogCleanup(owner)
            showOrUpdateDialog(LaunchProgressMapper.map(LoginProgress(LoginPhase.Seating)))
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
            showOrUpdateDialog(LaunchProgressMapper.map(progress))
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

                    override fun launchHome() =
                        homeResultLauncher.launch(
                            HomeScreenBaseActivity
                                .buildHomeLaunchIntent(activity)
                                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP),
                        )

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

        private fun showOrUpdateDialog(state: LaunchDialogState) {
            val title = fragment.getString(state.titleRes)
            val message = state.overrideMessage ?: fragment.getString(state.messageRes)

            if (launchDialog == null || state.showSyncDialog != showingSyncDialog) {
                // Showing the dialog after the host fragment has saved its state (e.g. backgrounded
                // or being recreated) would throw an IllegalStateException, so skip it.
                if (fragment.childFragmentManager.isStateSaved) {
                    return
                }

                dismissLaunchDialog()
                launchDialog =
                    CustomProgressDialog
                        .newInstance(title, message, LAUNCH_DIALOG_TASK_ID)
                        .apply { if (state.showSyncDialog) addProgressBar() }
                showingSyncDialog = state.showSyncDialog
                launchDialog?.showNow(fragment.childFragmentManager, LAUNCH_DIALOG_TAG)
            } else {
                launchDialog?.updateTitle(title)
                launchDialog?.updateMessage(message)
            }

            state.percent?.let { launchDialog?.updateProgressBar(it, PROGRESS_BAR_MAX) }
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

            // Back-out (RESULT_CANCELED) is a no-op: the app session stays alive and the back stack
            // returns the worker to the launching screen.
            if (result.resultCode != Activity.RESULT_OK) {
                return
            }

            // Logout (RESULT_OK): route to the login screen via DispatchActivity.
            val activity = fragment.requireActivity()
            val intent =
                Intent(activity, DispatchActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    .putExtra(LoginActivity.USER_TRIGGERED_LOGOUT, true)
                    .putExtra(ConnectAppUtils.IS_LAUNCH_FROM_CONNECT, true)
            activity.startActivity(intent)
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
