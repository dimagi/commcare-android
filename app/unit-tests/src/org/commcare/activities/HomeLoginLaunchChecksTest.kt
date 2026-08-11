package org.commcare.activities

import android.content.Intent
import androidx.preference.PreferenceManager
import org.commcare.CommCareApplication
import org.commcare.android.util.ReflectionUtils
import org.commcare.connect.ConnectConstants.PERSONALID_MANAGED_LOGIN
import org.commcare.core.network.CommCareNetworkServiceGenerator.CURRENT_DRIFT
import org.commcare.heartbeat.UpdateToPrompt
import org.commcare.preferences.DeveloperPreferences
import org.commcare.preferences.HiddenPreferences
import org.commcare.preferences.PrefValues
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Characterization pins for the login-launch check sequence run from `onCreateSessionSafe`
 * (`doLoginLaunchChecksInOrder`): which step claims the launch, and the boolean it returns.
 *
 * That return value is the contract most likely to break silently. Step 1 (demo) halts with `false`,
 * steps 2-7 short-circuit with `true` when they claim the launch, and steps 8-9 (PIN, drift) are
 * non-claiming side effects that still return `false`. It reaches the activity as the private
 * `redirectedInOnCreate` flag, which `onResumeSessionSafe` consults to decide whether to dispatch
 * home on top of whatever the claiming step launched.
 *
 * Three of the six claiming steps are not pinned: steps 5 and 6 both call through `sendFormsOrSync`,
 * which assigns the real syncer before `setFormAndDataSyncer(fake)` can land, and step 2 needs an
 * app whose profile declares an update-info form xmlns.
 *
 * `buildHomeLaunchIntent` is pinned here too: it builds the intent whose extras decide whether these
 * checks run at all, and which mode step 8 sees.
 */
class HomeLoginLaunchChecksTest : BaseHomeScreenActivityTest() {
    /**
     * The base clears default preferences per test, but the PIN and drift steps read *app*
     * preferences. Belt-and-braces today, since each test reinstalls under a fresh application id.
     */
    @Before
    fun clearLaunchCheckAppPrefs() {
        appPrefs()
            .edit()
            .remove(DeveloperPreferences.OFFER_PIN_FOR_LOGIN)
            .remove(HiddenPreferences.HAS_DISMISSED_PIN_CREATION)
            .remove(DRIFT_WARNING_ENABLED)
            .apply()
    }

    /**
     * Build home as if arriving from LoginActivity, so onCreateSessionSafe runs
     * doLoginLaunchChecksInOrder. Non-demo session from form_nav_tests unless a demo user has been
     * seated. (PERSONALID_MANAGED_LOGIN defaults to false in the base builder.)
     */
    private fun buildHomeFromLogin(
        loginMode: LoginMode = LoginMode.PASSWORD,
        manuallySwitchedToPasswordMode: Boolean = false,
    ): StandardHomeActivity =
        buildHome {
            putExtra(DispatchActivity.START_FROM_LOGIN, true)
            putExtra(LoginActivity.LOGIN_MODE, loginMode)
            putExtra(LoginActivity.MANUAL_SWITCH_TO_PW_MODE, manuallySwitchedToPasswordMode)
        }

    // region the intent that triggers the checks

    @Suppress("DEPRECATION") // typed getSerializableExtra needs a higher minSdk than the app's
    @Test
    fun `buildHomeLaunchIntent targets standard home with managed login enabled`() {
        // The precondition for everything else here: [buildHomeFromLogin] sets START_FROM_LOGIN
        // itself, so without this row the sequence could stop running in production, suite still
        // green. form_nav_tests is a single-app profile, so useRootMenuHomeActivity() is false.
        val context = CommCareApplication.instance()
        val intent = HomeScreenBaseActivity.buildHomeLaunchIntent(context)

        assertEquals(StandardHomeActivity::class.java.name, intent.component!!.className)
        assertTrue(intent.getBooleanExtra(DispatchActivity.START_FROM_LOGIN, false))
        assertTrue(intent.getBooleanExtra(PERSONALID_MANAGED_LOGIN, false))
        assertEquals(LoginMode.PASSWORD, intent.getSerializableExtra(LoginActivity.LOGIN_MODE))
        assertEquals(false, intent.getBooleanExtra(LoginActivity.MANUAL_SWITCH_TO_PW_MODE, true))
    }

    // endregion

    // region step 1: demo user

