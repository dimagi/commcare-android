package org.commcare.connect

import android.app.Activity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectAppLaunchUiControllerTest {
    @Test
    fun `redirects to job status on a successful result carrying the flag`() {
        assertTrue(shouldRedirectToJobStatus(Activity.RESULT_OK, redirectExtra = true))
    }

    @Test
    fun `does not redirect when the flag is absent`() {
        assertFalse(shouldRedirectToJobStatus(Activity.RESULT_OK, redirectExtra = false))
    }

    @Test
    fun `does not redirect when the result was not ok`() {
        assertFalse(shouldRedirectToJobStatus(Activity.RESULT_CANCELED, redirectExtra = true))
    }
}
