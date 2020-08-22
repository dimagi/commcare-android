package org.commcare.androidTests

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import org.commcare.utils.assert
import org.commcare.dalvik.R
import org.commcare.provider.DebugControlsReceiver
import org.commcare.utils.InstrumentationUtility
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
class SyncRecoveryTest: BaseTest() {

    lateinit var mContext: Context
    val mReceiver = DebugControlsReceiver()

    companion object {
        const val CCZ_NAME = "case_claim.ccz"
        const val APP_NAME = "Case Search and Claim"
        const val RECOVER_SYNC_ACTION = "org.commcare.dalvik.api.action.TriggerSyncRecover"
    }

    @Before
    fun setup() {
        installApp(APP_NAME, CCZ_NAME)
        InstrumentationUtility.login("test", "123")
        mContext = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        LocalBroadcastManager.getInstance(mContext).registerReceiver(mReceiver, IntentFilter(RECOVER_SYNC_ACTION))
    }

    @After
    fun tearDown() {
        LocalBroadcastManager.getInstance(mContext).unregisterReceiver(mReceiver)
    }

    @Test
    fun testRecoverySyncWorks() {
        // screen_entity_select_list
        onView(withText("Start"))
                .perform(click())
        onView(withText("Follow Up"))
                .perform(click())

        // Store case list size.
        val size = InstrumentationUtility.getListSize(R.id.screen_entity_select_list)

        // Stage a recovery sync.
        val intent = Intent(RECOVER_SYNC_ACTION)

        // Had to use LocalBroadcastManager to make sure that the broadcast message is processed
        // by the application before espresso resumes the test.
        LocalBroadcastManager.getInstance(mContext)
                .sendBroadcastSync(intent)

        pressBack()
        pressBack()

        onView(withText("Sync with Server"))
                .perform(click())
        onView(withText("Start"))
                .perform(click())
        onView(withText("Follow Up"))
                .perform(click())

        // make sure the same number of cases are around
        assert(InstrumentationUtility.getListSize(R.id.screen_entity_select_list) == size, "List size isn't same")
    }

}