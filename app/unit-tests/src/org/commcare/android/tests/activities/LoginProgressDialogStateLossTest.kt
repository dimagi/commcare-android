package org.commcare.android.tests.activities

import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.commcare.CommCareTestApplication
import org.commcare.activities.LoginActivity
import org.commcare.android.util.TestAppInstaller
import org.commcare.tasks.DataPullTask
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config

@Config(application = CommCareTestApplication::class)
@RunWith(AndroidJUnit4::class)
class LoginProgressDialogStateLossTest {
    @Before
    fun setUp() {
        (CommCareTestApplication.instance() as CommCareTestApplication).initWorkManager()
        TestAppInstaller.installApp(TEST_APP_PATH)
    }

    @Test
    fun `showing a progress dialog after the activity is stopped does not crash`() {
        val controller =
            Robolectric
                .buildActivity(
                    LoginActivity::class.java,
                    Intent(ApplicationProvider.getApplicationContext(), LoginActivity::class.java),
                ).setup()
        val activity = controller.get()

        controller.pause().stop()

        activity.showProgressDialog(DataPullTask.DATA_PULL_TASK_ID)

        assertNull(activity.currentProgressDialog)
    }

    @Test
    fun `a progress dialog requested while stopped is shown once the activity resumes`() {
        val controller =
            Robolectric
                .buildActivity(
                    LoginActivity::class.java,
                    Intent(ApplicationProvider.getApplicationContext(), LoginActivity::class.java),
                ).setup()
        val activity = controller.get()

        controller.pause().stop()
        activity.showProgressDialog(DataPullTask.DATA_PULL_TASK_ID)

        controller.start().resume().postResume()

        assertNotNull(activity.currentProgressDialog)
    }

    companion object {
        private const val TEST_APP_PATH = "jr://resource/commcare-apps/form_nav_tests/profile.ccpr"
    }
}
