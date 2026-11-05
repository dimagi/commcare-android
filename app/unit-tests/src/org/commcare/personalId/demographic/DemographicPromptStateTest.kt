package org.commcare.personalId.demographic

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.commcare.CommCareTestApplication
import org.commcare.android.database.connect.models.ConnectUserRecord
import org.commcare.android.util.ConnectTestUtils
import org.commcare.connect.database.ConnectDatabaseHelper
import org.commcare.connect.database.ConnectUserDatabaseUtil
import org.commcare.personalId.PersonalIdUserPreferences
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.time.Duration.Companion.hours

@Config(application = CommCareTestApplication::class)
@RunWith(AndroidJUnit4::class)
class DemographicPromptStateTest {
    private val nowMillis = 1_700_000_000_000L
    private val state = DemographicPromptState { nowMillis }

    @Before
    fun setUp() {
        PersonalIdUserPreferences.clear()
        ConnectTestUtils.signInToPersonalId()
    }

    @After
    fun tearDown() {
        ConnectDatabaseHelper.teardown()
        PersonalIdUserPreferences.clear()
    }

    @Test
    fun `shouldShow is true for an incomplete profile that has never been prompted`() {
        assertTrue(state.shouldShow(state.profileCompletion()))
    }

    @Test
    fun `shouldShow is false once every profile field is filled`() {
        updateUser { fillAllProfileFields() }

        assertFalse(state.shouldShow(state.profileCompletion()))
    }

    @Test
    fun `shouldShow is true when only email is missing`() {
        updateUser {
            fillAllProfileFields()
            email = null
        }

        assertTrue(state.shouldShow(state.profileCompletion()))
    }

    @Test
    fun `shouldShow is false while the snooze has not elapsed`() {
        PersonalIdUserPreferences.setDemographicPromptNextShowAt(nowMillis + 1)

        assertFalse(state.shouldShow(state.profileCompletion()))
    }

    @Test
    fun `shouldShow is true once the snooze time is reached`() {
        PersonalIdUserPreferences.setDemographicPromptNextShowAt(nowMillis)

        assertTrue(state.shouldShow(state.profileCompletion()))
    }

    @Test
    fun `stage is plain for the first two prompts`() {
        assertEquals(BannerStage.PLAIN, stageAfterPrompts(0))
        assertEquals(BannerStage.PLAIN, stageAfterPrompts(1))
    }

    @Test
    fun `stage is warning for the third prompt`() {
        assertEquals(BannerStage.WARNING, stageAfterPrompts(2))
    }

    @Test
    fun `stage is final from the fourth prompt on`() {
        assertEquals(BannerStage.FINAL, stageAfterPrompts(3))
        assertEquals(BannerStage.FINAL, stageAfterPrompts(10))
    }

    @Test
    fun `recordDecline counts the prompt and snoozes it for 24 hours`() {
        state.recordDecline()

        assertPromptRecordedAndSnoozed()
    }

    @Test
    fun `recordCompleteTapped counts the prompt and snoozes it for 24 hours`() {
        state.recordCompleteTapped()

        assertPromptRecordedAndSnoozed()
    }

    @Test
    fun `profileCompletion counts only filled fields out of all seven`() {
        updateUser {
            email = "user@example.org"
            gender = " "
            languages = ""
        }

        val completion = state.profileCompletion()
        assertEquals(1, completion.filledFieldCount)
        assertEquals(7, completion.totalFieldCount)
    }

    private fun stageAfterPrompts(promptCount: Int): BannerStage {
        PersonalIdUserPreferences.setDemographicPromptCount(promptCount)
        return state.stage()
    }

    private fun assertPromptRecordedAndSnoozed() {
        assertEquals(1, state.promptCount)
        assertEquals(nowMillis + 24.hours.inWholeMilliseconds, state.nextShowAt)
        assertFalse(state.shouldShow(state.profileCompletion()))
    }

    private fun updateUser(update: ConnectUserRecord.() -> Unit) {
        val user = ConnectUserDatabaseUtil.getUser()
        user.update()
        ConnectUserDatabaseUtil.storeUser(user)
    }

    private fun ConnectUserRecord.fillAllProfileFields() {
        email = "user@example.org"
        gender = "female"
        dateOfBirth = "1990-01-01"
        languages = "en"
        education = "secondary"
        chwExperience = "2"
        techProficiency = "high"
    }
}
