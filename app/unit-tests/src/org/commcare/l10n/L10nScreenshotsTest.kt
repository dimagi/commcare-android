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
        val targets = targetsOf(null, null)

        assertEquals(1, targets.size)
        assertFalse(L10nScreenshots.isRequested(targets.single()))
    }

    @Test
    fun `a property naming no locales is treated as not set`() {
        assertFalse(L10nScreenshots.isRequested(targetsOf(" , ", null).single()))
    }

    @Test
    fun `a listed locale is requested`() {
        assertTrue(L10nScreenshots.isRequested(targetsOf("ti", null).single()))
    }

    @Test
    fun `no size property renders every locale at the default size in its own locale folder`() {
        val targets = targetsOf("en,fr", null)

        assertEquals(listOf(L10nScreenshots.DEFAULT_SIZE), targets.map { it.size }.distinct())
        assertEquals(listOf("en", "fr"), targets.map { it.folder })
    }

    @Test
    fun `a single listed size keeps the locale folder`() {
        val target = targetsOf("fr", " w320dp-h534dp-hdpi ").single()

        assertEquals("w320dp-h534dp-hdpi", target.size)
        assertEquals("fr", target.folder)
    }

    @Test
    fun `several sizes give each locale one target per size, foldered by size`() {
        val targets = targetsOf("en,fr", "w360dp-h800dp-xhdpi,w320dp-h534dp-hdpi")

        assertEquals(
            listOf(
                "en/w360dp-h800dp-xhdpi",
                "en/w320dp-h534dp-hdpi",
                "fr/w360dp-h800dp-xhdpi",
                "fr/w320dp-h534dp-hdpi",
            ),
            targets.map { it.folder },
        )
    }

    @Test
    fun `a target's test name has no path separator`() {
        val target = targetsOf("fr", "w360dp-h800dp-xhdpi,w320dp-h534dp-hdpi").first()

        assertEquals("fr_w360dp-h800dp-xhdpi", target.toString())
    }

    @Test
    fun `a language qualifier maps to that language`() {
        assertEquals(Locale("fr"), L10nScreenshots.javaLocaleOf("fr"))
    }

    @Test
    fun `a region qualifier maps to its language and country`() {
        assertEquals(Locale("pt", "BR"), L10nScreenshots.javaLocaleOf("pt-rBR"))
    }

    private fun localesOf(localeList: String?): List<String> = targetsOf(localeList, null).map { it.locale }

    private fun targetsOf(
        localeList: String?,
        sizeList: String?,
    ): List<ScreenshotTarget> = L10nScreenshots.parametersFor(localeList, sizeList).map { it.single() as ScreenshotTarget }
}
