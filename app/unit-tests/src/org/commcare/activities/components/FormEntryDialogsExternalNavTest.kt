package org.commcare.activities.components

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.commcare.CommCareTestApplication
import org.commcare.activities.FormEntryActivity
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.robolectric.annotation.Config

@Config(application = CommCareTestApplication::class)
@RunWith(AndroidJUnit4::class)
class FormEntryDialogsExternalNavTest {
    private fun newPendingNav(): Intent {
        val ctx = CommCareTestApplication.instance()
        return Intent(ctx, FormEntryActivity::class.java).putExtra("marker", "x")
    }

    @Test
    fun discardChoice_runsDiscardAndStartsPendingNav() {
        val activity = mock(FormEntryActivity::class.java)
        val pendingNav = newPendingNav()

        // We invoke the helper exposed for testing to assert behavior.
        FormEntryDialogs.invokeDiscardListenerForTest(activity, pendingNav)

        verify(activity, times(1)).discardChangesAndExit()
        val captor = ArgumentCaptor.forClass(Intent::class.java)
        verify(activity, times(1)).startActivity(captor.capture())
        assertEquals("x", captor.value.getStringExtra("marker"))
    }

    @Test
    fun saveChoice_stashesPendingNavThenSaves() {
        val activity = mock(FormEntryActivity::class.java)
        val pendingNav = newPendingNav()

        FormEntryDialogs.invokeSaveListenerForTest(activity, pendingNav)

        verify(activity, times(1)).setPendingNavAfterSave(pendingNav)
        verify(activity, times(1)).saveFormToDisk(FormEntryConstants.EXIT)
        verify(activity, never()).startActivity(org.mockito.kotlin.any())
    }

    @Test
    fun stayChoice_doesNothingToActivity() {
        val activity = mock(FormEntryActivity::class.java)
        val pendingNav = newPendingNav()

        FormEntryDialogs.invokeStayListenerForTest(activity, pendingNav)

        verify(activity, never()).discardChangesAndExit()
        verify(activity, never()).saveFormToDisk(org.mockito.kotlin.any())
        verify(activity, never()).startActivity(org.mockito.kotlin.any())
    }
}
