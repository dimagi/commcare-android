package org.commcare.personalId.profile.demographic

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.commcare.CommCareApplication
import org.commcare.CommCareTestApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.util.Locale

@Config(application = CommCareTestApplication::class)
@RunWith(AndroidJUnit4::class)
class PersonalIdLanguagesTest {
    private val context = CommCareApplication.instance()

    private val hausa = PersonalIdLanguage("hau", "Hausa")
    private val hindi = PersonalIdLanguage("hin", "हिन्दी (Hindi)")
    private val english = PersonalIdLanguage("eng", "English")
    private val acholi = PersonalIdLanguage("ach", "Acholi")

    // ========== Stored codes ==========

    @Test
    fun `stored codes are split on commas, ignoring spaces and empty items`() {
        assertEquals(listOf("hau", "yor", "eng"), PersonalIdLanguages.parseCodes(" hau, yor,,eng ,"))
    }

    @Test
    fun `nothing stored gives no codes`() {
        assertEquals(emptyList<String>(), PersonalIdLanguages.parseCodes(null))
        assertEquals(emptyList<String>(), PersonalIdLanguages.parseCodes(""))
    }

    @Test
    fun `codes are joined with commas, and no codes are stored as null`() {
        assertEquals("hau,yor", PersonalIdLanguages.joinCodes(listOf("hau", "yor")))
        assertNull(PersonalIdLanguages.joinCodes(emptyList()))
    }

    // ========== Picker order ==========

    @Test
    fun `selected languages come first, then the device language, then the rest alphabetically`() {
        val ordered =
            PersonalIdLanguages.orderForPicker(
                listOf(acholi, english, hausa, hindi),
                selectedCodes = setOf("hau"),
                deviceLanguageCode = "hin",
            )

        assertEquals(listOf(hausa, hindi, acholi, english), ordered)
    }

    @Test
    fun `a selected device language is listed once, with the selected ones`() {
        val ordered =
            PersonalIdLanguages.orderForPicker(
                listOf(acholi, english, hausa, hindi),
                selectedCodes = setOf("hin"),
                deviceLanguageCode = "hin",
            )

        assertEquals(listOf(hindi, acholi, english, hausa), ordered)
    }

    @Test
    fun `without a device language the rest are alphabetical after the selected ones`() {
        val ordered =
            PersonalIdLanguages.orderForPicker(
                listOf(hindi, hausa, english, acholi),
                selectedCodes = setOf("eng"),
                deviceLanguageCode = null,
            )

        assertEquals(listOf(english, acholi, hausa, hindi), ordered)
    }

    // ========== Device language ==========

    @Test
    fun `the device language is converted to its three-letter code`() {
        assertEquals("hin", PersonalIdLanguages.deviceLanguageCode(Locale("hi", "IN")))
        assertEquals("hau", PersonalIdLanguages.deviceLanguageCode(Locale("ha", "NG")))
        assertEquals("fra", PersonalIdLanguages.deviceLanguageCode(Locale.FRANCE))
    }

    @Test
    fun `a locale without a language gives no device language`() {
        assertNull(PersonalIdLanguages.deviceLanguageCode(Locale.ROOT))
    }

    // ========== Bundled list ==========

    @Test
    fun `the bundled list loads every language with a unique three-letter code and a label`() {
        val languages = PersonalIdLanguages.all(context)

        assertEquals(158, languages.size)
        assertEquals(languages.size, languages.map { it.code }.toSet().size)
        assertTrue(languages.all { it.code.matches(Regex("[a-z]{3}")) })
        assertTrue(languages.all { it.label.isNotBlank() })
    }

    @Test
    fun `labels come back in the order of the codes, skipping codes not in the list`() {
        val labels = PersonalIdLanguages.labelsFor(context, listOf("yor", "xyz", "hau"))

        assertEquals(listOf("Yorùbá (Yoruba)", "Hausa"), labels)
    }
}
