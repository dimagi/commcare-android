package org.commcare.l10n

import android.os.Bundle
import androidx.navigation.fragment.NavHostFragment
import androidx.test.core.app.ApplicationProvider
import org.commcare.CommCareTestApplication
import org.commcare.activities.connect.ConnectMessagingActivity
import org.commcare.connect.database.ConnectMessagingDatabaseHelper
import org.commcare.dalvik.R
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
class ChannelMessagesScreenshotTest(
    locale: String,
) : BaseConnectScreenshotTest(locale) {
    @Before
    fun seedMessaging() {
        val context = ApplicationProvider.getApplicationContext<CommCareTestApplication>()
        ConnectScreenshotData.channels().forEach { ConnectMessagingDatabaseHelper.storeMessagingChannel(context, it) }
        ConnectScreenshotData.messages().forEach { ConnectMessagingDatabaseHelper.storeMessagingMessage(context, it) }
    }

    @Test
    fun channelMessages() {
        val controller =
            Robolectric
                .buildActivity(ConnectMessagingActivity::class.java)
                .create()
                .postCreate(null)
                .start()
                .resume()
        val navHost =
            controller.get().supportFragmentManager.findFragmentById(R.id.nav_host_fragment_connect_messaging) as NavHostFragment
        navHost.navController.navigate(
            R.id.connectMessageFragment,
            Bundle().apply { putString(ConnectMessagingActivity.CHANNEL_ID, ConnectScreenshotData.CONVERSATION_CHANNEL_ID) },
        )
        ShadowLooper.idleMainLooper()
        L10nScreenshots.capture(controller, locale, "31_connect_channel_messages")
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun locales() = L10nScreenshots.parameters()
    }
}
