package org.commcare.home

import android.app.Activity.RESULT_OK
import android.content.Intent
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.commcare.CommCareApplication
import org.commcare.CommCareTestApplication
import org.commcare.activities.BaseHomeScreenActivityTest.Companion.TEST_APP_PATH
import org.commcare.activities.BaseHomeScreenActivityTest.Companion.TEST_PASSWORD
import org.commcare.activities.BaseHomeScreenActivityTest.Companion.TEST_USER
import org.commcare.activities.UpdateActivity
import org.commcare.android.mocks.FormAndDataSyncerFake
import org.commcare.android.util.TestAppInstaller
import org.commcare.preferences.AdvancedActionsPreferences
import org.commcare.preferences.DeveloperPreferences
import org.commcare.preferences.HiddenPreferences
import org.commcare.preferences.PrefValues
import org.commcare.tasks.DataPullTask.PullTaskResult
import org.javarosa.core.services.locale.Localization
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast

/**
 * The results, resume checks and app-health checks the coordinator took over from the home
 * activity, over real delegates with only the syncer faked.
 */
@Config(application = CommCareTestApplication::class)
@RunWith(AndroidJUnit4::class)
class HomeActivityCoordinatorResultsTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var host: FakeHomeActivityHost
    private lateinit var syncer: FormAndDataSyncerFake
    private lateinit var coordinator: HomeActivityCoordinator

    @Before
    fun setUp() {
        TestAppInstaller.installAppAndLogin(TEST_APP_PATH, TEST_USER, TEST_PASSWORD)
        host = FakeHomeActivityHost()
        syncer = FormAndDataSyncerFake()
        coordinator = HomeActivityCoordinator(host, FakeSeatedAppSession())
        coordinator.sync.setFormAndDataSyncer(syncer)
        host.performRestore()
        host.dispatchOnCreate()
    }

    @After
    fun tearDown() {
        UpdateActivity.sBlockedUpdateWorkflowInProgress = false
        coordinator.sessionExpiration.stopListening()
    }

    // region settings and advanced actions

    @Test
    fun `a data reset from settings finishes the host`() {
        coordinator.onActivityResult(HomeRequestCodes.PREFERENCES_ACTIVITY, HomeResultCodes.RESULT_DATA_RESET, null)

        assertEquals(1, host.finishCount)
        assertTrue(host.startedForResult.isEmpty())
    }

    @Test
    fun `a custom restore uses the chosen file`() {
        val restoreFile = tempFolder.newFile("restore.xml")
        setCustomRestoreLocation(restoreFile.absolutePath)

        coordinator.onActivityResult(HomeRequestCodes.PREFERENCES_ACTIVITY, DeveloperPreferences.RESULT_SYNC_CUSTOM, null)

        assertEquals(listOf(restoreFile), syncer.customRestoreFiles)
    }

    @Test
    fun `a custom restore with no file chosen says so`() {
        setCustomRestoreLocation("")

        coordinator.onActivityResult(HomeRequestCodes.PREFERENCES_ACTIVITY, DeveloperPreferences.RESULT_SYNC_CUSTOM, null)

        assertTrue(syncer.customRestoreFiles.isEmpty())
        assertEquals(Localization.get("custom.restore.file.not.set"), ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun `a custom restore from a missing file says so`() {
        setCustomRestoreLocation(tempFolder.root.resolve("missing.xml").absolutePath)

        coordinator.onActivityResult(HomeRequestCodes.PREFERENCES_ACTIVITY, DeveloperPreferences.RESULT_SYNC_CUSTOM, null)

        assertTrue(syncer.customRestoreFiles.isEmpty())
        assertEquals(Localization.get("custom.restore.file.not.exist"), ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun `processed forms are counted in a toast`() {
        val result =
            Intent()
                .putExtra(AdvancedActionsPreferences.FORM_PROCESS_COUNT_KEY, 3)
                .putExtra(AdvancedActionsPreferences.FORM_PROCESS_MESSAGE_KEY, FORMS_PROCESSED_KEY)

        coordinator.onActivityResult(
            HomeRequestCodes.ADVANCED_ACTIONS_ACTIVITY,
            HomeResultCodes.RESULT_FORMS_PROCESSED,
            result,
        )

        assertEquals(listOf(Localization.get(FORMS_PROCESSED_KEY, arrayOf("3"))), host.shownToasts)
        assertEquals(1, host.refreshHostUiCount)
        assertTrue(host.startedForResult.isEmpty())
    }

    // endregion

    // region resume and app-health checks

    @Test
    fun `with nothing pending, resuming shows home`() {
        coordinator.onHostResumed()

        assertEquals(1, host.actionBarRefreshCount)
        assertEquals(1, host.refreshHostUiCount)
        assertEquals(0, syncer.formSendCount)
        assertTrue(CommCareApplication.instance().session.appHealthChecksCompleted())
    }

    @Test
    fun `a pending auto sync takes over from home on resume`() {
        seedPendingAutoSync()

        coordinator.onHostResumed()

        assertEquals(1, syncer.formSendCount)
        assertFalse(syncer.lastSendUserTriggered)
        assertEquals("home is not refreshed while the host is not visible", 0, host.refreshHostUiCount)
    }

    @Test
    fun `an app sync refreshes a visible home first`() {
        host.moveTo(Lifecycle.State.STARTED)

        coordinator.triggerSync(autoSyncPending = true)

        assertEquals(1, host.refreshHostUiCount)
        assertEquals(1, syncer.formSendCount)
    }

    @Test
    fun `a launch redirect skips the next home dispatch`() {
        coordinator.redirectedInOnCreate = true

        coordinator.onHostResumed()
        assertEquals(0, host.actionBarRefreshCount)
        assertEquals(0, host.refreshHostUiCount)

        coordinator.onHostResumed()
        assertEquals(1, host.refreshHostUiCount)
    }

    @Test
    fun `a navigating result skips the next home dispatch`() {
        coordinator.onActivityResult(HomeRequestCodes.GET_REMOTE_DATA, RESULT_OK, null)
        val launchedByResult = host.startedForResult.size

        coordinator.onHostResumed()
        assertEquals(0, host.refreshHostUiCount)

        coordinator.onHostResumed()
        assertEquals(1, host.refreshHostUiCount)
        assertEquals(launchedByResult, host.startedForResult.size)
    }

    @Test
    fun `launch checks always clear one-time flags`() {
        HiddenPreferences.setPostUpdateSyncNeeded(true)

        assertTrue(coordinator.runLaunchChecks())

        assertFalse(CommCareApplication.instance().isPostUpdateSyncNeeded)
        assertFalse(
            HiddenPreferences.isPendingSyncRequest(
                CommCareApplication
                    .instance()
                    .session.loggedInUser.username,
            ),
        )
    }

    // endregion

    @Test
    fun `a pull during a blocked update resumes the update`() {
        UpdateActivity.sBlockedUpdateWorkflowInProgress = true

        coordinator.onPullTaskFinished(PullTaskResult.DOWNLOAD_SUCCESS)

        val intent = host.startedActivities.single()
        assertEquals(UpdateActivity::class.java.name, intent.component!!.className)
        assertTrue(intent.getBooleanExtra(UpdateActivity.KEY_PRE_UPDATE_SYNC_SUCCEED, false))
    }

    @Test
    fun `a pull with no blocked update starts nothing`() {
        coordinator.onPullTaskFinished(PullTaskResult.DOWNLOAD_SUCCESS)

        assertTrue(host.startedActivities.isEmpty())
    }

    private fun setCustomRestoreLocation(path: String) {
        CommCareApplication
            .instance()
            .currentApp.appPreferences
            .edit()
            .putString(CUSTOM_RESTORE_LOCATION_KEY, path)
            .apply()
    }

    private fun seedPendingAutoSync() {
        CommCareApplication
            .instance()
            .currentApp.appPreferences
            .edit()
            .putString(HiddenPreferences.AUTO_SYNC_FREQUENCY, PrefValues.FREQUENCY_DAILY)
            .apply()
    }

    private companion object {
        const val CUSTOM_RESTORE_LOCATION_KEY = "cc-custom-restore-doc-location"
        const val FORMS_PROCESSED_KEY = "bulk.form.send.success"
    }
}
