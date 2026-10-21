package org.commcare.l10n

import org.commcare.CommCareTestApplication
import org.commcare.android.database.connect.models.ConnectUserRecord
import org.commcare.dalvik.R
import org.commcare.personalId.profile.BasePersonalIdProfileTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@Config(application = CommCareTestApplication::class, sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(ParameterizedRobolectricTestRunner::class)
class ProfileScreenshotTest(
    private val locale: String,
) : BasePersonalIdProfileTest() {
    @get:Rule
    val localeRule: TestRule = L10nScreenshots.localeRule(locale)

    override fun buildUser(): ConnectUserRecord = ConnectScreenshotData.user()

    @Test
    fun profile() = L10nScreenshots.capture(activityController, locale, "40_personalid_profile")

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun locales() = L10nScreenshots.parameters()
    }
}

@Config(application = CommCareTestApplication::class, sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(ParameterizedRobolectricTestRunner::class)
class ProfileEditScreenshotTest(
    private val locale: String,
) : BasePersonalIdProfileTest() {
    @get:Rule
    val localeRule: TestRule = L10nScreenshots.localeRule(locale)

    override fun buildUser(): ConnectUserRecord = ConnectScreenshotData.user()

    @Test
    fun profileEdit() {
        onUiThread { navController.navigate(R.id.action_profile_to_profile_edit) }
        L10nScreenshots.capture(activityController, locale, "41_personalid_profile_edit")
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun locales() = L10nScreenshots.parameters()
    }
}
