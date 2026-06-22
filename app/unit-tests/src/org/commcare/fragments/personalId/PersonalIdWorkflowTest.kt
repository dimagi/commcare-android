package org.commcare.fragments.personalId

import org.junit.Assert.assertEquals
import org.junit.Test

class PersonalIdWorkflowTest {
    // --- fromEmailWorkFlow ---

    @Test
    fun `fromEmailWorkFlow maps EXISTING_USER to USER_PROMPT`() {
        assertEquals(
            PersonalIdWorkflow.USER_PROMPT,
            PersonalIdWorkflow.fromEmailWorkFlow(EmailWorkFlow.EXISTING_USER),
        )
    }

    @Test
    fun `fromEmailWorkFlow maps REGISTRATION to CONFIGURATION`() {
        assertEquals(
            PersonalIdWorkflow.CONFIGURATION,
            PersonalIdWorkflow.fromEmailWorkFlow(EmailWorkFlow.REGISTRATION),
        )
    }

    @Test
    fun `fromEmailWorkFlow maps RECOVERY to CONFIGURATION`() {
        assertEquals(
            PersonalIdWorkflow.CONFIGURATION,
            PersonalIdWorkflow.fromEmailWorkFlow(EmailWorkFlow.RECOVERY),
        )
    }
}
