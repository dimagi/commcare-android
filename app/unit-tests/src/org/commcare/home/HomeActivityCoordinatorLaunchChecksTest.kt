package org.commcare.home

import android.app.Activity.RESULT_CANCELED
import android.app.Activity.RESULT_OK
import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import io.mockk.verifyOrder
import org.commcare.CommCareTestApplication
import org.commcare.activities.CommCarePreferenceActivity
import org.commcare.activities.CreatePinActivity
import org.commcare.activities.FormRecordListActivity
import org.commcare.activities.LoginActivity
import org.commcare.activities.LoginMode
import org.commcare.activities.PinAuthenticationActivity
import org.commcare.activities.SessionAwarePreferenceActivity
import org.commcare.android.database.user.models.FormRecord
import org.commcare.preferences.DeveloperPreferences
import org.commcare.views.dialogs.StandardAlertDialog
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The coordinator's launch-check order and early returns, against mocked delegates, plus the
 * action facade's availability queries and routing.
 */
@Config(application = CommCareTestApplication::class)
@RunWith(AndroidJUnit4::class)
class HomeActivityCoordinatorLaunchChecksTest {
    private val host = FakeHomeActivityHost()
    private val appUpdate = mockk<AppUpdateDelegate>(relaxed = true)
    private val sync = mockk<SyncDelegate>(relaxed = true)
    private val sessionLaunch = mockk<SessionLaunchDelegate>(relaxed = true)
    private val coordinator =
        HomeActivityCoordinator(
            host = host,
            session = FakeSeatedAppSession(),
            appUpdate = appUpdate,
            sync = sync,
            sessionLaunch = sessionLaunch,
        )

    private val launchSteps: List<Pair<String, () -> Boolean>> =
        listOf(
            "update info form" to { sessionLaunch.showUpdateInfoForm() },
            "form restoration" to { sessionLaunch.tryRestoringFormFromSessionExpiration() },
            "session restoration" to { sessionLaunch.tryRestoringSession() },
            "post-update sync" to { sync.runPostUpdateSyncIfNeeded() },
            "pending sync request" to { sync.runPendingSyncRequestIfNeeded() },
            "update prompt" to { appUpdate.promptForUpdateIfNeeded(false) },
        )

    @After
    fun tearDown() {
        unmockkStatic(DeveloperPreferences::class)
    }

    // region launch checks

    @Test
    fun `a demo user halts the checks unclaimed with a warning and runs no step`() {
        host.demoUser = true

        assertFalse(coordinator.runLaunchChecks())

        assertEquals(1, host.shownDialogs.size)
        assertTrue(host.shownDialogs.single() is StandardAlertDialog)
        launchSteps.forEach { (_, step) -> verify(exactly = 0) { step() } }
    }

    @Test
    fun `each claiming step ends the checks with true and runs nothing after it`() {
        launchSteps.forEachIndexed { claimingIndex, (name, _) ->
            launchSteps.forEachIndexed { index, (_, step) -> every { step() } returns (index == claimingIndex) }

            assertTrue("$name should claim the launch", coordinator.runLaunchChecks())

            launchSteps.forEachIndexed { index, (_, step) ->
                verify(exactly = if (index <= claimingIndex) 1 else 0) { step() }
            }
            clearMocks(appUpdate, sync, sessionLaunch, answers = false)
        }
        assertTrue(host.startedForResult.isEmpty())
    }

    @Test
    fun `with no step claiming, every step runs in order and the checks end unclaimed`() {
        launchSteps.forEach { (_, step) -> every { step() } returns false }

        assertFalse(coordinator.runLaunchChecks())

        verifyOrder {
            sessionLaunch.showUpdateInfoForm()
            sessionLaunch.tryRestoringFormFromSessionExpiration()
            sessionLaunch.tryRestoringSession()
            sync.runPostUpdateSyncIfNeeded()
            sync.runPendingSyncRequestIfNeeded()
            appUpdate.promptForUpdateIfNeeded(false)
        }
    }

    @Test
    fun `a primed login reaches the pin step and launches pin creation without claiming`() {
        launchSteps.forEach { (_, step) -> every { step() } returns false }
        host.launchIntent = Intent().putExtra(LoginActivity.LOGIN_MODE, LoginMode.PRIMED)

        assertFalse(coordinator.runLaunchChecks())

        val (intent, requestCode) = host.startedForResult.single()
        assertEquals(CreatePinActivity::class.java.name, intent.component?.className)
        assertEquals(LoginMode.PRIMED, intent.getSerializableExtra(LoginActivity.LOGIN_MODE))
        assertEquals(HomeActivityCoordinator.CREATE_PIN_REQUEST_CODE, requestCode)
    }

    @Test
    fun `a claimed launch never reaches the pin step`() {
        every { sessionLaunch.tryRestoringSession() } returns true
        host.launchIntent = Intent().putExtra(LoginActivity.LOGIN_MODE, LoginMode.PRIMED)

        assertTrue(coordinator.runLaunchChecks())

        assertTrue(host.startedForResult.isEmpty())
    }

    // endregion

    // region action availability

