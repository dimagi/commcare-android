package org.commcare.views.widgets

import android.content.Intent
import android.os.Build
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.commcare.CommCareTestApplication
import org.commcare.preferences.DeveloperPreferences
import org.commcare.preferences.PrefValues
import org.commcare.views.widgets.AudioRecordingService.RecordingState
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSystemClock
import java.time.Duration

/**
 * Covers the recording state the service reports to a UI that rebinds to it, in particular the
 * elapsed time, which has to exclude any stretch spent paused or stopped however long ago that was.
 */
@Config(application = CommCareTestApplication::class, sdk = [Build.VERSION_CODES.N])
@RunWith(AndroidJUnit4::class)
class AudioRecordingServiceTest {

    @Before
    fun stubQualityProfile() {
        mockkStatic(DeveloperPreferences::class)
        every { DeveloperPreferences.getAudioQualityProfile() } returns PrefValues.AUDIO_QUALITY_DEFAULT
    }

    @After
    fun unstubQualityProfile() {
        unmockkStatic(DeveloperPreferences::class)
    }

    private fun startCommandIntent() =
        Intent(ApplicationProvider.getApplicationContext(), AudioRecordingService::class.java).apply {
            putExtra(AudioRecordingService.RECORDING_FILENAME_EXTRA_KEY, RECORDING_FILE_NAME)
            putExtra(AudioRecordingService.PAUSE_SUPPORTED_EXTRA_KEY, true)
        }

    private fun startedService(intent: Intent = startCommandIntent()): AudioRecordingService =
        Robolectric.buildService(AudioRecordingService::class.java, intent)
            .create()
            .startCommand(0, 0)
            .get()

    /** What a Chronometer given this base would display, in ms. */
    private fun AudioRecordingService.elapsed(): Long =
        SystemClock.elapsedRealtime() - chronometerBase

    private fun advance(seconds: Long) {
        ShadowSystemClock.advanceBy(Duration.ofSeconds(seconds))
    }

    @Test
    fun `elapsed time tracks an uninterrupted recording`() {
        val service = startedService()
        assertEquals(RecordingState.RECORDING, service.state)

        advance(5)
        assertEquals(5000, service.elapsed())
    }

    @Test
    fun `elapsed time freezes while paused`() {
        val service = startedService()
        advance(5)
        service.pauseRecording()
        assertEquals(RecordingState.PAUSED, service.state)

        advance(10)
        // Still 5s: the pause does not count towards the recording.
        assertEquals(5000, service.elapsed())
    }

    @Test
    fun `elapsed time excludes the pause after resuming`() {
        val service = startedService()
        advance(5)
        service.pauseRecording()
        advance(10)
        service.resumeRecording()
        assertEquals(RecordingState.RECORDING, service.state)

        advance(3)
        assertEquals(8000, service.elapsed())
    }

    @Test
    fun `elapsed time freezes after stopping`() {
        val service = startedService()
        advance(5)
        service.stopRecording()
        assertEquals(RecordingState.STOPPED, service.state)

        advance(10)
        // A UI binding well after the stop must still see the recording's real length.
        assertEquals(5000, service.elapsed())
    }

    @Test
    fun `stopping while paused keeps the pause excluded`() {
        val service = startedService()
        advance(5)
        service.pauseRecording()
        advance(10)
        service.stopRecording()

        advance(10)
        // Re-freezing on stop would have counted the 10s pause, reporting 15s for a 5s recording.
        assertEquals(5000, service.elapsed())
    }

    @Test
    fun `repeated stops are ignored`() {
        val service = startedService()
        advance(5)
        service.stopRecording()
        advance(10)
        service.stopRecording()

        assertEquals(RecordingState.STOPPED, service.state)
        assertEquals(5000, service.elapsed())
    }

    @Test
    fun `a second start does not restart the recording`() {
        val intent = startCommandIntent()
        val service = startedService(intent)

        advance(5)
        service.onStartCommand(intent, 0, 0)

        // Elapsed time is unchanged, i.e. the recording was not restarted from zero.
        assertEquals(5000, service.elapsed())
        assertEquals(RecordingState.RECORDING, service.state)
    }

    @Test
    fun `file name is reported for a rebinding UI`() {
        val service = startedService()
        assertEquals(RECORDING_FILE_NAME, service.fileName)
    }

    companion object {
        private const val RECORDING_FILE_NAME = "/tmp/test-recording.m4a"
    }
}
