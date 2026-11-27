package org.commcare.personalId.profile.demographic

import org.commcare.android.database.connect.models.ConnectUserRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DemographicProfileTest {
    private val fullProfile =
        DemographicProfile("female", "1994-03-21", "eng,hau", "bachelors_degree", "3_5_years", "comfortable")

    @Test
    fun `request params carry every field by its server key`() {
        assertEquals(
            mapOf(
                "gender" to "female",
                "date_of_birth" to "1994-03-21",
                "languages" to "eng,hau",
                "education" to "bachelors_degree",
                "chw_experience" to "3_5_years",
                "tech_proficiency" to "comfortable",
            ),
            fullProfile.toRequestParams(),
        )
    }

    @Test
    fun `request params send unset fields as empty strings`() {
        val params = DemographicProfile(gender = "male").toRequestParams()

        assertEquals("male", params["gender"])
        assertEquals(6, params.size)
        assertEquals(listOf("", "", "", "", ""), params.filterKeys { it != "gender" }.values.toList())
    }

    @Test
    fun `applyTo copies every field onto the user record`() {
        val user = ConnectUserRecord()

        fullProfile.applyTo(user)

        assertEquals("female", user.gender)
        assertEquals("1994-03-21", user.dateOfBirth)
        assertEquals("eng,hau", user.languages)
        assertEquals("bachelors_degree", user.education)
        assertEquals("3_5_years", user.chwExperience)
        assertEquals("comfortable", user.techProficiency)
    }

    @Test
    fun `applyTo clears fields that are unset`() {
        val user = ConnectUserRecord().apply { gender = "female" }

        DemographicProfile().applyTo(user)

        assertNull(user.gender)
    }
}