    @Test
    fun `every action but the language is unavailable when the host withholds actions`() {
        mockkStatic(DeveloperPreferences::class)
        every { DeveloperPreferences.shouldOfferPinForLogin() } returns true
        every { appUpdate.showCommCareUpdateMenu } returns true
        host.actionsAvailable = false

        assertFalse(coordinator.canViewSavedForms())
        assertFalse(coordinator.canOpenSettings())
        assertFalse(coordinator.canOpenAdvanced())
        assertFalse(coordinator.canShowAbout())
        assertFalse(coordinator.canUpdateApp())
        assertFalse(coordinator.canSetPin())
        assertFalse(coordinator.canUpdateCommCare())
        assertTrue(coordinator.canChangeLanguage())
    }

    @Test
    fun `plain actions are available when the host allows actions`() {
        assertTrue(coordinator.canViewSavedForms())
        assertTrue(coordinator.canOpenSettings())
        assertTrue(coordinator.canOpenAdvanced())
        assertTrue(coordinator.canShowAbout())
        assertTrue(coordinator.canUpdateApp())
        assertTrue(coordinator.canChangeLanguage())
    }

    @Test
    fun `set pin also needs the pin feature`() {
        mockkStatic(DeveloperPreferences::class)
        every { DeveloperPreferences.shouldOfferPinForLogin() } returns false
        assertFalse(coordinator.canSetPin())

        every { DeveloperPreferences.shouldOfferPinForLogin() } returns true
        assertTrue(coordinator.canSetPin())
    }

    @Test
    fun `update commcare also needs the update menu to be on offer`() {
        every { appUpdate.showCommCareUpdateMenu } returns false
        assertFalse(coordinator.canUpdateCommCare())

        every { appUpdate.showCommCareUpdateMenu } returns true
        assertTrue(coordinator.canUpdateCommCare())
    }

    // endregion

    // region action routing

    @Test
    fun `sync sends forms then pulls as a user-triggered sync`() {
        coordinator.sync()

        verify { sync.sendFormsOrSync(true) }
    }

    @Test
    fun `the update actions hand off to the app update delegate`() {
        coordinator.updateApp()
        coordinator.updateCommCare()

        verify { appUpdate.launchUpdateActivity(false) }
        verify { appUpdate.startCommCareUpdate() }
    }

    @Test
    fun `view saved forms opens the full form archive`() {
        coordinator.viewSavedForms()

        val (intent, requestCode) = host.startedForResult.single()
        assertEquals(FormRecordListActivity::class.java.name, intent.component?.className)
        assertFalse(intent.hasExtra(FormRecord.META_STATUS))
        assertEquals(HomeActivityCoordinator.FORM_ARCHIVE_REQUEST_CODE, requestCode)
    }

    @Test
    fun `settings and advanced open the preference screen in their own modes`() {
        coordinator.openSettings()
        coordinator.openAdvanced()

        val (settings, advanced) = host.startedForResult
        assertEquals(SessionAwarePreferenceActivity::class.java.name, settings.first.component?.className)
        assertEquals(
            CommCarePreferenceActivity.PREF_TYPE_COMMCARE,
            settings.first.getStringExtra(CommCarePreferenceActivity.EXTRA_PREF_TYPE),
        )
        assertEquals(HomeActivityCoordinator.PREFERENCES_REQUEST_CODE, settings.second)
        assertEquals(
            CommCarePreferenceActivity.PREF_TYPE_ADVANCED_ACTIONS,
            advanced.first.getStringExtra(CommCarePreferenceActivity.EXTRA_PREF_TYPE),
        )
        assertEquals(HomeActivityCoordinator.ADVANCED_ACTIONS_REQUEST_CODE, advanced.second)
    }

    @Test
    fun `set pin asks for the password before pin creation`() {
        coordinator.setPin()

        val (authIntent, authCode) = host.startedForResult.single()
        assertEquals(PinAuthenticationActivity::class.java.name, authIntent.component?.className)
        assertEquals(HomeActivityCoordinator.AUTHENTICATION_FOR_PIN_REQUEST_CODE, authCode)

        assertTrue(
            coordinator.onActivityResult(HomeActivityCoordinator.AUTHENTICATION_FOR_PIN_REQUEST_CODE, RESULT_OK, null),
        )

        val (createIntent, createCode) = host.startedForResult.last()
        assertEquals(CreatePinActivity::class.java.name, createIntent.component?.className)
        assertEquals(LoginMode.PASSWORD, createIntent.getSerializableExtra(LoginActivity.LOGIN_MODE))
        assertEquals(HomeActivityCoordinator.CREATE_PIN_REQUEST_CODE, createCode)
    }

    @Test
    fun `a cancelled password check is consumed without starting pin creation`() {
        assertTrue(
            coordinator.onActivityResult(HomeActivityCoordinator.AUTHENTICATION_FOR_PIN_REQUEST_CODE, RESULT_CANCELED, null),
        )

        assertTrue(host.startedForResult.isEmpty())
    }

    @Test
    fun `change language shows the locale chooser`() {
        coordinator.changeLanguage()

        assertEquals(1, host.shownDialogs.size)
    }

    // endregion
}
