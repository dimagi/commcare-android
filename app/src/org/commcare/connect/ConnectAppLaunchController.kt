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
import org.commcare.activities.LoginActivity
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

/**
 * How the launch dialog should render a [LoginProgress]: [titleKey]/[messageKey] are localization
 * keys; [overrideMessage] is an already-localized runtime message that supersedes [messageKey].
 */
internal data class LaunchDialogState(
    val showSyncDialog: Boolean,
    val titleKey: String,
    val messageKey: String,
    val overrideMessage: String?,
    val percent: Int?,
)

/** Maps each [LoginProgress] phase to the [LaunchDialogState] the launch dialog should show for it. */
internal object LaunchProgressMapper {
    fun map(progress: LoginProgress): LaunchDialogState {
        val syncing = progress.phase == LoginPhase.Syncing
        val (titleKey, messageKey) =
            when (progress.phase) {
                LoginPhase.Seating -> "seating.app" to "seating.app"
                LoginPhase.SigningIn -> "key.manage.title" to "key.manage.start"
                LoginPhase.Syncing -> "sync.communicating.title" to "sync.progress.starting"
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

        // Registered at construction (fragment init, the only valid time) so a back-out of the launched
        // app's Home is delivered back here and can return the worker to the opportunities list.
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

                    override fun launchHome() = homeResultLauncher.launch(HomeScreenBaseActivity.buildHomeLaunchIntent(activity))

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
            val title = Localization.get(state.titleKey)
            val message = state.overrideMessage ?: Localization.get(state.messageKey)

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

            val activity = fragment.requireActivity()
            if (result.resultCode == Activity.RESULT_OK) {
                // Route to the login screen via DispatchActivity on the user logging-out.
                val intent =
                    Intent(activity, DispatchActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        .putExtra(LoginActivity.USER_TRIGGERED_LOGOUT, true)
                        .putExtra(ConnectAppUtils.IS_LAUNCH_FROM_CONNECT, true)
                activity.startActivity(intent)
                return
            }

            // Back-out (RESULT_CANCELED): end the app session, return to the opportunities list.
            CommCareApplication.instance().closeUserSession()
            ConnectNavHelper.goToConnectJobsList(activity, clearTop = true)
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
