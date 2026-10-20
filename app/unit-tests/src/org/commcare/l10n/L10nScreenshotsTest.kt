package org.commcare.l10n

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class L10nScreenshotsTest {
    @Test
    fun `each listed locale becomes one parameter, in order and trimmed`() {
        assertEquals(listOf("en", "fr", "pt"), localesOf(" en, fr ,pt "))
    }

    @Test
    fun `no locale property gives a single placeholder that is not requested`() {
        val locales = localesOf(null)

        assertEquals(1, locales.size)
        assertFalse(L10nScreenshots.isRequested(locales.single()))
    }

    @Test
    fun `a property naming no locales is treated as not set`() {
        assertFalse(L10nScreenshots.isRequested(localesOf(" , ").single()))
    }

    @Test
    fun `a listed locale is requested`() {
        assertTrue(L10nScreenshots.isRequested(localesOf("ti").single()))
    }

    @Test
    fun `a language qualifier maps to that language`() {
        assertEquals(Locale("fr"), L10nScreenshots.javaLocaleOf("fr"))
    }

    @Test
    fun `a region qualifier maps to its language and country`() {
        assertEquals(Locale("pt", "BR"), L10nScreenshots.javaLocaleOf("pt-rBR"))
    }

    private fun localesOf(localeList: String?): List<String> = L10nScreenshots.parametersFor(localeList).map { it.single() as String }
}
