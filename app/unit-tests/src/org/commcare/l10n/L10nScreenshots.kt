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

/**
 * Settings for the opt-in localization screenshot tests, read from the system properties the
 * build passes in when run with `-Pl10n.locale=en,fr`. Without that property every test skips.
 */
object L10nScreenshots {
    const val LOCALE_PROPERTY = "l10n.locale"
    private const val OUT_DIR_PROPERTY = "l10n.outDir"
    private const val NOT_REQUESTED = "not-requested"
    private const val DEVICE_QUALIFIERS = "w360dp-h800dp-xhdpi"
    private const val DEFAULT_LOCALE = "en"

    private val outDir: File = File(System.getProperty(OUT_DIR_PROPERTY) ?: "build/outputs/l10n-screenshots")

    /** One parameter per locale in [LOCALE_PROPERTY], or a single placeholder that [localeRule] skips. */
    @JvmStatic
    fun parameters(): Collection<Array<Any>> = parametersFor(System.getProperty(LOCALE_PROPERTY))

    /** One parameter per locale in the comma-separated [localeList], or the skipped placeholder when it names none. */
    fun parametersFor(localeList: String?): Collection<Array<Any>> =
        localeList
            .orEmpty()
            .split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .ifEmpty { listOf(NOT_REQUESTED) }
            .map { arrayOf<Any>(it) }

    /** Whether [locale] is a real locale rather than the placeholder used when none was requested. */
    fun isRequested(locale: String): Boolean = locale != NOT_REQUESTED

    /** Skips the test unless [locale] was requested, and otherwise switches the device to it before setup runs. */
    fun localeRule(locale: String): TestRule =
        TestRule { base, _: Description ->
            object : Statement() {
                override fun evaluate() {
                    assumeTrue("Set -P$LOCALE_PROPERTY to record localization screenshots", isRequested(locale))
                    RuntimeEnvironment.setQualifiers(DEVICE_QUALIFIERS)
                    if (locale != DEFAULT_LOCALE) {
                        RuntimeEnvironment.setQualifiers("+$locale")
                    }
                    base.evaluate()
                }
            }
        }

    /** Records the activity's window as `<outDir>/<locale>/<screen>.png`. */
    fun capture(
        controller: ActivityController<out Activity>,
        locale: String,
        screen: String,
    ) {
        controller.visible()
        ShadowLooper.idleMainLooper()
        val file = File(File(outDir, locale), "$screen.png")
        file.parentFile?.mkdirs()
        controller
            .get()
            .window
            .decorView
            .captureRoboImage(file.absolutePath)
    }
}