    @Test
    fun `demo user halts before the pin step without claiming the launch`() {
        seatDemoUser()
        assertTrue(
            "seatDemoUser() must produce a demo session, else this test can't gate on step 1",
            HomeScreenBaseActivity.isDemoUser(),
        )

        val home = buildHomeFromLogin(LoginMode.PRIMED)

        // Step 1 short-circuits with `return false`, so steps 2-9 — including the PIN step that the
        // identical non-demo PRIMED login below reaches — never run, but the launch is not claimed.
        assertFalse("demo halt should skip the PIN step", launchedCreatePin(startedActivities(home)))
        assertFalse("demo halt returns false, not a claim", claimedLaunch(home))
        assertNotNull("demo mode warning should be raised", pendingAlertDialog(home))
    }

    // endregion

    // region step 3: form interrupted by session expiration, and its precedence over step 4

    @Test
    fun `interrupted form session claims the launch and beats session restoration`() {
        seedInterruptedFormSession()

        val home = buildHomeFromLogin(LoginMode.PRIMED)

        assertTrue("restoring an interrupted form claims the launch", claimedLaunch(home))
        val formEntry = onlyStartedActivity(startedActivities(home))
        assertEquals(FormEntryActivity::class.java.name, formEntry.component!!.className)
        // The interrupted-form restore is the only path that flags the relaunch as a restart after
        // session expiration. Seeding the SSD also leaves a command on the live session, so step 4
        // would equally have claimed this launch — this extra is what proves step 3 got there first.
        assertTrue(
            "step 3 must beat step 4",
            formEntry.getBooleanExtra(FormEntryActivity.KEY_IS_RESTART_AFTER_EXPIRATION, false),
        )
    }

    // endregion

    // region step 4: saved session restoration

    @Test
    fun `session with a pending command claims the launch and skips the pin step`() {
        HiddenPreferences.clearInterruptedSSD()
        CommCareApplication
            .instance()
            .currentSessionWrapper.session
            .setCommand(FORM_COMMAND)

        val home = buildHomeFromLogin(LoginMode.PRIMED)

        val started = startedActivities(home)
        assertTrue("restoring a saved session claims the launch", claimedLaunch(home))
        assertFalse("a claimed launch must not fall through to the PIN step", launchedCreatePin(started))
        val formEntry = onlyStartedActivity(started)
        assertEquals(FormEntryActivity::class.java.name, formEntry.component!!.className)
        assertFalse(
            "step 4 is a plain session restore, not a restart after expiration",
            formEntry.getBooleanExtra(FormEntryActivity.KEY_IS_RESTART_AFTER_EXPIRATION, false),
        )
    }

    // endregion

    // region step 7: update prompt

    @Test
    fun `a forced update prompt claims the launch and skips the pin step`() {
        seedForcedCczUpdatePrompt()

        val home = buildHomeFromLogin(LoginMode.PRIMED)

        assertTrue("prompting for an update claims the launch", claimedLaunch(home))
        // UpdatePromptHelper uses startActivity, not startActivityForResult, so this prompt does not
        // land in the startedActivities() queue the other steps are asserted against.
        assertEquals(
            PromptCczUpdateActivity::class.java.name,
            shadowOf(home).nextStartedActivity.component!!.className,
        )
        // The identical PRIMED login in the step-8 row below launches CreatePinActivity; that this
        // one does not is what proves step 7 short-circuited ahead of it.
        assertFalse(
            "a claimed launch must not fall through to the PIN step",
            launchedCreatePin(startedActivities(home)),
        )
    }

    // endregion

    // region step 8: PIN launch conditions

    @Suppress("DEPRECATION") // typed getSerializableExtra needs a higher minSdk than the app's
    @Test
    fun `primed login reaches the pin step and launches create pin without claiming`() {
        // A fresh login with nothing pending falls through steps 2-7 to step 8, which in PRIMED
        // mode launches CreatePinActivity — proof that control reached step 8 unclaimed.
        val home = buildHomeFromLogin(LoginMode.PRIMED)

        val started = onlyStartedActivity(startedActivities(home))
        assertEquals(CreatePinActivity::class.java.name, started.component!!.className)
        assertEquals(LoginMode.PRIMED, started.getSerializableExtra(LoginActivity.LOGIN_MODE))
        assertFalse("the PIN step is a side effect, not a claim", claimedLaunch(home))
    }

    @Test
    fun `password login offers the pin dialog when enabled and not yet dismissed`() {
        offerPinForLogin()

        val home = buildHomeFromLogin(LoginMode.PASSWORD)

        assertNotNull("password mode should offer the PIN choice dialog", pendingAlertDialog(home))
        assertFalse(
            "password mode prompts rather than launching create-pin directly",
            launchedCreatePin(startedActivities(home)),
        )
        assertFalse("the PIN step is a side effect, not a claim", claimedLaunch(home))
    }

