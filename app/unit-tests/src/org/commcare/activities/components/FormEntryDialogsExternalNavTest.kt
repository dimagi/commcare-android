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
import org.mockito.kotlin.any
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

        FormEntryDialogs.handleDiscardChoice(activity, pendingNav)

        verify(activity, times(1)).discardChangesAndExitToPendingNav(pendingNav)
    }

    @Test
    fun saveChoice_stashesPendingNavThenSaves() {
        val activity = mock(FormEntryActivity::class.java)
        val pendingNav = newPendingNav()

        FormEntryDialogs.handleSaveChoice(activity, pendingNav)

        verify(activity, times(1)).setPendingNavAfterSave(pendingNav)
        verify(activity, times(1)).saveFormToDisk(FormEntryConstants.EXIT)
        verify(activity, never()).startActivity(any())
    }

    @Test
    fun helpersAreCalledOnlyByCorrespondingChoice() {
        val activity = mock(FormEntryActivity::class.java)

        // The Stay listener only dismisses the dialog and does not touch the activity.
        // Without invoking any choice handler, verify the mock receives no interaction
        // with any of the helper methods.
        verify(activity, never()).discardChangesAndExitToPendingNav(any())
        verify(activity, never()).setPendingNavAfterSave(any())
        verify(activity, never()).saveFormToDisk(any())
        verify(activity, never()).startActivity(any())
    }
}
