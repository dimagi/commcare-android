package org.commcare.home

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.commcare.CommCareTestApplication
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@Config(application = CommCareTestApplication::class)
@RunWith(AndroidJUnit4::class)
class ServiceBackedSessionTest {
    private val session = ServiceBackedSession()

    @Test
    fun `an in-app update is allowed when no session is bound`() {
        assertTrue(session.shouldShowInAppUpdate())
    }

    @Test
    fun `hiding the in-app update with no session bound does not throw`() {
        session.hideInAppUpdate()
    }
}
