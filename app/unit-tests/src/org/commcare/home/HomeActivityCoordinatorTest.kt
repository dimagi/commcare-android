package org.commcare.home

import android.app.Activity.RESULT_CANCELED
import android.app.Activity.RESULT_OK
import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.commcare.CommCareApplication
import org.commcare.CommCareTestApplication
import org.commcare.activities.BaseHomeScreenActivityTest.Companion.TEST_APP_PATH
import org.commcare.activities.BaseHomeScreenActivityTest.Companion.TEST_PASSWORD
import org.commcare.activities.BaseHomeScreenActivityTest.Companion.TEST_USER
import org.commcare.android.database.user.models.FormRecord
import org.commcare.android.mocks.FormAndDataSyncerFake
import org.commcare.android.util.TestAppInstaller
import org.commcare.appupdate.AppUpdateController.IN_APP_UPDATE_REQUEST_CODE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Host-agnostic coordinator tests. Robolectric is needed only for real `Bundle` and
 * `LifecycleRegistry` behavior — no activity is built.
 */
@Config(application = CommCareTestApplication::class)
@RunWith(AndroidJUnit4::class)
class HomeActivityCoordinatorTest {
    @Test
    fun `coordinator registers itself and its app-update delegate on the host lifecycle during construction`() {
        val host = FakeHomeActivityHost()

        HomeActivityCoordinator(host)

        assertEquals(2, host.observerCount)
    }

    /**
     * A write made before `ON_CREATE` (as `processFromExternalLaunch` does) must not be clobbered by
     * the coordinator's own `onCreate` restore pass.
     */
    @Test
    fun `a write before on-create is not overwritten by the on-create restore`() {
        val host = FakeHomeActivityHost()
        val coordinator = HomeActivityCoordinator(host)
        host.performRestore()
        coordinator.launchState.wasExternal = true

        host.dispatchOnCreate()

        assertTrue(coordinator.launchState.wasExternal)
    }

    @Test
    fun `forwarding an activity result does not disturb launch and nav state`() {
        val host = FakeHomeActivityHost()
        val coordinator = HomeActivityCoordinator(host)
        host.performRestore()
        host.dispatchOnCreate()
        coordinator.launchState.wasExternal = true

        coordinator.onActivityResult(REQUEST_CODE, RESULT_OK, Intent())

        assertTrue(coordinator.launchState.wasExternal)
        assertFalse(coordinator.launchState.loginExtraWasConsumed)
    }

    @Test
    fun `an in-app update result goes no further than the update delegate`() {
        val host = FakeHomeActivityHost()
        val coordinator = HomeActivityCoordinator(host, FakeSeatedAppSession())

        coordinator.onActivityResult(IN_APP_UPDATE_REQUEST_CODE, RESULT_OK, null)

        assertTrue(host.startedForResult.isEmpty())
        assertTrue(host.finishedWithResults.isEmpty())
    }

    @Test
    fun `a form completed in form entry is sent without a sync`() {
        TestAppInstaller.installAppAndLogin(TEST_APP_PATH, TEST_USER, TEST_PASSWORD)
        val host = FakeHomeActivityHost()
        val coordinator = HomeActivityCoordinator(host)
        host.performRestore()
        host.dispatchOnCreate()
        val syncer = FormAndDataSyncerFake()
        coordinator.sync.setFormAndDataSyncer(syncer)
        val state = CommCareApplication.instance().currentSessionWrapper
        state.session.setCommand(FORM_COMMAND)
        coordinator.sessionLaunch.startForm(state)
        val record = state.formRecord
        record.status = FormRecord.STATUS_UNSENT
        CommCareApplication.instance().getUserStorage(FormRecord::class.java).write(record)

        coordinator.handleFormEntryResult(RESULT_OK, Intent())

        assertEquals(1, syncer.formSendCount)
        assertFalse(syncer.lastSendSyncsAfterwards)
    }

    @Test
    fun `a cancelled form is not sent`() {
        TestAppInstaller.installAppAndLogin(TEST_APP_PATH, TEST_USER, TEST_PASSWORD)
        val host = FakeHomeActivityHost()
        val coordinator = HomeActivityCoordinator(host)
        host.performRestore()
        host.dispatchOnCreate()
        val syncer = FormAndDataSyncerFake()
        coordinator.sync.setFormAndDataSyncer(syncer)
        val state = CommCareApplication.instance().currentSessionWrapper
        state.session.setCommand(FORM_COMMAND)
        coordinator.sessionLaunch.startForm(state)

        coordinator.handleFormEntryResult(RESULT_CANCELED, Intent())

        assertEquals(0, syncer.formSendCount)
    }

    private companion object {
        const val REQUEST_CODE = 7
        const val FORM_COMMAND = "m0-f0"
    }
}