    @Test
    fun `password login skips the pin dialog once creation has been dismissed`() {
        offerPinForLogin()
        dismissPinCreation()

        val home = buildHomeFromLogin(LoginMode.PASSWORD)

        assertNull("a dismissed PIN prompt must stay dismissed", pendingAlertDialog(home))
    }

    @Test
    fun `manually switching to password mode re-offers the dismissed pin dialog`() {
        offerPinForLogin()
        dismissPinCreation()

        val home = buildHomeFromLogin(LoginMode.PASSWORD, manuallySwitchedToPasswordMode = true)

        assertNotNull(
            "a manual switch to password mode overrides the earlier dismissal",
            pendingAlertDialog(home),
        )
    }

    @Test
    fun `password login offers no pin dialog when the pin feature is off`() {
        // OFFER_PIN_FOR_LOGIN defaults to off, so this is also the plain-login baseline: nothing
        // claims the launch and no dialog is raised.
        val home = buildHomeFromLogin(LoginMode.PASSWORD)

        assertFalse(home.isFinishing)
        assertNull(pendingAlertDialog(home))
        assertFalse("a plain password login is claimed by no step", claimedLaunch(home))
    }

    // endregion

    // region step 9: clock drift

    @Test
    fun `drift warning shows and stamps the last warning time`() {
        enableDriftWarning()
        setCurrentDrift(5 * 60 * 1000L)

        val home = buildHomeFromLogin(LoginMode.PASSWORD)

        assertNotNull("nonzero drift should raise the drift dialog", pendingAlertDialog(home))
        assertTrue("showing the drift dialog must stamp the warning time", driftWarningStamped())
        assertFalse("the drift step is a side effect, not a claim", claimedLaunch(home))
    }

    @Test
    fun `no drift warning when the clock is not drifting`() {
        enableDriftWarning()
        setCurrentDrift(0L)

        val home = buildHomeFromLogin(LoginMode.PASSWORD)

        assertNull("zero drift should raise no dialog", pendingAlertDialog(home))
        assertFalse("zero drift should not stamp the warning time", driftWarningStamped())
    }

    // endregion

    // region the one-time login flags cleared in the `finally` block

    @Test
    fun `one time login flags are cleared even when an earlier step claims the launch`() {
        // Step 3 claims this launch, so steps 5 and 6 never read their flags. Only the `finally`
        // block can clear them; dropping it would re-fire the sync steps on the next home create.
        seedInterruptedFormSession()
        HiddenPreferences.setPostUpdateSyncNeeded(true)
        seedPendingSyncRequest()

        val home = buildHomeFromLogin()

        assertTrue("step 3 should have claimed this launch", claimedLaunch(home))
        assertEquals(
            "interrupted SSD flag not cleared",
            -1,
            HiddenPreferences.getIdOfInterruptedSSD(),
        )
        assertFalse(
            "post-update-sync flag not cleared",
            CommCareApplication.instance().isPostUpdateSyncNeeded,
        )
        assertFalse(
            "pending sync request not cleared",
            HiddenPreferences.isPendingSyncRequest(loggedInUsername()),
        )
    }

    // endregion

    // region assertions on the launch checks

    /**
     * The boolean `doLoginLaunchChecksInOrder` returned, as stored on the activity. Private with no
     * accessor; the walk up from [StandardHomeActivity] finds this field rather than the same-named
     * one further up on `SessionAwareCommCareActivity`.
     */
    private fun claimedLaunch(home: StandardHomeActivity): Boolean = ReflectionUtils.readField(home, "redirectedInOnCreate") as Boolean

    /**
     * The dialog a launch check raised during `onCreate`, or null if none was raised. Asking the
     * activity to show what it stashed is what `onResume` does, so the dialog becomes a real
     * fragment to assert on. Drains the pending dialog, so call it once per test.
     */
    private fun pendingAlertDialog(home: StandardHomeActivity): Any? {
        home.showPendingAlertDialog()
        // DialogFragment.show() commits asynchronously; the fragment isn't findable by tag until
        // the transaction runs.
        home.supportFragmentManager.executePendingTransactions()
        return home.currentAlertDialog
    }

