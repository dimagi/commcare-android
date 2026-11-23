package org.commcare.personalId.profile

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.commcare.CommCareApplication
import org.commcare.CommCareTestApplication
import org.commcare.android.database.connect.models.ConnectUserRecord
import org.commcare.personalId.demographic.DateOfBirth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@Config(application = CommCareTestApplication::class)
@RunWith(AndroidJUnit4::class)
class PersonalIdProfileDisplayModelTest {
    private val context = CommCareApplication.instance()

    @Test
    fun `fromUserRecord maps all fields from the record`() {
        val user =
            ConnectUserRecord().apply {
                name = "Ada Lovelace"
                email = "ada@example.com"
                photo = "base64photo"
                primaryPhone = "+919876543210"
            }

        val profileDetails = PersonalIdProfileDisplayModel.fromUserRecord(context, user)

        assertEquals("Ada Lovelace", profileDetails.name)
        assertEquals("+91 98765 43210", profileDetails.displayPhone)
        assertEquals("ada@example.com", profileDetails.email)
        assertEquals("base64photo", profileDetails.photoBase64)
    }

    @Test
    fun `fromUserRecord falls back to empty string for a null email`() {
        val user =
            ConnectUserRecord().apply {
                name = "Ada Lovelace"
                email = null
            }

        val profileDetails = PersonalIdProfileDisplayModel.fromUserRecord(context, user)

        assertEquals("", profileDetails.email)
    }

    @Test
    fun `fromUserRecord shows demographic keys as labels`() {
        val user =
            ConnectUserRecord().apply {
                name = "Ada Lovelace"
                gender = "female"
                languages = "hau,eng"
                dateOfBirth = "1979-11-06"
                education = "bachelors_degree"
                chwExperience = "3_5_years"
                techProficiency = "comfortable"
            }

        val profileDetails = PersonalIdProfileDisplayModel.fromUserRecord(context, user)

        assertEquals("Female", profileDetails.gender)
        assertEquals(listOf("Hausa", "English"), profileDetails.languages)
        assertEquals(DateOfBirth.forDisplay("1979-11-06"), profileDetails.dateOfBirth)
        assertEquals("Bachelors Degree", profileDetails.education)
        assertEquals("3–5 years", profileDetails.chwExperience)
        assertEquals("Comfortable with technology", profileDetails.techProficiency)
    }

    @Test
    fun `fromUserRecord leaves unset demographic fields empty`() {
        val user = ConnectUserRecord().apply { name = "Ada Lovelace" }

        val profileDetails = PersonalIdProfileDisplayModel.fromUserRecord(context, user)

        assertNull(profileDetails.gender)
        assertEquals(emptyList<String>(), profileDetails.languages)
        assertNull(profileDetails.dateOfBirth)
        assertNull(profileDetails.education)
        assertNull(profileDetails.chwExperience)
        assertNull(profileDetails.techProficiency)
    }
}
