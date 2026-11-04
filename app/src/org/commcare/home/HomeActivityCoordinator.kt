package org.commcare.home

import android.app.Activity.RESULT_OK
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import org.commcare.CommCareApplication
import org.commcare.activities.DispatchActivity
import org.commcare.activities.DriftHelper
import org.commcare.activities.UpdateActivity
import org.commcare.connect.database.ConnectTaskUtils
import org.commcare.models.AndroidSessionWrapper
import org.commcare.preferences.AdvancedActionsPreferences
import org.commcare.preferences.DeveloperPreferences
import org.commcare.preferences.HiddenPreferences
import org.commcare.recovery.measures.RecoveryMeasuresHelper
import org.commcare.sync.FirebaseMessagingDataSyncer
import org.commcare.tasks.DataPullTask.PullTaskResult
import org.commcare.util.LogTypes
import org.commcare.utils.CommCareUtil
import org.commcare.utils.SessionUnavailableException
import org.commcare.views.dialogs.StandardAlertDialog
import org.javarosa.core.services.Logger
import org.javarosa.core.services.locale.Localization
import java.io.File
import java.text.SimpleDateFormat

/**
 * Composition root for the behavior shared by every CommCare home screen.
 *
 * - Registers itself on the host lifecycle during init — before `ON_CREATE` is dispatched — so it
 * observes the host directly instead of being driven by forwarded calls.
 * - Owns the launch/nav instance state that describes *how the activity was launched*, persisting
 * it through the host's `SavedStateRegistry`.
 * - Runs the post-login launch checks in order, the resume and app-health checks, and routes
 * activity results to the delegates.
 *
 * @property appUpdate owns the binary and content app updates. Session-independent, registered on the
 * host lifecycle.
 * @property sync owns form sending and data pulls; the host's task receiver.
 * @property sessionLaunch starts form entry and handles its result, and owns the form and session
 * restoration launch steps.
 * @property sessionNavigation walks the session and handles the menu, case and remote-request results.
 * @property sessionExpiration reports session loss to the host. Driven by the session-aware activity
 * chain, not the lifecycle.
 * @property pin owns the PIN creation offer, the set-PIN action and the PIN screens' results.
 * @property actions the home actions, each paired with an availability query, for the host to surface
 * wherever it likes.
 */
