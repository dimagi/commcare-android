package org.commcare.views.widgets

import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.commcare.CommCareTestApplication
import org.commcare.android.util.TestAppInstaller
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import java.io.File

/**
 * A configuration change rebuilds the recording dialog while the service keeps recording. The rebuilt
 * dialog has to pick that recording back up rather than start a new one, so that saving hands back
 * the file that was actually recorded.
 */
@Config(application = CommCareTestApplication::class, sdk = [Build.VERSION_CODES.N])
@RunWith(AndroidJUnit4::class)
class RecordingFragmentRecreationTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var service: AudioRecordingService

    // Doesn't exist yet, so the dialog starts a new recording at this path rather than reloading one
    private lateinit var recordingFilePath: String

    @Before
    fun setUp() {
        recordingFilePath = File(tempFolder.root, "recording.mp3").absolutePath

        // The dialog extends the user session once it binds, so it needs a logged-in user
        TestAppInstaller.installAppAndLogin(TEST_APP_PATH, TEST_USER, TEST_PASSWORD)

        service =
            Robolectric
                .buildService(AudioRecordingService::class.java, startIntent())
                .create()
                .startCommand(0, 0)
                .get()
        shadowOf(ApplicationProvider.getApplicationContext<CommCareTestApplication>())
            .setComponentNameAndServiceForBindService(
                ComponentName(ApplicationProvider.getApplicationContext(), AudioRecordingService::class.java),
                service.onBind(startIntent()),
            )
    }

    @Test
    fun `a dialog recreated mid-recording does not start a second recording`() {
        val controller = showRecordingDialog()
        assertTrue(drainNewRecordingIntents() > 0)

        controller.recreate()
        ShadowLooper.idleMainLooper()

        assertNotNull(controller.get().supportFragmentManager.findFragmentByTag(FRAGMENT_TAG))
        assertEquals(0, drainNewRecordingIntents())
    }

    @Test
    fun `saving after recreation hands back the file that was recorded`() {
        val controller = showRecordingDialog()
        controller.recreate()
        ShadowLooper.idleMainLooper()

        var savedPath: String? = null
        val activity = controller.get()
        activity.supportFragmentManager.setFragmentResultListener(RESULT_KEY, activity) { _, result ->
            savedPath = result.getString(RecordingFragment.RESULT_AUDIO_FILE_PATH_KEY)
        }

        // Pressing Save on the recording notification
        service.onStartCommand(
            Intent(activity, AudioRecordingService::class.java)
                .setAction(AudioRecordingService.ACTION_SAVE_RECORDING),
            0,
            0,
        )
        ShadowLooper.idleMainLooper()

        assertEquals(recordingFilePath, savedPath)
    }

    private fun showRecordingDialog(): ActivityController<FragmentActivity> {
        val controller = Robolectric.buildActivity(FragmentActivity::class.java).setup()
        RecordingFragment()
            .apply {
                arguments =
                    Bundle().apply {
                        putString(RecordingFragment.AUDIO_FILE_PATH_ARG_KEY, recordingFilePath)
                        putString(RecordingFragment.RESULT_REQUEST_KEY_ARG_KEY, RESULT_KEY)
                    }
            }.show(controller.get().supportFragmentManager, FRAGMENT_TAG)
        ShadowLooper.idleMainLooper()
        return controller
    }

    /**
     * Robolectric records bind intents alongside start intents. Starting a new recording sends the file
     * name with both its start and its bind; rebinding to a running recording sends neither.
     */
    private fun drainNewRecordingIntents(): Int {
        val app = shadowOf(ApplicationProvider.getApplicationContext<CommCareTestApplication>())
        return generateSequence { app.nextStartedService }
            .count { it.hasExtra(AudioRecordingService.RECORDING_FILENAME_EXTRA_KEY) }
    }

    private fun startIntent() =
        Intent(ApplicationProvider.getApplicationContext(), AudioRecordingService::class.java)
            .putExtra(AudioRecordingService.RECORDING_FILENAME_EXTRA_KEY, recordingFilePath)

    companion object {
        private const val TEST_APP_PATH = "jr://resource/commcare-apps/form_nav_tests/profile.ccpr"
        private const val TEST_USER = "test"
        private const val TEST_PASSWORD = "123"
        private const val FRAGMENT_TAG = "recorder"
        private const val RESULT_KEY = "recording-result"
    }
}
