package org.commcare.android.tests.processing

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import org.commcare.CommCareApplication
import org.commcare.CommCareTestApplication
import org.commcare.android.database.user.models.FormRecord
import org.commcare.android.util.SavedFormLoader
import org.commcare.android.util.TestAppInstaller
import org.commcare.google.services.analytics.AnalyticsParamValue
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.util.Date
import java.util.concurrent.TimeUnit

@Config(application = CommCareTestApplication::class)
@RunWith(AndroidJUnit4::class)
class SessionEndAnalyticsTest {
    @Before
    fun setUp() {
        mockkStatic(FirebaseAnalyticsUtil::class)
        every { FirebaseAnalyticsUtil.reportSessionEnd(any(), any(), any()) } returns Unit
        TestAppInstaller.installAppAndLogin(
            "jr://resource/commcare-apps/archive_form_tests/profile.ccpr",
            "test",
            "123",
        )
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `logging out reports the unsent forms and the oldest one's age`() {
        SavedFormLoader.loadFormsFromPayload(
            "/commcare-apps/archive_form_tests/unsent_form_payload.xml",
            FormRecord.STATUS_UNSENT,
        )
        SavedFormLoader.loadFormsFromPayload(
            "/commcare-apps/archive_form_tests/saved_form_payload.xml",
            FormRecord.STATUS_SAVED,
        )
        val formStorage = CommCareApplication.instance().getUserStorage(FormRecord::class.java)
        formStorage.forEachIndexed { index, form ->
            val hoursAgo = if (index == 0) 3L else 1L
            form.setLastModified(Date(System.currentTimeMillis() - TimeUnit.HOURS.toMillis(hoursAgo)))
            formStorage.write(form)
        }
        val ageMinutes = slot<Long>()

        CommCareApplication.instance().closeUserSession()

        verify(exactly = 1) {
            FirebaseAnalyticsUtil.reportSessionEnd(
                AnalyticsParamValue.SESSION_END_REASON_LOGOUT,
                5,
                capture(ageMinutes),
            )
        }
        assertTrue("The oldest unsent form is three hours old", ageMinutes.captured in 180L..181L)
    }

    @Test
    fun `an expired session reports expiry`() {
        CommCareApplication.instance().expireUserSession()

        verify(exactly = 1) {
            FirebaseAnalyticsUtil.reportSessionEnd(AnalyticsParamValue.SESSION_END_REASON_EXPIRY, 0, any())
        }
    }

    @Test
    fun `closing a session that is already closed reports nothing more`() {
        CommCareApplication.instance().closeUserSession()
        CommCareApplication.instance().closeUserSession()

        verify(exactly = 1) { FirebaseAnalyticsUtil.reportSessionEnd(any(), any(), any()) }
    }
}
