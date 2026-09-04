package org.commcare.activities

import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import org.commcare.CommCareTestApplication
import org.commcare.android.util.ReflectionUtils
import org.commcare.android.util.TestAppInstaller
import org.commcare.dalvik.R
import org.commcare.login.LoginError
import org.commcare.login.LoginPhase
import org.commcare.login.LoginProgress
import org.commcare.login.LoginProgressListener
import org.commcare.login.LoginResult
import org.commcare.login.LoginViewModel
import org.commcare.login.PostLoginOutcome
import org.commcare.tasks.DataPullTask
import org.commcare.views.dialogs.CustomProgressDialog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

/**
 * Regression pins covering the login progress dialog's behavior when the user leaves the login
 * screen mid-login, either by backgrounding the app or by rotating the device.
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

    /** Task id behind the staged-update install, the other connected task on this screen. */
    private val upgradeTaskId: Int
        get() = ReflectionUtils.readField(activity, "TASK_UPGRADE_INSTALL") as Int

    /**
     * What the fake login work has been asked to do, so the tests can follow the pipeline from
     * outside instead of inspecting the view model that runs it.
     */
    private var loginAttempts = 0
    private var loginsInFlight = 0
    private var loginCancellations = 0

    /** The listener handed to the fake login work, so tests can emit progress from outside it. */
    private var progressListener: LoginProgressListener? = null

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
     * The sync dialog is up, the user backgrounds the app, and `DataPullTask` finishing emits
     * `Syncing -> SigningIn`, which dismisses one dialog and shows the next. Only the incoming
     * dialog should survive to the resume.
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
     * postponed show too — otherwise the dialog would appear after the thing that wanted it gone.
     */
    @Test
    fun `blanket dismissal while stopped drops a postponed show`() {
        background()

        activity.showProgressDialog(syncTaskId)
        activity.dismissCurrentProgressDialog()

        foreground()

        assertNull("A dismissed pending show should not appear on resume", currentDialog())
    }

    /**
     * A connected task blocking while stopped is the newer request, so the earlier direct show
     * must not take the resume. A queued dismissal makes this reachable: it skips the blocking
     * branch, which would otherwise clear the postponed show on its way past.
     */
    @Test
    fun `blocking request while stopped supersedes an earlier postponed show`() {
        activity.showProgressDialog(syncTaskId)
        background()

        activity.dismissProgressDialogForTask(syncTaskId)
        activity.showProgressDialog(keyExchangeTaskId)
        activity.startBlockingForTask(upgradeTaskId)

        foreground()

        assertNull(
            "The superseded show should not take the resume",
            currentDialog(),
        )
    }

    /**
     * Two login phases can map to the same task id, so a postponed show can land on a dialog that
     * is already correct. Rebuilding it would throw away the title and message the task has
     * reported since, so the existing dialog is kept.
     */
    @Test
    fun `postponed show for the task already showing keeps that dialog`() {
        activity.showProgressDialog(syncTaskId)
        val original = currentDialog()
        background()

        activity.showProgressDialog(syncTaskId)

        foreground()

        assertSame("The dialog already up for this task should be left alone", original, currentDialog())
    }

    // ======== The STOP button ========

    @Test
    fun `stop button cancels the login pipeline and dismisses the dialog`() {
        startSuspendingLogin()

        clickStopButton()

        assertEquals("STOP should cancel the login pipeline", 1, loginCancellations)
        assertNull("STOP should dismiss the sync dialog", currentDialog())
    }

    /**
     * A cancelled login must not stay latched onto the screen: the next LOGIN press has to start a
     * pipeline of its own, with its own dialog and its own working STOP button.
     */
    @Test
    fun `a cancelled login leaves the screen able to run and stop another`() {
        startSuspendingLogin()
        clickStopButton()

        clickLogin()

        assertEquals("LOGIN should start a second pipeline", 2, loginAttempts)
        assertEquals("The second login should get its own dialog", syncTaskId, currentDialog()?.taskId)

        clickStopButton()

        assertEquals("STOP should cancel the second pipeline too", 2, loginCancellations)
        assertNull("STOP should dismiss the second login's dialog", currentDialog())
    }

    /**
     * `DataPullTask` is an AsyncTask: cancelling it does not stop an `onProgressUpdate` that is
     * already queued on the main looper, so the pipeline can report one more sync percentage after
     * the user has pressed STOP. That late report must not put the dialog back up - nothing will
     * ever dismiss it, since the cancelled pipeline produces no result.
     */
    @Test
    fun `progress arriving after the stop press does not resurrect the dialog`() {
        startSuspendingLogin()
        emitSyncProgress(percent = 40)

        clickStopButton()
        emitSyncProgress(percent = 60)

        assertNull("A cancelled login must not re-show its dialog", currentDialog())
    }

    private fun emitSyncProgress(percent: Int) {
        requireNotNull(progressListener) { "no login in flight" }
            .onProgress(LoginProgress(LoginPhase.Syncing, percent = percent))
        idle()
    }

    /**
     * The phase is the stalest record of what the user is looking at, so a STOP press dismisses
     * whichever login dialog is up rather than the one the phase names. A dialog left behind here
     * would never come down: the cancelled pipeline reports no result.
     */
    @Test
    fun `stop dismisses a login dialog the phase no longer names`() {
        fakeLoginWork { listener ->
            listener.onProgress(LoginProgress(LoginPhase.SigningIn))
            awaitCancellation()
        }
        clickLogin()

        activity.dismissCurrentProgressDialog()
        activity.showProgressDialog(syncTaskId)

        clickStopButton()

        assertEquals("STOP should cancel the login pipeline", 1, loginCancellations)
        assertNull("STOP should dismiss the dialog that is actually showing", currentDialog())
    }

    // ======== Rotation ========

    @Test
    fun `rotation keeps the login running and puts its dialog back`() {
        startSuspendingLogin()
        assertEquals("The sync dialog should be up before rotating", syncTaskId, currentDialog()?.taskId)

        rotate()

        assertEquals("Rotation must not cancel the pipeline", 0, loginCancellations)
        assertEquals("Rotation must not start the login over", 1, loginAttempts)
        assertEquals("The same pipeline should still be running", 1, loginsInFlight)
        assertEquals(
            "The dialog should be rebuilt on the recreated activity",
            syncTaskId,
            currentDialog()?.taskId,
        )
    }

    @Test
    fun `stop button cancels a pipeline that survived rotation`() {
        startSuspendingLogin()

        rotate()
        clickStopButton()

        assertEquals("STOP should cancel the surviving pipeline", 1, loginCancellations)
        assertNull("STOP should dismiss the restored dialog", currentDialog())
    }

    /**
     * A successful login closes the screen, so a result handed to the recreated activity a second
     * time would close that one too — the user's rotation would eat the screen they came back to.
     */
    @Test
    fun `a consumed result is not redelivered after rotation`() {
        fakeLoginWork { successfulLogin() }

        clickLogin()
        assertTrue("A successful login should close the login screen", activity.isFinishing)

        rotate()

        assertFalse("A consumed result must not close the recreated screen too", activity.isFinishing)
        assertNull("And it must not rebuild a dialog", currentDialog())
    }

    @Test
    fun `a finished login leaves no dialog behind on rotation`() {
        fakeLoginWork { listener ->
            listener.onProgress(LoginProgress(LoginPhase.Syncing))
            LoginResult.Failed(LoginError.BadCredentials)
        }

        clickLogin()

        rotate()

        assertNull("A stale phase should not survive the login that reported it", currentDialog())
        assertEquals("No pipeline should still be running", 0, loginsInFlight)
    }

    // ======== Helpers ========

    private fun background() {
        controller.pause().stop()
    }

    private fun foreground() {
        controller.start().resume()
    }

    private fun currentDialog(): CustomProgressDialog? = activity.currentProgressDialog

    /**
     * The plainest success the activity can act on: no PersonalId link check, so it goes straight
     * to setting its result and finishing.
     */
    private fun successfulLogin(): LoginResult.Success =
        LoginResult.Success(
            appId = "test-app",
            username = TEST_USERNAME,
            loginMode = LoginMode.PASSWORD,
            restoreSession = false,
            personalIdManagedLogin = false,
            linkPassword = "",
            postLoginOutcome = PostLoginOutcome(redirectToConnectOpportunityInfo = false),
        )

    /**
     * The one seam these tests need: the login work itself, which would otherwise talk to the
     * server. Everything else — starting a login, stopping it, rotating — goes through the screen.
     */
    private fun fakeLoginWork(work: suspend (LoginProgressListener) -> LoginResult) {
        loginViewModel().performLogin = { _, listener ->
            progressListener = listener
            loginAttempts++
            loginsInFlight++
            try {
                work(listener)
            } catch (cancelled: CancellationException) {
                loginCancellations++
                throw cancelled
            } finally {
                loginsInFlight--
            }
        }
    }

    private fun loginViewModel(): LoginViewModel = ViewModelProvider(activity)[LoginViewModel::class.java]

    /** Starts a login that reaches the sync phase and stays there until something cancels it. */
    private fun startSuspendingLogin() {
        fakeLoginWork { listener ->
            listener.onProgress(LoginProgress(LoginPhase.Syncing))
            awaitCancellation()
        }
        clickLogin()
    }

    private fun clickLogin() {
        activity.findViewById<EditText>(R.id.edit_username).setText(TEST_USERNAME)
        activity.findViewById<EditText>(R.id.edit_password).setText(TEST_PASSWORD)
        activity.findViewById<Button>(R.id.login_button).performClick()
        idle()
    }

    private fun clickStopButton() {
        val dialog = requireNotNull(currentDialog()?.dialog as? AlertDialog) { "sync dialog not showing" }
        val stopButton =
            requireNotNull(dialog.findViewById<Button>(R.id.dialog_cancel_button)) {
                "sync dialog has no STOP button"
            }
        stopButton.performClick()
        idle()
    }

    private fun rotate() {
        controller.recreate()
        activity = controller.get()
        idle()
    }

    /** `postValue` hops through the main looper, so values land only once it has been drained. */
    private fun idle() = ShadowLooper.idleMainLooper()

    companion object {
        private const val TEST_APP_PATH = "jr://resource/commcare-apps/form_nav_tests/profile.ccpr"
        private const val TEST_USERNAME = "test-user"
        private const val TEST_PASSWORD = "test-password"
    }
}
