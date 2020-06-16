package org.commcare.tests

import android.content.Context
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.*
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.commcare.CommCareApplication
import org.commcare.sync.FormSubmissionHelper
import org.commcare.sync.FormSubmissionWorker
import org.commcare.utils.FormUploadResult
import org.hamcrest.CoreMatchers.`is`
import org.junit.Assert.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * @author $|-|!˅@M
 */
@RunWith(AndroidJUnit4::class)
class FormSubmissionWorkerTest {

    @MockK
    lateinit var formSubmissionHelper: FormSubmissionHelper
    private lateinit var context: Context
    private lateinit var inputData: Data

    @Before
    fun setup() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        inputData = Data.Builder().putBoolean("TESTING", true).build()
        MockKAnnotations.init(this)
        FormSubmissionWorker.setTestData(formSubmissionHelper)
    }

    @Test
    fun testFormSubmissionResults() {
        // This probably should be in unit-test?
        val worker =
                TestListenableWorkerBuilder<FormSubmissionWorker>(context)
                        .setInputData(inputData)
                        .build() as FormSubmissionWorker

        every { formSubmissionHelper.uploadForms() } returns FormUploadResult.FULL_SUCCESS
        runBlocking {
            val result = worker.doWork()
            assertThat(result, `is`(ListenableWorker.Result.success()))
        }

        every { formSubmissionHelper.uploadForms() } returns FormUploadResult.CAPTIVE_PORTAL
        runBlocking {
            val result = worker.doWork()
            assertThat(result, `is`(ListenableWorker.Result.failure()))
        }

        every { formSubmissionHelper.uploadForms() } returns FormUploadResult.RATE_LIMITED
        runBlocking {
            val result = worker.doWork()
            assertThat(result, `is`(ListenableWorker.Result.retry()))
        }
    }

    @Test
    fun testPeriodicFormSubmission() {
        val request =
                CommCareApplication.instance()
                        .formSubmissionPeriodicRequest()
                        .setInputData(inputData)
                        .build()
        val config = Configuration.Builder()
                // Set log level to Log.DEBUG to make it easier to debug
                .setMinimumLoggingLevel(Log.DEBUG)
                // Use a SynchronousExecutor here to make it easier to write tests
                .setExecutor(SynchronousExecutor())
                .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, config)
        every { formSubmissionHelper.uploadForms() } returns FormUploadResult.FULL_SUCCESS
        val workManager = WorkManager.getInstance(context)
        val testDriver = WorkManagerTestInitHelper.getTestDriver(context)
        val operation = workManager.enqueue(request)
        testDriver!!.setAllConstraintsMet(request.id)
        testDriver.setPeriodDelayMet(request.id)
        operation.result.get()
        val workInfo = workManager.getWorkInfoById(request.id).get()
        verify(exactly = 1) { formSubmissionHelper.uploadForms() }
        assertThat(workInfo.state, `is`(WorkInfo.State.ENQUEUED))
    }

}