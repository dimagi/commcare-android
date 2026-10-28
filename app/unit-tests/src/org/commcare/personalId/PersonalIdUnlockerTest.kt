package org.commcare.personalId

import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.commcare.CommCareTestApplication
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

@RunWith(AndroidJUnit4::class)
@Config(application = CommCareTestApplication::class)
class PersonalIdUnlockerTest {
    @Before
    fun setUp() {
        PersonalIdUnlocker.resetSession()
    }

    @Test
    fun `SESSION_WITH_TIME_THRESHOLD requires unlock when session has never been unlocked`() {
        assertTrue(PersonalIdUnlocker.requiresUnlockForSession())
    }

    @Test
    fun `SESSION_WITH_TIME_THRESHOLD bypasses unlock when unlocked within threshold`() {
        PersonalIdUnlocker.lastUnlockTime = SystemClock.elapsedRealtime()
        assertFalse(PersonalIdUnlocker.requiresUnlockForSession())
    }

    @Test
    fun `SESSION_WITH_TIME_THRESHOLD requires unlock when last unlock at exactly the threshold`() {
        val tenMinutesAgo = SystemClock.elapsedRealtime() - 10.minutes.inWholeMilliseconds
        PersonalIdUnlocker.lastUnlockTime = tenMinutesAgo
        assertTrue(PersonalIdUnlocker.requiresUnlockForSession())
    }

    @Test
    fun `SESSION_WITH_TIME_THRESHOLD requires unlock when last unlock exceeded threshold`() {
        val elevenMinutesAgo = SystemClock.elapsedRealtime() - 11.minutes.inWholeMilliseconds
        PersonalIdUnlocker.lastUnlockTime = elevenMinutesAgo
        assertTrue(PersonalIdUnlocker.requiresUnlockForSession())
    }

    @Test
    fun `SESSION_WITH_TIME_THRESHOLD bypasses unlock when last unlock is just within threshold boundary`() {
        val nineMinutesAgo = SystemClock.elapsedRealtime() - 10.minutes.inWholeMilliseconds + 1
        PersonalIdUnlocker.lastUnlockTime = nineMinutesAgo
        assertFalse(PersonalIdUnlocker.requiresUnlockForSession())
    }

    @Test
    fun `resetSession clears lastUnlockTime`() {
        PersonalIdUnlocker.lastUnlockTime = SystemClock.elapsedRealtime()
        PersonalIdUnlocker.resetSession()
        assertTrue(PersonalIdUnlocker.requiresUnlockForSession())
    }
}
