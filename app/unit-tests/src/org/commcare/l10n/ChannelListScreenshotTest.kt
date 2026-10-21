package org.commcare.l10n

import androidx.test.core.app.ApplicationProvider
import org.commcare.CommCareTestApplication
import org.commcare.activities.connect.ConnectMessagingActivity
import org.commcare.connect.database.ConnectMessagingDatabaseHelper
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
class ChannelListScreenshotTest(
    locale: String,
) : BaseConnectScreenshotTest(locale) {
    @Before
    fun seedMessaging() {
        val context = ApplicationProvider.getApplicationContext<CommCareTestApplication>()
        ConnectScreenshotData.channels().forEach { ConnectMessagingDatabaseHelper.storeMessagingChannel(context, it) }
        ConnectScreenshotData.messages().forEach { ConnectMessagingDatabaseHelper.storeMessagingMessage(context, it) }
    }

    @Test
    fun channelList() {
        val controller =
            Robolectric
                .buildActivity(ConnectMessagingActivity::class.java)
                .create()
                .postCreate(null)
                .start()
                .resume()
        L10nScreenshots.capture(controller, locale, "30_connect_channel_list")
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun locales() = L10nScreenshots.parameters()
    }
}
