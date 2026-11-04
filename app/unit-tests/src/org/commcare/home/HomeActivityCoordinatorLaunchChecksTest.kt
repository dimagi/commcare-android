package org.commcare.home

import android.app.Activity.RESULT_CANCELED
import android.app.Activity.RESULT_OK
import android.content.Intent
import androidx.preference.PreferenceManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.commcare.CommCareApplication
import org.commcare.CommCareTestApplication
import org.commcare.activities.BaseHomeScreenActivityTest.Companion.TEST_APP_PATH
import org.commcare.activities.BaseHomeScreenActivityTest.Companion.TEST_PASSWORD
import org.commcare.activities.BaseHomeScreenActivityTest.Companion.TEST_USER
import org.commcare.activities.CommCarePreferenceActivity
import org.commcare.activities.CreatePinActivity
import org.commcare.activities.FormRecordListActivity
import org.commcare.activities.LoginActivity
import org.commcare.activities.LoginMode
import org.commcare.activities.PinAuthenticationActivity
import org.commcare.activities.SessionAwarePreferenceActivity
import org.commcare.activities.UpdateActivity
import org.commcare.android.database.user.models.FormRecord
import org.commcare.android.mocks.FormAndDataSyncerFake
import org.commcare.android.util.TestAppInstaller
import org.commcare.appupdate.AppUpdateState
import org.commcare.preferences.DeveloperPreferences
import org.commcare.preferences.HiddenPreferences
import org.commcare.preferences.PrefValues
import org.commcare.views.dialogs.StandardAlertDialog
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The coordinator's launch-check order and early returns, plus the action facade's availability
 * queries and routing, over real delegates with only the syncer and Play Core update faked.
 */
@Config(application = CommCareTestApplication::class)
@RunWith(AndroidJUnit4::class)
class HomeActivityCoordinatorLaunchChecksTest {
    private lateinit var host: FakeHomeActivityHost
    private lateinit var updateController: FakeAppUpdateController
    private lateinit var syncer: FormAndDataSyncerFake
    private lateinit var coordinator: HomeActivityCoordinator

    @Before
    fun setUp() {
        TestAppInstaller.installAppAndLogin(TEST_APP_PATH, TEST_USER, TEST_PASSWORD)
        HiddenPreferences.clearInterruptedSSD()
        host = FakeHomeActivityHost()
        updateController = FakeAppUpdateController()
        syncer = FormAndDataSyncerFake()
        val session = FakeSeatedAppSession()
        coordinator =
            HomeActivityCoordinator(
                host = host,
                session = session,
                appUpdate =
                    AppUpdateDelegate(
                        host = host,
                        session = session,
                        networkAvailable = { true },
                        controllerFactory = { callback, _ -> updateController.also { it.callback = callback } },
                    ),
            )
        coordinator.sync.setFormAndDataSyncer(syncer)
        host.performRestore()
        host.dispatchOnCreate()
    }

    @After
    fun tearDown() {
        coordinator.sessionExpiration.stopListening()
    }

    // region launch checks

    @Test
    fun `a demo user halts unclaimed with a warning`() {
        host.demoUser = true
        seedSessionCommand()

        assertFalse(coordinator.runLaunchChecks())

        assertEquals(1, host.shownDialogs.size)
        assertTrue(host.shownDialogs.single() is StandardAlertDialog)
        assertTrue(host.startedForResult.isEmpty())
    }

    @Test
    fun `session restoration beats a post-update sync`() {
        seedSessionCommand()
        HiddenPreferences.setPostUpdateSyncNeeded(true)

        assertTrue(coordinator.runLaunchChecks())

        assertEquals(HomeRequestCodes.MODEL_RESULT, host.startedForResult.single().second)
        assertEquals(0, syncer.formSendCount)
    }

    @Test
    fun `a post-update sync beats a pending sync request`() {
        HiddenPreferences.setPostUpdateSyncNeeded(true)
        seedPendingSyncRequest()

        assertTrue(coordinator.runLaunchChecks())

        assertEquals(1, syncer.formSendCount)
        assertFalse(CommCareApplication.instance().isPostUpdateSyncNeeded)
    }

