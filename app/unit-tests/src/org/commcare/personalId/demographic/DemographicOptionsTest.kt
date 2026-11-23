package org.commcare.personalId.demographic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DemographicOptionsTest {
    @Test
    fun `every option is found again by its own key`() {
        Gender.entries.forEach { assertEquals(it, demographicOptionFromKey<Gender>(it.key)) }
        Education.entries.forEach { assertEquals(it, demographicOptionFromKey<Education>(it.key)) }
        ChwExperience.entries.forEach { assertEquals(it, demographicOptionFromKey<ChwExperience>(it.key)) }
        TechProficiency.entries.forEach { assertEquals(it, demographicOptionFromKey<TechProficiency>(it.key)) }
    }

    @Test
    fun `an unset or unknown key is read as no option`() {
        assertNull(demographicOptionFromKey<Gender>(null))
        assertNull(demographicOptionFromKey<Gender>(""))
        assertNull(demographicOptionFromKey<Gender>("non_binary"))
    }

    @Test
    fun `keys are unique within each field`() {
        listOf(Gender.entries, Education.entries, ChwExperience.entries, TechProficiency.entries).forEach { options ->
            val keys = options.map { it.key }
            assertEquals("Duplicate key in $keys", keys.size, keys.toSet().size)
        }
    }

    @Test
    fun `stored keys match the ones agreed for the server`() {
        assertEquals("female", Gender.FEMALE.key)
        assertEquals("bachelors_degree", Education.BACHELORS_DEGREE.key)
        assertEquals("3_5_years", ChwExperience.THREE_TO_FIVE_YEARS.key)
        assertEquals("comfortable", TechProficiency.COMFORTABLE.key)
    }
}
