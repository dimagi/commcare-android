package org.commcare.personalId

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

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
        PersonalIdUnlocker.lastUnlockTime = System.currentTimeMillis()
        assertFalse(PersonalIdUnlocker.requiresUnlockForSession())
    }

    @Test
    fun `SESSION_WITH_TIME_THRESHOLD requires unlock when last unlock exceeded threshold`() {
        val elevenMinutesAgo = System.currentTimeMillis() - (11 * 60 * 1000L)
        PersonalIdUnlocker.lastUnlockTime = elevenMinutesAgo
        assertTrue(PersonalIdUnlocker.requiresUnlockForSession())
    }

    @Test
    fun `SESSION_WITH_TIME_THRESHOLD bypasses unlock when last unlock is within threshold boundary`() {
        val nineMinutesAgo = System.currentTimeMillis() - (9 * 60 * 1000L)
        PersonalIdUnlocker.lastUnlockTime = nineMinutesAgo
        assertFalse(PersonalIdUnlocker.requiresUnlockForSession())
    }

    @Test
    fun `resetSession clears lastUnlockTime`() {
        PersonalIdUnlocker.lastUnlockTime = System.currentTimeMillis()
        PersonalIdUnlocker.resetSession()
        assertTrue(PersonalIdUnlocker.requiresUnlockForSession())
    }
}
