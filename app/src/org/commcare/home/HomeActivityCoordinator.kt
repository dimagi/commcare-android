package org.commcare.home

import android.content.Intent
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.preference.PreferenceManager
import org.commcare.CommCareApplication
import org.commcare.android.logging.ReportingUtils
import org.commcare.models.AndroidSessionWrapper
import org.commcare.preferences.HiddenPreferences
import org.commcare.utils.SessionUnavailableException

/**
 * Composition root for the behavior shared by every CommCare home screen.
 *
 * - Registers itself on the host lifecycle during init — before `ON_CREATE` is dispatched — so it
 * observes the host directly instead of being driven by forwarded calls.
 * - Routes each activity result to the delegate that opened the screen, and decides what home
 * shows when the host resumes.
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
 * wherever it likes, and the results of the Settings and Advanced Actions screens.
 * @property appHealth the app-health checks and the syncs home owes while the user is on it.
 * @property launchState how the activity was launched, kept through recreation.
 * @property launchChecks the checks that run once after login.
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
    val appHealth: AppHealthDelegate = AppHealthDelegate(host = host, sync = sync, appUpdate = appUpdate),
) : DefaultLifecycleObserver {
    constructor(host: HomeActivityHost) : this(host, ServiceBackedSession())

    private var navigationProceedingAfterResult = false

    val launchState = HomeLaunchState(host)

    val sessionNavigation =
        SessionNavigationDelegate(host = host, sessionLaunch = sessionLaunch) { state, shouldWarnUser ->
            clearSessionAndExit(state, shouldWarnUser)
        }

    val launchChecks =
        LaunchChecksDelegate(
            host = host,
            sessionLaunch = sessionLaunch,
            sessionNavigation = sessionNavigation,
            sync = sync,
            appUpdate = appUpdate,
            pin = pin,
        )

    /**
     * A launch step or launch sync took over in `onCreate`, so the next resume leaves the screen it
     * started alone.
     */
    var redirectedInOnCreate = false

    init {
        host.lifecycle.addObserver(this)
        host.lifecycle.addObserver(appUpdate)
    }

    override fun onCreate(owner: LifecycleOwner) {
        launchState.ensureRestored()
        recordCommCareVersionIfSessionLive()
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
        if (actions.onActivityResult(requestCode, resultCode, intent)) {
            return
        }
        val continueSessionNav =
            when (requestCode) {
                HomeRequestCodes.GET_INCOMPLETE_FORM -> {
                    sessionLaunch.handleIncompleteFormResult(resultCode, intent)
                }

                HomeRequestCodes.MODEL_RESULT -> {
                    handleFormEntryResult(resultCode, intent) && !appHealth.runChecksIfNotYetRun()
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
            appHealth.showHomeOrPendingAction()
        }
        if (!sessionLaunch.formEntryStartedSinceResume) {
            appHealth.runPendingSyncRequest()
        }
        redirectedInOnCreate = false
        navigationProceedingAfterResult = false
        sessionLaunch.formEntryStartedSinceResume = false
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
        val outcome = sessionLaunch.handleFormEntryResult(resultCode, intent, launchState.wasExternal)
        if (outcome.formCompleted) {
            sync.startUnsentFormsTask(syncAfterwards = false, userTriggered = false)
        }
        return outcome.continueSessionNav
    }

    /** Reset the session, finishing the host if an external launch asked to exit. */
    fun clearSessionAndExit(
        currentState: AndroidSessionWrapper,
        shouldWarnUser: Boolean,
    ) = sessionLaunch.clearSessionAndExit(currentState, shouldWarnUser, launchState.wasExternal)

    /** Home opened with a live session, so this CommCare version has run successfully. */
    private fun recordCommCareVersionIfSessionLive() {
        try {
            CommCareApplication.instance().session
        } catch (e: SessionUnavailableException) {
            return
        }
        PreferenceManager
            .getDefaultSharedPreferences(CommCareApplication.instance())
            .edit()
            .putString(HiddenPreferences.LAST_SUCCESSFUL_CC_VERSION, ReportingUtils.getCommCareVersionString())
            .apply()
    }
}
