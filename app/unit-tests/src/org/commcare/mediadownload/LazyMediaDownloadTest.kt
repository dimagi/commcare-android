package org.commcare.mediadownload

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import io.mockk.every
import io.mockk.mockkObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.commcare.CommCareApplication
import org.commcare.CommCareTestApplication
import org.commcare.android.mocks.ModernHttpRequesterMock
import org.commcare.android.util.TestAppInstaller
import org.commcare.android.util.UpdateUtils
import org.commcare.engine.resource.AppInstallStatus
import org.commcare.network.RequestStats
import org.commcare.resources.model.InstallRequestSource
import org.commcare.utils.FileUtil
import org.commcare.utils.TimeProvider
import org.hamcrest.CoreMatchers
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.util.*

@Config(application = CommCareTestApplication::class)
@RunWith(AndroidJUnit4::class)
class LazyMediaDownloadTest {

    private lateinit var context: Context

    companion object {
        const val REF_BASE_DIR = "jr://resource/commcare-apps/update_tests/"
        const val LAZY_MEDIA_REF = "jr://file/commcare/image/data/question1.jpg"
    }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun testLazyMediaIsDownloadedOnInstall() {
        TestAppInstaller.installAppAndLogin(UpdateUtils.buildResourceRef(REF_BASE_DIR,
                "valid_update_with_lazy_multimedia_present", "profile.ccpr"),
                "test", "123")
        assertTrue(FileUtil.referenceFileExists(LAZY_MEDIA_REF))
    }

    @Test
    fun testLazyMediaDownloadInBackground() {
        updateToAnAppWithLazyMedia()
        assertFalse(FileUtil.referenceFileExists(LAZY_MEDIA_REF))

        // unknown failure
        runAndCheckForDownloadWorkerResult(ListenableWorker.Result.retry())

        // network failure
        ModernHttpRequesterMock.setRequestPayloads(arrayOf("null", "null", "null", "null"))
        runAndCheckForDownloadWorkerResult(ListenableWorker.Result.retry())

        // success
        mockSuccessfulResourceDownload()
        runAndCheckForDownloadWorkerResult(ListenableWorker.Result.success())
    }

    @Test
    fun testLazyMediaDownloadInForeground() {
        updateToAnAppWithLazyMedia()
        assertFalse(FileUtil.referenceFileExists(LAZY_MEDIA_REF))
        MissingMediaDownloadHelper.requestMediaDownload(LAZY_MEDIA_REF, Dispatchers.Main, object : MissingMediaDownloadListener {
            override fun onComplete(result: MissingMediaDownloadResult) {
                assertTrue(result is MissingMediaDownloadResult.Error)
                assertFalse(FileUtil.referenceFileExists(LAZY_MEDIA_REF))
            }
        })

        var requestAge = mockTimeAndGetRequestAge(
                100,
                InstallRequestSource.FOREGROUND_LAZY_RESOURCE)
        assertEquals(RequestStats.RequestAge.LT_120, requestAge)

        mockSuccessfulResourceDownload()
        MissingMediaDownloadHelper.requestMediaDownload(LAZY_MEDIA_REF, Dispatchers.Main, object : MissingMediaDownloadListener {
            override fun onComplete(result: MissingMediaDownloadResult) {
                assertTrue(result is MissingMediaDownloadResult.Success)
                assertTrue(FileUtil.referenceFileExists(LAZY_MEDIA_REF))
            }
        })

        requestAge = mockTimeAndGetRequestAge(
                190,
                InstallRequestSource.FOREGROUND_LAZY_RESOURCE)
        assertEquals(RequestStats.RequestAge.LT_10, requestAge)
    }

    private fun runAndCheckForDownloadWorkerResult(expectedResult: ListenableWorker.Result) {
        runBlocking {
            val worker = TestListenableWorkerBuilder<MissingMediaDownloadWorker>(context).build()
            var result = worker.doWork()
            assertThat(result, CoreMatchers.`is`(expectedResult))

            val requestAge = mockTimeAndGetRequestAge(
                    80,
                    InstallRequestSource.BACKGROUND_LAZY_RESOURCE)

            if (result is ListenableWorker.Result.Success) {
                assertTrue(FileUtil.referenceFileExists(LAZY_MEDIA_REF))
                assertEquals(RequestStats.RequestAge.LT_10, requestAge)
            } else {
                assertFalse(FileUtil.referenceFileExists(LAZY_MEDIA_REF))
                assertEquals(RequestStats.RequestAge.LT_90, requestAge)
            }
        }
    }

    private fun mockTimeAndGetRequestAge(daysInFuture: Int, installRequestSource: InstallRequestSource): RequestStats.RequestAge {
        mockkObject(TimeProvider)
        every { TimeProvider.getCurrentDate() } returns Date(Date().time + daysInFuture * 24 * 60 * 60 * 1000L)
        return RequestStats.getRequestAge(
                (ApplicationProvider.getApplicationContext() as CommCareApplication).currentApp,
                installRequestSource)
    }

    private fun mockSuccessfulResourceDownload() {
        val mediaRef = UpdateUtils.buildResourceRef(
                REF_BASE_DIR,
                "valid_update_with_lazy_multimedia_present",
                "commcare/image/data/question1.jpg")
        ModernHttpRequesterMock.setRequestPayloads(arrayOf(mediaRef))
        ModernHttpRequesterMock.setResponseCodes(arrayOf(200))
    }

    // install and app and update to a version with a lazy resource
    private fun updateToAnAppWithLazyMedia() {
        TestAppInstaller.installAppAndLogin(
                UpdateUtils.buildResourceRef(REF_BASE_DIR, "base_app", "profile.ccpr"),
                "test", "123")
        val profileRef = UpdateUtils.buildResourceRef(REF_BASE_DIR,
                "valid_update_with_lazy_multimedia_present", "profile.ccpr")
        UpdateUtils.installUpdate(profileRef,
                AppInstallStatus.UpdateStaged,
                AppInstallStatus.Installed)
    }
}