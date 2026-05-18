package org.commcare.fragments.personalId

import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.commcare.CommCareTestApplication
import org.commcare.dalvik.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@Config(application = CommCareTestApplication::class)
@RunWith(AndroidJUnit4::class)
class PersonalIdPhotoCaptureFragmentTest : BasePersonalIdPhotoCaptureFragmentTest() {
    @Test
    fun testFragmentSmokeTest_fragmentViewInflated() {
        assertNotNull("Fragment view should be inflated", fragment.view)
        val title = fragment.view!!.findViewById<TextView>(R.id.title)
        assertNotNull("Title TextView should exist", title)
        assertEquals(
            R.id.personalid_photo_capture,
            navController.currentDestination?.id,
        )
    }
}
