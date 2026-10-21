package org.commcare.l10n

import androidx.test.core.app.ApplicationProvider
import org.commcare.CommCareTestApplication
import org.commcare.activities.connect.ConnectActivity
import org.commcare.connect.database.ConnectJobUtils
import org.commcare.connect.repository.ConnectRepository
import org.commcare.connect.repository.ConnectSyncPreferences
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.Robolectric
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@Config(application = CommCareTestApplication::class, sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(ParameterizedRobolectricTestRunner::class)
class OpportunityListScreenshotTest(
    locale: String,
) : BaseConnectScreenshotTest(locale) {
    @Before
    fun seedOpportunities() {
        ConnectJobUtils.storeJobs(ApplicationProvider.getApplicationContext(), ConnectScreenshotData.opportunities(), true)
        ConnectSyncPreferences.getInstance().apply {
            markSessionStart()
            storeLastSyncTime(ConnectRepository.SYNC_KEY_OPPORTUNITIES)
        }
    }

    @Test
    fun opportunityList() {
        val controller =
            Robolectric
                .buildActivity(ConnectActivity::class.java)
                .create()
                .postCreate(null)
                .start()
                .resume()
        L10nScreenshots.capture(controller, locale, "20_connect_opportunity_list")
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun locales() = L10nScreenshots.parameters()
    }
}
