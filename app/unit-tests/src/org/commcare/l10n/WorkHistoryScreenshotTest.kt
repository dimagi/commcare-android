package org.commcare.l10n

import okhttp3.mockwebserver.MockResponse
import org.commcare.CommCareTestApplication
import org.commcare.activities.connect.PersonalIdWorkHistoryActivity
import org.commcare.connect.network.PersonalIdMockApiServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.Robolectric
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper

@Config(application = CommCareTestApplication::class, sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(ParameterizedRobolectricTestRunner::class)
class WorkHistoryScreenshotTest(
    locale: String,
) : BaseConnectScreenshotTest(locale) {
    private val personalIdApi = PersonalIdMockApiServer(PersonalIdMockApiServer.CallbackMode.MAIN_LOOPER)

    @Before
    fun serveWorkHistory() {
        personalIdApi.start()
        personalIdApi.server.enqueue(MockResponse().setResponseCode(200).setBody(ConnectScreenshotData.workHistoryResponse()))
    }

    @After
    fun stopPersonalIdApi() {
        personalIdApi.shutdown()
    }

    @Test
    fun workHistory() {
        val controller =
            Robolectric
                .buildActivity(PersonalIdWorkHistoryActivity::class.java)
                .create()
                .postCreate(null)
                .start()
                .resume()
        personalIdApi.drainHttp()
        ShadowLooper.idleMainLooper()
        L10nScreenshots.capture(controller, locale, "50_personalid_work_history")
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun locales() = L10nScreenshots.parameters()
    }
}