    /**
     * Every activity home started for result during create, oldest first. Reading drains the
     * shadow's queue, so each test takes this list once and asserts against the list.
     */
    private fun startedActivities(home: StandardHomeActivity): List<Intent> {
        val shadow = shadowOf(home)
        val started = mutableListOf<Intent>()
        while (true) {
            started += (shadow.nextStartedActivityForResult ?: break).intent
        }
        return started
    }

    /** Asserts exactly one activity was started, and returns its intent. */
    private fun onlyStartedActivity(started: List<Intent>): Intent {
        assertEquals(
            "expected exactly one started activity, got ${started.map { it.component?.className }}",
            1,
            started.size,
        )
        return started.single()
    }

    private fun launchedCreatePin(started: List<Intent>): Boolean =
        started.any { it.component?.className == CreatePinActivity::class.java.name }

    // endregion

    // region fixtures

    /**
     * Leave behind the state a form interrupted by session expiration leaves: a session descriptor
     * (plus its stub form record) in user storage, pointed at by the interrupted-SSD preference.
     */
    private fun seedInterruptedFormSession() {
        val wrapper = CommCareApplication.instance().currentSessionWrapper
        wrapper.session.setCommand(FORM_COMMAND)
        wrapper.commitStub()
        HiddenPreferences.setInterruptedSSD(wrapper.sessionDescriptorId)
    }

    /**
     * Register a forced .ccz update prompt for a version far ahead of the installed app, which is
     * what step 7's `UpdatePromptHelper.promptForUpdateIfNeeded` looks for.
     *
     * The CCZ type is used rather than APK because it compares against the seated app's profile
     * version rather than the package's `versionName`. `isForced = true` makes `shouldShowPrompt`
     * fire regardless of show-frequency or whether the prompt was already shown this login, so the
     * row doesn't depend on `UpdatePromptShowHistory` bookkeeping.
     *
     * No cleanup needed: `registerWithSystem()` writes to *app* preferences, and every test installs
     * a fresh app whose preferences file is named for its new application id.
     */
    private fun seedForcedCczUpdatePrompt() {
        UpdateToPrompt(
            FAR_FUTURE_CCZ_VERSION,
            "true",
            UpdateToPrompt.Type.CCZ_UPDATE,
        ).registerWithSystem()
    }

    private fun dismissPinCreation() {
        appPrefs()
            .edit()
            .putBoolean(HiddenPreferences.HAS_DISMISSED_PIN_CREATION, true)
            .apply()
    }

    private fun enableDriftWarning() {
        appPrefs()
            .edit()
            .putString(DRIFT_WARNING_ENABLED, PrefValues.YES)
            .apply()
    }

    private fun setCurrentDrift(driftMillis: Long) {
        defaultPrefs().edit().putLong(CURRENT_DRIFT, driftMillis).apply()
    }

    private fun driftWarningStamped(): Boolean = defaultPrefs().getLong(LAST_DRIFT_WARNING_AT, -1) > 0

    /**
     * Mark a background sync as pending for the logged-in user. Written through the same key
     * `HiddenPreferences.isPendingSyncRequest` reads — that reader only checks for the key's
     * presence, so the value need not be a serialized `FCMMessageData`. The fixture asserts the
     * seeding took, so a change to the key format fails here rather than silently passing the test
     * that depends on it.
     */
    private fun seedPendingSyncRequest() {
        val username = loggedInUsername()
        val key =
            HiddenPreferences.BACKGROUND_SYNC_PENDING + username + "@" +
                HiddenPreferences.getUserDomainWithoutServerUrl()
        defaultPrefs().edit().putString(key, "pending").apply()
        assertTrue(
            "fixture failed to mark a sync as pending",
            HiddenPreferences.isPendingSyncRequest(username),
        )
    }

    private fun loggedInUsername(): String =
        CommCareApplication
            .instance()
            .session.loggedInUser.username

    private fun appPrefs() = CommCareApplication.instance().currentApp.appPreferences

    private fun defaultPrefs() = PreferenceManager.getDefaultSharedPreferences(CommCareApplication.instance())

    // endregion

    companion object {
        /** The only form entry in the form_nav_tests app; takes no datums. */
        private const val FORM_COMMAND = "m0-f0"

        /** Comfortably ahead of the test app's profile version, so the prompt stays relevant. */
        private const val FAR_FUTURE_CCZ_VERSION = "999999"

        /** Mirrors the private preference keys `DriftHelper` reads. */
        private const val DRIFT_WARNING_ENABLED = "incorrect_time_warning_enabled"
        private const val LAST_DRIFT_WARNING_AT = "last_incorrect_time_warning_at"
    }
}
