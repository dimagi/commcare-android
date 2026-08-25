package org.commcare.activities

import android.widget.Button
import androidx.appcompat.app.AlertDialog
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.Job
import org.commcare.CommCareTestApplication
import org.commcare.android.util.ReflectionUtils
import org.commcare.android.util.TestAppInstaller
import org.commcare.dalvik.R
import org.commcare.login.LoginPhase
import org.commcare.tasks.DataPullTask
import org.commcare.views.dialogs.CustomProgressDialog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config

/**
 * Regression pins for CI-915, covering both halves of the login progress dialog's behaviour when
 * the user backgrounds the app mid-login.
 *
 * The login engine drives its dialogs by calling `showProgressDialog` directly from
 * `LoginActivity.updateLoginProgressUi`, rather than through the paused-guarded
 * `startBlockingForTask` path the pre-2.64 implementation used. `showProgressDialog` commits with
 * `showNow`, so without a guard the `Syncing -> SigningIn` swap threw
 * `IllegalStateException: Can not perform this action after onSaveInstanceState` whenever
 * `DataPullTask` finished while the activity was stopped.
 *
 * The engine also connects its tasks to a `HeadlessTaskConnector`, so nothing is ever registered
 * with the activity's `TaskConnectorViewModel` and the inherited `cancelCurrentTask()` had nothing
 * to cancel — the dialog's STOP button disabled itself and hung on "Cancelling...".
 */
@Config(application = CommCareTestApplication::class)
@RunWith(AndroidJUnit4::class)
class LoginProgressDialogLifecycleTest {
    private lateinit var controller: ActivityController<LoginActivity>
    private lateinit var activity: LoginActivity

    /**
     * Task id behind [LoginPhase.SigningIn]. Read off the activity because production keeps it
     * private, and a bare literal here would silently stop matching if it were renumbered.
     */
    private val keyExchangeTaskId: Int
        get() = ReflectionUtils.readField(activity, "TASK_KEY_EXCHANGE") as Int

    private val syncTaskId = DataPullTask.DATA_PULL_TASK_ID

    @Before
    fun setUp() {
        (CommCareTestApplication.instance() as CommCareTestApplication).initWorkManager()
        TestAppInstaller.installApp(TEST_APP_PATH)

        controller = Robolectric.buildActivity(LoginActivity::class.java).setup()
        activity = controller.get()
    }

    // ======== The crash: deferring the show while fragments are paused ========

    @Test
    fun `show requested while stopped commits nothing`() {
        background()

        activity.showProgressDialog(syncTaskId)

        assertNull("No dialog should be committed while fragments are paused", currentDialog())
    }

    @Test
    fun `show requested while stopped is committed on resume`() {
        background()
        activity.showProgressDialog(syncTaskId)

        foreground()

        assertEquals(
            "The deferred dialog should be committed once fragments resume",
            syncTaskId,
            currentDialog()?.taskId,
        )
    }

    /**
     * The exact CI-915 sequence: the sync dialog is up, the user backgrounds the app, and
     * `DataPullTask` finishing emits `Syncing -> SigningIn`, which dismisses one dialog and shows
     * the next. Only the incoming dialog should survive to the resume.
     */
    @Test
    fun `sync to signing in swap while stopped leaves only the incoming dialog`() {
        activity.showProgressDialog(syncTaskId)
        background()

        activity.dismissProgressDialogForTask(syncTaskId)
        activity.showProgressDialog(keyExchangeTaskId)

        foreground()

        assertEquals(
            "The key exchange dialog should replace the sync dialog on resume",
            keyExchangeTaskId,
            currentDialog()?.taskId,
        )
    }

    /**
     * A login that both progresses and finishes while backgrounded: the deferred show is followed
     * by its own dismissal, so nothing should be left showing when the user comes back.
     */
    @Test
    fun `deferred show cancelled by its own dismissal leaves no dialog`() {
        activity.showProgressDialog(syncTaskId)
        background()

        activity.dismissProgressDialogForTask(syncTaskId)
        activity.showProgressDialog(keyExchangeTaskId)
        activity.dismissProgressDialogForTask(keyExchangeTaskId)

        foreground()

        assertNull("A login that finished while backgrounded should leave no dialog", currentDialog())
    }

    /**
     * A blanket dismissal is the caller saying nothing should be left showing, so it has to drop a
     * post-poned show too — otherwise the dialog would appear after the thing that wanted it gone.
     */
    @Test
    fun `blanket dismissal while stopped drops a post-poned show`() {
        background()

        activity.showProgressDialog(syncTaskId)
        activity.dismissCurrentProgressDialog()

        foreground()

        assertNull("A dismissed pending show should not appear on resume", currentDialog())
    }

    /**
     * Two login phases can map to the same task id, so a post-poned show can land on a dialog that
     * is already correct. Rebuilding it would throw away the title and message the task has
     * reported since, so the existing dialog is kept.
     */
    @Test
    fun `post-poned show for the task already showing keeps that dialog`() {
        activity.showProgressDialog(syncTaskId)
        val original = currentDialog()
        background()

        activity.showProgressDialog(syncTaskId)

        foreground()

        assertSame("The dialog already up for this task should be left alone", original, currentDialog())
    }

    // ========
    // The STOP button
    //
    // These two pin the fix rather than reproducing the bug: the pre-fix build has no retained job
    // at all, so they go red on the missing `loginJob` field rather than on a stuck dialog. The
    // four cases above are the ones that reproduce the CI-915 crash itself.
    // ========

    @Test
    fun `stop button cancels the login pipeline and dismisses the dialog`() {
        val job = Job()
        startFakeSyncPhase(job)

        clickStopButton()

        assertTrue("STOP should cancel the login pipeline's job", job.isCancelled)
        assertNull("STOP should dismiss the sync dialog", currentDialog())
    }

    @Test
    fun `cancelling clears the retained job so a later stop is inert`() {
        val job = Job()
        startFakeSyncPhase(job)

        activity.cancelCurrentTask()
        activity.cancelCurrentTask()

        assertTrue(job.isCancelled)
        assertNull("The cancelled job should not be retained", ReflectionUtils.readField(activity, "loginJob"))
    }

    // ======== Helpers ========

    /**
     * Put the activity in the state the crash needs: stopped, so `areFragmentsPaused` is set and
     * `onSaveInstanceState` has run.
     */
    private fun background() {
        controller.pause().stop()
    }

    /**
     * Bring the activity back through `onResumeFragments`, which is what flushes the deferred
     * dialog work. `postResume` is required — `resume` alone does not dispatch it.
     */
    private fun foreground() {
        controller.start().resume().postResume()
    }

    private fun currentDialog(): CustomProgressDialog? = activity.currentProgressDialog

    /**
     * Stand in for a login that has reached the sync phase: the dialog the STOP button lives on is
     * showing, and [job] is the pipeline the activity would cancel.
     */
    private fun startFakeSyncPhase(job: Job) {
        ReflectionUtils.writeField(activity, "currentLoginPhase", LoginPhase.Syncing)
        ReflectionUtils.writeField(activity, "loginJob", job)
        activity.showProgressDialog(syncTaskId)
    }

    private fun clickStopButton() {
        val dialog = requireNotNull(currentDialog()?.dialog as? AlertDialog) { "sync dialog not showing" }
        val stopButton =
            requireNotNull(dialog.findViewById<Button>(R.id.dialog_cancel_button)) {
                "sync dialog has no STOP button"
            }
        stopButton.performClick()
    }

    companion object {
        private const val TEST_APP_PATH = "jr://resource/commcare-apps/form_nav_tests/profile.ccpr"
    }
}
