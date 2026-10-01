package org.commcare.home

import android.app.Activity.RESULT_CANCELED
import android.app.Activity.RESULT_OK
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.commcare.CommCareTestApplication
import org.commcare.activities.UpdateActivity
import org.commcare.appupdate.AppUpdateController.IN_APP_UPDATE_REQUEST_CODE
import org.commcare.appupdate.AppUpdateState
import org.commcare.preferences.HiddenPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@Config(application = CommCareTestApplication::class)
@RunWith(AndroidJUnit4::class)
class AppUpdateDelegateTest {
    private val host = FakeHomeActivityHost()
    private val controller = FakeAppUpdateController()

    private fun created(
        session: SeatedAppSession = FakeSeatedAppSession(),
        networkAvailable: Boolean = true,
    ): AppUpdateDelegate {
        val delegate =
            AppUpdateDelegate(
                host = host,
                session = session,
                networkAvailable = { networkAvailable },
                controllerFactory = { callback, _ -> controller.also { it.callback = callback } },
            )
        host.lifecycle.addObserver(delegate)
        host.performRestore()
        host.dispatchOnCreate()
        return delegate
    }

    private fun reportAvailable(
        version: Int,
        timesDismissed: Int = 0,
    ) {
        repeat(timesDismissed) { HiddenPreferences.incrementCommCareUpdateCancellationCounter(version.toString()) }
        controller.availableVersion = version
        controller.state = AppUpdateState.AVAILABLE
        controller.fireCallback()
    }

    @Test
    fun `registers the update controller on create`() {
        created()

        assertEquals(1, controller.registerCount)
        assertEquals(0, controller.unregisterCount)
    }

    @Test
    fun `unregisters the update controller on destroy`() {
        created()

        host.dispatchOnDestroy()

        assertEquals(1, controller.unregisterCount)
    }

    @Test
    fun `an available update at the cancellation threshold starts the update`() {
        created()

        reportAvailable(version = 500, timesDismissed = 3)

        assertEquals(1, controller.startUpdateCount)
    }

    @Test
    fun `an available update over the cancellation threshold is demoted to an action`() {
        val delegate = created()
        val refreshesBefore = host.refreshActionSurfaceCount

        reportAvailable(version = 501, timesDismissed = 4)

        assertEquals(0, controller.startUpdateCount)
        assertTrue(delegate.showCommCareUpdateMenu)
        assertEquals(refreshesBefore + 1, host.refreshActionSurfaceCount)
    }

    @Test
    fun `an available update is not started once the session has suppressed in-app updates`() {
        val session = FakeSeatedAppSession().apply { inAppUpdateVisible = false }
        val delegate = created(session = session)

        reportAvailable(version = 502)

        assertEquals(0, controller.startUpdateCount)
        assertFalse(delegate.showCommCareUpdateMenu)
    }

    @Test
    fun `no available update with network hides the prompt for the session`() {
        val session = FakeSeatedAppSession()
        created(session = session, networkAvailable = true)
        controller.state = AppUpdateState.UNAVAILABLE

        controller.fireCallback()

        assertEquals(1, session.hideInAppUpdateCount)
    }

    @Test
    fun `no available update without network leaves the prompt alone`() {
        val session = FakeSeatedAppSession()
        created(session = session, networkAvailable = false)
        controller.state = AppUpdateState.UNAVAILABLE

        controller.fireCallback()

        assertEquals(0, session.hideInAppUpdateCount)
    }

    @Test
    fun `a download starting clears the demoted update action`() {
        val delegate = created()
        reportAvailable(version = 504, timesDismissed = 4)
        val refreshesBefore = host.refreshActionSurfaceCount

        controller.state = AppUpdateState.DOWNLOADING
        controller.fireCallback()

        assertFalse(delegate.showCommCareUpdateMenu)
        assertEquals(refreshesBefore + 1, host.refreshActionSurfaceCount)
    }

    @Test
    fun `a downloaded update offers a restart`() {
        created()
        controller.state = AppUpdateState.DOWNLOADED

        controller.fireCallback()

        assertEquals(1, host.shownDialogs.size)
    }

    @Test
    fun `a cancelled in-app update counts the refusal and hides the prompt`() {
        val session = FakeSeatedAppSession()
        val delegate = created(session = session)
        controller.availableVersion = 505
        val countBefore = HiddenPreferences.getCommCareUpdateCancellationCounter("505")

        val consumed = delegate.onActivityResult(IN_APP_UPDATE_REQUEST_CODE, RESULT_CANCELED)

        assertTrue(consumed)
        assertEquals(countBefore + 1, HiddenPreferences.getCommCareUpdateCancellationCounter("505"))
        assertEquals(1, session.hideInAppUpdateCount)
    }

    @Test
    fun `an accepted in-app update counts no refusal`() {
        val session = FakeSeatedAppSession()
        val delegate = created(session = session)
        controller.availableVersion = 506
        val countBefore = HiddenPreferences.getCommCareUpdateCancellationCounter("506")

        delegate.onActivityResult(IN_APP_UPDATE_REQUEST_CODE, RESULT_OK)

        assertEquals(countBefore, HiddenPreferences.getCommCareUpdateCancellationCounter("506"))
        assertEquals(0, session.hideInAppUpdateCount)
    }

    @Test
    fun `an unrelated request code is not consumed`() {
        val delegate = created()

        assertFalse(delegate.onActivityResult(IN_APP_UPDATE_REQUEST_CODE + 1, RESULT_OK))
    }

    @Test
    fun `launching the content update starts the update activity`() {
        val delegate = created()

        delegate.launchUpdateActivity(true)

        val started = shadowOf(host.hostActivity).nextStartedActivity
        assertEquals(UpdateActivity::class.java.name, started.component?.className)
        assertTrue(started.getBooleanExtra(UpdateActivity.KEY_PROCEED_AUTOMATICALLY, false))
    }
}
