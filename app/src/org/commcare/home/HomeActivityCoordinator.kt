package org.commcare.home

import android.content.Intent
import android.os.Bundle
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import org.commcare.activities.DriftHelper
import org.commcare.models.AndroidSessionWrapper
import org.commcare.views.dialogs.StandardAlertDialog
import org.javarosa.core.services.locale.Localization

/**
 * Composition root for the behavior shared by every CommCare home screen.
 *
 * - Registers itself on the host lifecycle during init — before `ON_CREATE` is dispatched — so it
 * observes the host directly instead of being driven by forwarded calls.
 * - Owns the launch/nav instance state that describes *how the activity was launched*, persisting
 * it through the host's `SavedStateRegistry`.
 * - Runs the post-login launch checks in order, and routes activity results to the delegates.
 *
 * @property appUpdate owns the binary and content app updates. Session-independent, registered on the
 * host lifecycle.
 * @property sync owns form sending and data pulls; the host's task receiver.
 * @property sessionLaunch starts form entry and handles its result, and owns the form and session
 * restoration launch steps.
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
     * Fan an activity result out to the delegates that need it.
     *
     * @return true when the result was consumed and the host should not handle it further
     */
    fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        intent: Intent?,
    ): Boolean =
        appUpdate.onActivityResult(requestCode, resultCode) ||
            pin.onActivityResult(requestCode, resultCode, intent)

    /**
     * Run the post-login launch checks. The order is deliberate: demo mode skips everything else,
     * form and session restoration come before any sync that could invalidate them, a post-update
     * sync includes the plain form send, and the PIN and drift checks run only once nothing else
     * has taken over.
     *
     * @return true when a step claimed the launch by starting another screen or a blocking task
     */
    fun runLaunchChecks(): Boolean {
        if (host.isDemoUser()) {
            showDemoModeWarning()
            return false
        }
        if (sessionLaunch.showUpdateInfoForm()) {
            return true
        }
        if (sessionLaunch.tryRestoringFormFromSessionExpiration()) {
            return true
        }
        if (sessionLaunch.tryRestoringSession()) {
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