    @Test
    fun `a pending sync request claims before the pin step`() {
        seedPendingSyncRequest()
        host.launchIntent = Intent().putExtra(LoginActivity.LOGIN_MODE, LoginMode.PRIMED)

        assertTrue(coordinator.runLaunchChecks())

        assertEquals(1, syncer.formSendCount)
        assertFalse(syncer.lastSendUserTriggered)
        assertTrue(host.startedForResult.isEmpty())
    }

    @Test
    fun `with nothing pending, the checks end unclaimed`() {
        assertFalse(coordinator.runLaunchChecks())

        assertEquals(0, syncer.formSendCount)
        assertTrue(host.startedForResult.isEmpty())
    }

    @Suppress("DEPRECATION")
    @Test
    fun `a primed login launches pin creation unclaimed`() {
        host.launchIntent = Intent().putExtra(LoginActivity.LOGIN_MODE, LoginMode.PRIMED)

        assertFalse(coordinator.runLaunchChecks())

        val (intent, requestCode) = host.startedForResult.single()
        assertEquals(CreatePinActivity::class.java.name, intent.component?.className)
        assertEquals(LoginMode.PRIMED, intent.getSerializableExtra(LoginActivity.LOGIN_MODE))
        assertEquals(HomeRequestCodes.CREATE_PIN, requestCode)
    }

    @Test
    fun `a claimed launch never reaches the pin step`() {
        seedSessionCommand()
        host.launchIntent = Intent().putExtra(LoginActivity.LOGIN_MODE, LoginMode.PRIMED)

        assertTrue(coordinator.runLaunchChecks())

        assertTrue(host.startedForResult.none { it.first.component?.className == CreatePinActivity::class.java.name })
    }

    // endregion

    // region action availability

    @Test
    fun `withheld actions leave only the language available`() {
        setOfferPinForLogin(true)
        demoteCommCareUpdateToAction()
        host.actionsAvailable = false

        assertFalse(coordinator.actions.canViewSavedForms())
        assertFalse(coordinator.actions.canOpenSettings())
        assertFalse(coordinator.actions.canOpenAdvanced())
        assertFalse(coordinator.actions.canShowAbout())
        assertFalse(coordinator.actions.canUpdateApp())
        assertFalse(coordinator.actions.canSetPin())
        assertFalse(coordinator.actions.canUpdateCommCare())
        assertTrue(coordinator.actions.canChangeLanguage())
    }

    @Test
    fun `plain actions are available when the host allows actions`() {
        assertTrue(coordinator.actions.canViewSavedForms())
        assertTrue(coordinator.actions.canOpenSettings())
        assertTrue(coordinator.actions.canOpenAdvanced())
        assertTrue(coordinator.actions.canShowAbout())
        assertTrue(coordinator.actions.canUpdateApp())
        assertTrue(coordinator.actions.canChangeLanguage())
    }

    @Test
    fun `set pin also needs the pin feature`() {
        setOfferPinForLogin(false)
        assertFalse(coordinator.actions.canSetPin())

        setOfferPinForLogin(true)
        assertTrue(coordinator.actions.canSetPin())
    }

    @Test
    fun `update commcare also needs the update menu to be on offer`() {
        assertFalse(coordinator.actions.canUpdateCommCare())

        demoteCommCareUpdateToAction()
        assertTrue(coordinator.actions.canUpdateCommCare())
    }

    // endregion

    // region action routing

    @Test
    fun `sync sends forms then pulls as a user-triggered sync`() {
        coordinator.actions.sync()

        assertEquals(1, syncer.formSendCount)
        assertTrue(syncer.lastSendSyncsAfterwards)
        assertTrue(syncer.lastSendUserTriggered)
    }

    @Test
    fun `update app opens the content update screen`() {
        coordinator.actions.updateApp()

        val started = shadowOf(host.hostActivity).nextStartedActivity
        assertEquals(UpdateActivity::class.java.name, started.component?.className)
        assertFalse(started.getBooleanExtra(UpdateActivity.KEY_PROCEED_AUTOMATICALLY, true))
    }

    @Test
    fun `update commcare starts the play store update`() {
        coordinator.actions.updateCommCare()

        assertEquals(1, updateController.startUpdateCount)
    }