class HomeActivityCoordinator(
    private val host: HomeActivityHost,
    session: SeatedAppSession,
    val appUpdate: AppUpdateDelegate = AppUpdateDelegate(host = host, session = session),
    val sync: SyncDelegate = SyncDelegate(host = host),
    val sessionLaunch: SessionLaunchDelegate = SessionLaunchDelegate(host = host),
    val sessionExpiration: SessionExpirationDelegate = SessionExpirationDelegate(host = host),
    val pin: PinDelegate = PinDelegate(host = host, session = session),
    val actions: HomeActionsDelegate = HomeActionsDelegate(host = host, sync = sync, appUpdate = appUpdate, pin = pin),
) : DefaultLifecycleObserver {
    constructor(host: HomeActivityHost) : this(host, ServiceBackedSession())

    private var restored = false

    private var externalLaunch = false
    private var loginExtraConsumed = false
    private var endpointNavPendingAfterSync = false

    private var navigationProceedingAfterResult = false

    private val dataSyncer by lazy { FirebaseMessagingDataSyncer(host.hostContext) }

    val sessionNavigation =
        SessionNavigationDelegate(host = host, sessionLaunch = sessionLaunch) { state, shouldWarnUser ->
            clearSessionAndExit(state, shouldWarnUser)
        }

    /**
     * A launch step or launch sync took over in `onCreate`, so the next resume leaves the screen it
     * started alone.
     */
    var redirectedInOnCreate = false

    /** Activity was launched by an external app, so form submission may redispatch back to it. */
    var wasExternal: Boolean
        get() {
            ensureRestored()
            return externalLaunch
        }
        set(value) {
            ensureRestored()
            externalLaunch = value
        }

    /** The one-shot `START_FROM_LOGIN` extra has already driven the post-login launch checks. */
    var loginExtraWasConsumed: Boolean
        get() {
            ensureRestored()
            return loginExtraConsumed
        }
        set(value) {
            ensureRestored()
            loginExtraConsumed = value
        }

    /** A session-endpoint launch needs a blocking sync before navigating to the endpoint. */
    var pendingEndpointNavigationAfterSync: Boolean
        get() {
            ensureRestored()
            return endpointNavPendingAfterSync
        }
        set(value) {
            ensureRestored()
            endpointNavPendingAfterSync = value
        }

    init {
        host.lifecycle.addObserver(this)
        host.lifecycle.addObserver(appUpdate)
    }

    override fun onCreate(owner: LifecycleOwner) {
        ensureRestored()
    }

    /**
     * Route an activity result to the delegate that launched the screen, then continue session
     * navigation unless that result ended it.
     */
    fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        intent: Intent?,
    ) {
        if (appUpdate.onActivityResult(requestCode, resultCode) || pin.onActivityResult(requestCode, resultCode, intent)) {
            return
        }
        if (resultCode == HomeResultCodes.RESULT_RESTART) {
            sessionNavigation.restartAfterCallout(intent)
            return
        }
        val continueSessionNav =
            when (requestCode) {
                HomeRequestCodes.PREFERENCES_ACTIVITY -> {
                    handlePreferencesResult(resultCode)
                    false
                }

                HomeRequestCodes.ADVANCED_ACTIONS_ACTIVITY -> {
                    handleAdvancedActionsResult(resultCode, intent)
                    false
                }

                HomeRequestCodes.GET_INCOMPLETE_FORM -> {
                    sessionLaunch.handleIncompleteFormResult(resultCode, intent)
                }

                HomeRequestCodes.MODEL_RESULT -> {
                    handleFormEntryResult(resultCode, intent) && !runAppHealthChecksIfNotYetRun()
                }

                else -> {
                    sessionNavigation.handleCalloutResult(requestCode, resultCode, intent)
                }
            }
        if (continueSessionNav) {
            navigationProceedingAfterResult = true
            sessionNavigation.startNextSessionStepSafe()
        }
    }

    /**
     * The host resumed with a live session. Unless a launch step or a returning result already
     * moved the user on, show home, or whatever app-health action is pending, then run any sync
     * that waited for form entry to finish.
     */
    fun onHostResumed() {
        if (!redirectedInOnCreate && !navigationProceedingAfterResult) {
            host.refreshActionBar()
            showHomeOrPendingAppHealthAction()
        }
        val username = loggedInUsername()
        if (HiddenPreferences.isPendingSyncRequest(username) && !sessionLaunch.formEntryStartedSinceResume) {
            dataSyncer.syncData(HiddenPreferences.getPendingSyncRequest(username))
        }
        redirectedInOnCreate = false
        navigationProceedingAfterResult = false
        sessionLaunch.formEntryStartedSinceResume = false
    }

    /** A pull finished; resume an app update that was waiting on it. */
    fun onPullTaskFinished(result: PullTaskResult) {
        if (!UpdateActivity.sBlockedUpdateWorkflowInProgress) {
            return
        }
        val intent = Intent(host.hostContext.applicationContext, UpdateActivity::class.java)
        intent.putExtra(UpdateActivity.KEY_PROCEED_AUTOMATICALLY, true)
        intent.putExtra(UpdateActivity.KEY_PRE_UPDATE_SYNC_SUCCEED, result == PullTaskResult.DOWNLOAD_SUCCESS)
        host.startActivity(intent)
    }

    /** Send forms, then sync. A sync the app kicked off on its own is logged with the last attempt. */
    fun triggerSync(autoSyncPending: Boolean) {
        if (autoSyncPending) {
            val lastUploadSyncAttempt = HiddenPreferences.getLastUploadSyncAttempt()
            val footer =
                if (lastUploadSyncAttempt == 0L) "never" else SimpleDateFormat.getDateTimeInstance().format(lastUploadSyncAttempt)
            Logger.log(LogTypes.TYPE_USER, "autosync triggered. Last Sync|$footer")
        }
        if (host.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            host.refreshHostUi()
        }
        sync.sendFormsOrSync(false)
    }

    /**
     * Run the post-login launch checks. The order is deliberate: demo mode skips everything else,
     * form and session restoration come before any sync that could invalidate them, a post-update
     * sync includes the plain form send, and the PIN and drift checks run only once nothing else
     * has taken over.
     *
     * @return true when a step claimed the launch by starting another screen or a blocking task
     */
    fun runLaunchChecks(): Boolean =
        try {
            runLaunchChecksInOrder()
        } finally {
            clearOneTimeLoginActionFlags()
        }

    private fun runLaunchChecksInOrder(): Boolean {
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
     * Handle the user's return from form entry, and send the form it completed.
     *
     * @return whether the host should continue session navigation
     */
    fun handleFormEntryResult(
        resultCode: Int,
        intent: Intent?,
    ): Boolean {
        val outcome = sessionLaunch.handleFormEntryResult(resultCode, intent, wasExternal)
        if (outcome.formCompleted) {
            sync.startUnsentFormsTask(syncAfterwards = false, userTriggered = false)
        }
        return outcome.continueSessionNav
    }

    /** Reset the session, finishing the host if an external launch asked to exit. */
    fun clearSessionAndExit(
        currentState: AndroidSessionWrapper,
        shouldWarnUser: Boolean,
    ) = sessionLaunch.clearSessionAndExit(currentState, shouldWarnUser, wasExternal)

    /**
     * Whatever the launch checks did, the actions behind these flags happen on the first launch
     * after login or not at all.
     */
    private fun clearOneTimeLoginActionFlags() {
        HiddenPreferences.setPostUpdateSyncNeeded(false)
        HiddenPreferences.clearInterruptedSSD()
        HiddenPreferences.clearPendingSyncRequest(loggedInUsername())
    }

    private fun loggedInUsername(): String =
        CommCareApplication
            .instance()
            .session.loggedInUser.username

    private fun showHomeOrPendingAppHealthAction() {
        try {
            CommCareApplication.instance().session
        } catch (e: SessionUnavailableException) {
            host.finishWithResult(RESULT_OK)
            return
        }
        if (!runAppHealthChecks()) {
            host.refreshHostUi()
        }
    }

    private fun runAppHealthChecksIfNotYetRun(): Boolean =
        !CommCareApplication.instance().session.appHealthChecksCompleted() && runAppHealthChecks()

    /**
     * Run the first pending app-health action: recovery measures, a sync a blocked update waits
     * on, a pending sync, then an update prompt. Forced log submission runs regardless.
     *
     * @return true when an action took over the foreground
     */
    private fun runAppHealthChecks(): Boolean {
        val kickedOff =
            when {
                RecoveryMeasuresHelper.recoveryMeasuresPending() -> {
                    host.finishWithResult(
                        RESULT_OK,
                        Intent().putExtra(DispatchActivity.EXECUTE_RECOVERY_MEASURES, true),
                    )
                    true
                }

                UpdateActivity.isUpdateBlockedOnSync() && UpdateActivity.sBlockedUpdateWorkflowInProgress -> {
                    triggerSync(true)
                    true
                }

                CommCareApplication.instance().isSyncPending ||
                    ConnectTaskUtils.isLastTaskUpdateLaterThanLastSync(host.hostContext) -> {
                    triggerSync(true)
                    true
                }

                else -> {
                    appUpdate.promptForUpdateIfNeeded(true)
                }
            }
        val session = CommCareApplication.instance().session
        if (HiddenPreferences.shouldForceLogs(session.loggedInUser.uniqueId)) {
            CommCareUtil.triggerLogSubmission(CommCareApplication.instance(), true)
        }
        session.setAppHealthChecksCompleted()
        return kickedOff
    }

    private fun handlePreferencesResult(resultCode: Int) {
        if (resultCode == HomeResultCodes.RESULT_DATA_RESET) {
            host.finish()
        } else if (resultCode == DeveloperPreferences.RESULT_SYNC_CUSTOM) {
            performCustomRestore()
        }
    }

    private fun performCustomRestore() {
        val failureMessageKey =
            try {
                val filePath = DeveloperPreferences.getCustomRestoreDocLocation()
                when {
                    filePath.isNullOrEmpty() -> {
                        "custom.restore.file.not.set"
                    }

                    !File(filePath).exists() -> {
                        "custom.restore.file.not.exist"
                    }

                    else -> {
                        sync.performCustomRestoreFromFile(File(filePath))
                        null
                    }
                }
            } catch (e: Exception) {
                "custom.restore.error"
            }
        failureMessageKey?.let { Toast.makeText(host.hostContext, Localization.get(it), Toast.LENGTH_LONG).show() }
    }

    private fun handleAdvancedActionsResult(
        resultCode: Int,
        intent: Intent?,
    ) {
        if (resultCode != HomeResultCodes.RESULT_FORMS_PROCESSED) {
            return
        }
        val formProcessCount = intent!!.getIntExtra(AdvancedActionsPreferences.FORM_PROCESS_COUNT_KEY, 0)
        val localizationKey = intent.getStringExtra(AdvancedActionsPreferences.FORM_PROCESS_MESSAGE_KEY)
        host.displayToast(Localization.get(localizationKey, arrayOf(formProcessCount.toString())))
        host.refreshHostUi()
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

    /** Launch step: warn while the device clock drifts from the server's, at most once a day. */
    private fun checkForDrift() {
        if (DriftHelper.shouldShowDriftWarning() && DriftHelper.getCurrentDrift() != 0L) {
            host.showAlertDialog(DriftHelper.getDriftDialog(host.hostContext))
            DriftHelper.updateLastDriftWarningTime()
        }
    }

    /**
     * Consume any saved launch/nav state and register the provider that writes it back. Idempotent.
     *
     * Called lazily from the property accessors rather than only from [onCreate] because the
     * session-gated hosts read this state inside `onCreateSessionSafe`, which runs during
     * `Activity.onCreate` — before `ON_CREATE` is dispatched.
     */
    private fun ensureRestored() {
        if (restored) {
            return
        }
        restored = true
        val registry = host.savedStateRegistry
        registry.consumeRestoredStateForKey(PROVIDER_KEY)?.let { state ->
            externalLaunch = state.getBoolean(KEY_WAS_EXTERNAL)
            loginExtraConsumed = state.getBoolean(KEY_LOGIN_EXTRA_CONSUMED)
            endpointNavPendingAfterSync = state.getBoolean(KEY_PENDING_ENDPOINT_NAV_AFTER_SYNC)
        }
        registry.registerSavedStateProvider(PROVIDER_KEY) {
            Bundle().apply {
                putBoolean(KEY_WAS_EXTERNAL, externalLaunch)
                putBoolean(KEY_LOGIN_EXTRA_CONSUMED, loginExtraConsumed)
                putBoolean(KEY_PENDING_ENDPOINT_NAV_AFTER_SYNC, endpointNavPendingAfterSync)
            }
        }
    }

    companion object {
        private const val PROVIDER_KEY = "org.commcare.home.HomeActivityCoordinator"
        private const val KEY_WAS_EXTERNAL = "was_external"
        private const val KEY_LOGIN_EXTRA_CONSUMED = "login_extra_was_consumed"
        private const val KEY_PENDING_ENDPOINT_NAV_AFTER_SYNC = "pending_endpoint_nav_after_sync"
    }
}
