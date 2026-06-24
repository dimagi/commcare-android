package org.commcare.personalId.profile

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.commcare.CommCareApplication
import org.commcare.CommCareTestApplication
import org.commcare.android.database.connect.models.ConnectUserRecord
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@Config(application = CommCareTestApplication::class)
@RunWith(AndroidJUnit4::class)
class PersonalIdProfileViewModelTest {
    private val context = CommCareApplication.instance()

    @Test
    fun `getProfileDisplayModelForUser maps all fields from the record`() {
        val user =
            ConnectUserRecord().apply {
                name = "Ada Lovelace"
                email = "ada@example.com"
                photo = "base64photo"
                primaryPhone = "+919876543210"
            }

        val profileDetails = PersonalIdProfileViewModel.getProfileDisplayModelForUser(context, user)

        assertEquals("Ada Lovelace", profileDetails.name)
        assertEquals("+91 98765 43210", profileDetails.displayPhone)
        assertEquals("ada@example.com", profileDetails.email)
        assertEquals("base64photo", profileDetails.photoBase64)
    }

    @Test
    fun `getProfileDisplayModelForUser falls back to empty string for a null email`() {
        val user =
            ConnectUserRecord().apply {
                name = "Ada Lovelace"
                email = null
            }

        val profileDetails = PersonalIdProfileViewModel.getProfileDisplayModelForUser(context, user)

        assertEquals("", profileDetails.email)
    }
}