    @Test
    fun `view saved forms opens the full form archive`() {
        coordinator.actions.viewSavedForms()

        val (intent, requestCode) = host.startedForResult.single()
        assertEquals(FormRecordListActivity::class.java.name, intent.component?.className)
        assertFalse(intent.hasExtra(FormRecord.META_STATUS))
        assertEquals(HomeRequestCodes.GET_INCOMPLETE_FORM, requestCode)
    }

    @Test
    fun `settings and advanced open their own preference modes`() {
        coordinator.actions.openSettings()
        coordinator.actions.openAdvanced()

        val (settings, advanced) = host.startedForResult
        assertEquals(SessionAwarePreferenceActivity::class.java.name, settings.first.component?.className)
        assertEquals(
            CommCarePreferenceActivity.PREF_TYPE_COMMCARE,
            settings.first.getStringExtra(CommCarePreferenceActivity.EXTRA_PREF_TYPE),
        )
        assertEquals(HomeRequestCodes.PREFERENCES_ACTIVITY, settings.second)
        assertEquals(
            CommCarePreferenceActivity.PREF_TYPE_ADVANCED_ACTIONS,
            advanced.first.getStringExtra(CommCarePreferenceActivity.EXTRA_PREF_TYPE),
        )
        assertEquals(HomeRequestCodes.ADVANCED_ACTIONS_ACTIVITY, advanced.second)
    }

    @Suppress("DEPRECATION")
    @Test
    fun `set pin asks for the password before pin creation`() {
        coordinator.actions.setPin()

        val (authIntent, authCode) = host.startedForResult.single()
        assertEquals(PinAuthenticationActivity::class.java.name, authIntent.component?.className)
        assertEquals(HomeRequestCodes.AUTHENTICATION_FOR_PIN, authCode)

        coordinator.onActivityResult(HomeRequestCodes.AUTHENTICATION_FOR_PIN, RESULT_OK, null)

        val (createIntent, createCode) = host.startedForResult.last()
        assertEquals(CreatePinActivity::class.java.name, createIntent.component?.className)
        assertEquals(LoginMode.PASSWORD, createIntent.getSerializableExtra(LoginActivity.LOGIN_MODE))
        assertEquals(HomeRequestCodes.CREATE_PIN, createCode)
    }

    @Test
    fun `a cancelled password check starts no pin creation`() {
        coordinator.onActivityResult(HomeRequestCodes.AUTHENTICATION_FOR_PIN, RESULT_CANCELED, null)

        assertTrue(host.startedForResult.isEmpty())
    }

    @Test
    fun `change language shows the locale chooser`() {
        coordinator.actions.changeLanguage()

        assertEquals(1, host.shownDialogs.size)
    }

    // endregion

    private fun seedSessionCommand() {
        CommCareApplication
            .instance()
            .currentSessionWrapper.session
            .setCommand(FORM_COMMAND)
    }

    private fun seedPendingSyncRequest() {
        val username =
            CommCareApplication
                .instance()
                .session.loggedInUser.username
        PreferenceManager
            .getDefaultSharedPreferences(CommCareApplication.instance())
            .edit()
            .putString(
                HiddenPreferences.BACKGROUND_SYNC_PENDING + username + "@" + HiddenPreferences.getUserDomainWithoutServerUrl(),
                "pending",
            ).apply()
    }

    private fun setOfferPinForLogin(offered: Boolean) {
        CommCareApplication
            .instance()
            .currentApp.appPreferences
            .edit()
            .putString(DeveloperPreferences.OFFER_PIN_FOR_LOGIN, if (offered) PrefValues.YES else PrefValues.NO)
            .apply()
    }

    private fun demoteCommCareUpdateToAction() {
        repeat(TIMES_DISMISSED_TO_DEMOTE) {
            HiddenPreferences.incrementCommCareUpdateCancellationCounter(AVAILABLE_VERSION.toString())
        }
        updateController.availableVersion = AVAILABLE_VERSION
        updateController.state = AppUpdateState.AVAILABLE
        updateController.fireCallback()
    }

    private companion object {
        const val FORM_COMMAND = "m0-f0"
        const val AVAILABLE_VERSION = 500
        const val TIMES_DISMISSED_TO_DEMOTE = 4
    }
}
