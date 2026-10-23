package org.commcare.l10n

import android.app.Activity
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assume.assumeTrue
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import org.robolectric.RuntimeEnvironment
import org.robolectric.android.controller.ActivityController
import org.robolectric.shadows.ShadowLooper
import java.io.File
import java.util.Locale

/**
 * One screenshot run: a [locale] and the screen [size] qualifiers it renders at, saved under [folder].
 */
data class ScreenshotTarget(
    val locale: String,
    val size: String,
    val folder: String,
) {
    override fun toString(): String = folder.replace('/', '_')
}

/**
 * Settings for the opt-in localization screenshot tests, read from the system properties the
 * build passes in when run with `-Pl10n.locale=en,fr`. Without that property every test skips.
 */
object L10nScreenshots {
    const val LOCALE_PROPERTY = "l10n.locale"
    const val SIZES_PROPERTY = "l10n.sizes"
    const val DEFAULT_SIZE = "w360dp-h800dp-xhdpi"
    private const val OUT_DIR_PROPERTY = "l10n.outDir"
    private const val NOT_REQUESTED = "not-requested"
    private const val DEFAULT_LOCALE = "en"

    private val outDir: File = File(System.getProperty(OUT_DIR_PROPERTY) ?: "build/outputs/l10n-screenshots")

    /** One parameter per locale and size requested, or a single placeholder that [localeRule] skips. */
    @JvmStatic
    fun parameters(): Collection<Array<Any>> = parametersFor(System.getProperty(LOCALE_PROPERTY), System.getProperty(SIZES_PROPERTY))

    /**
     * One parameter per locale in the comma-separated [localeList] at each size in [sizeList], or the skipped
     * placeholder when no locale is named. Sizes default to [DEFAULT_SIZE], and get their own folder when several are named.
     */
    fun parametersFor(
        localeList: String?,
        sizeList: String? = null,
    ): Collection<Array<Any>> {
        val sizes = entriesOf(sizeList).ifEmpty { listOf(DEFAULT_SIZE) }
        return entriesOf(localeList).ifEmpty { listOf(NOT_REQUESTED) }.flatMap { locale ->
            sizes.map { size ->
                val folder = if (sizes.size > 1) "$locale/$size" else locale
                arrayOf<Any>(ScreenshotTarget(locale, size, folder))
            }
        }
    }

    private fun entriesOf(commaSeparated: String?): List<String> =
        commaSeparated
            .orEmpty()
            .split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()

    /** Whether [target] names a real locale rather than the placeholder used when none was requested. */
    fun isRequested(target: ScreenshotTarget): Boolean = target.locale != NOT_REQUESTED

    /** Skips the test unless [target] was requested, and otherwise switches the device to it before setup runs. */
    fun localeRule(target: ScreenshotTarget): TestRule =
        TestRule { base, _: Description ->
            object : Statement() {
                override fun evaluate() {
                    assumeTrue("Set -P$LOCALE_PROPERTY to record localization screenshots", isRequested(target))
                    RuntimeEnvironment.setQualifiers(target.size)
                    if (target.locale != DEFAULT_LOCALE) {
                        RuntimeEnvironment.setQualifiers("+${target.locale}")
                    }
                    val savedDefault = Locale.getDefault()
                    Locale.setDefault(javaLocaleOf(target.locale))
                    try {
                        base.evaluate()
                    } finally {
                        Locale.setDefault(savedDefault)
                    }
                }
            }
        }

    /** The Java locale for a resource qualifier such as `fr` or `pt-rBR`, which dates and numbers format with. */
    fun javaLocaleOf(qualifier: String): Locale {
        val parts = qualifier.split("-r", limit = 2)
        return if (parts.size == 2) Locale(parts[0], parts[1]) else Locale(qualifier)
    }

    /** Records the activity's window as `<outDir>/<target folder>/<screen>.png`. */
    fun capture(
        controller: ActivityController<out Activity>,
        target: ScreenshotTarget,
        screen: String,
    ) {
        controller.visible()
        ShadowLooper.idleMainLooper()
        val file = File(File(outDir, target.folder), "$screen.png")
        file.parentFile?.mkdirs()
        controller
            .get()
            .window
            .decorView
            .captureRoboImage(file.absolutePath)
    }
}
