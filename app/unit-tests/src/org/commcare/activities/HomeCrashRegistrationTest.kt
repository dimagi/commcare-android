package org.commcare.activities

import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockkStatic
import io.mockk.verify
import org.commcare.appupdate.AppUpdateControllerFactory
import org.commcare.utils.CrashUtil
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * Crash-reporting app data must be registered before `onCreateSessionSafe` does work that can crash,
 * or those crashes reach Crashlytics without the app's identifying keys.
 */
class HomeCrashRegistrationTest : BaseHomeScreenActivityTest() {
    @Test
    fun `app data is registered before startup work in onCreateSessionSafe crashes`() {
        mockkStatic(CrashUtil::class)
        every { CrashUtil.registerAppData() } just Runs
        mockkStatic(AppUpdateControllerFactory::class)
        every { AppUpdateControllerFactory.create(any(), any()) } throws IllegalStateException("startup crash")

        assertThrows(IllegalStateException::class.java) { buildHome() }

        verify(exactly = 1) { CrashUtil.registerAppData() }
    }
}
