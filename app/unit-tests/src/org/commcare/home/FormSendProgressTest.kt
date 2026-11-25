package org.commcare.home

import org.commcare.sync.FormSubmissionHelper.SUBMISSION_BEGIN
import org.commcare.sync.FormSubmissionHelper.SUBMISSION_DONE
import org.commcare.sync.FormSubmissionHelper.SUBMISSION_NOTIFY
import org.commcare.sync.FormSubmissionHelper.SUBMISSION_START
import org.commcare.sync.FormSubmissionHelper.SUBMISSION_SUCCESS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FormSendProgressTest {
    private val progress = FormSendProgress()

    private fun update(vararg values: Long) = progress.onUpdate(values.toTypedArray())

    @Test
    fun `a send starts at none of its forms`() {
        assertEquals(FormSendProgress.State(0, 4, 0), update(SUBMISSION_BEGIN, 4))
    }

    @Test
    fun `starting a form counts the forms before it as sent`() {
        update(SUBMISSION_BEGIN, 4)

        assertEquals(FormSendProgress.State(1, 4, 25), update(SUBMISSION_START, 1, 1000))
    }

    @Test
    fun `bytes sent move the percent within a form`() {
        update(SUBMISSION_BEGIN, 4)
        update(SUBMISSION_START, 1, 1000)

        assertEquals(FormSendProgress.State(1, 4, 37), update(SUBMISSION_NOTIFY, 1, 500))
    }

    @Test
    fun `a successful send ends at all its forms`() {
        update(SUBMISSION_BEGIN, 3)

        assertEquals(FormSendProgress.State(3, 3, 100), update(SUBMISSION_DONE, SUBMISSION_SUCCESS))
    }

    @Test
    fun `a failed send leaves the progress where it was`() {
        update(SUBMISSION_BEGIN, 3)

        assertNull(update(SUBMISSION_DONE, 0))
    }

    @Test
    fun `updates before the send begins don't move it`() {
        assertNull(update(SUBMISSION_START, 0, 1000))
    }

    @Test
    fun `other updates don't move it`() {
        update(SUBMISSION_BEGIN, 3)

        assertNull(update(8))
        assertNull(progress.onUpdate(emptyArray()))
    }
}
