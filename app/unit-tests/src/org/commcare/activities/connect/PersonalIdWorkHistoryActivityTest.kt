package org.commcare.activities.connect

import android.os.Build
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import okhttp3.mockwebserver.MockResponse
import org.commcare.activities.BaseHomeScreenActivityTest
import org.commcare.android.util.ConnectTestUtils.signInToPersonalId
import org.commcare.connect.network.PersonalIdMockApiServer
import org.commcare.dalvik.R
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.robolectric.Robolectric
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

@Config(sdk = [Build.VERSION_CODES.P])
class PersonalIdWorkHistoryActivityTest : BaseHomeScreenActivityTest() {
    private val personalIdApi = PersonalIdMockApiServer(PersonalIdMockApiServer.CallbackMode.MAIN_LOOPER)

    @Before
    fun signInAndServeWorkHistory() {
        signInToPersonalId(hasConnectAccess = true, userId = "test-user-id", password = "test-password")
        personalIdApi.start()
        personalIdApi.server.enqueue(MockResponse().setResponseCode(200).setBody(buildWorkHistoryJson()))
    }

    @After
    fun stopPersonalIdApi() {
        personalIdApi.shutdown()
    }

    @Test
    fun `the toolbar keeps the work history title after returning to the screen while an app is seated`() {
        val controller = launchWorkHistoryScreen()

        controller
            .pause()
            .stop()
            .restart()
            .start()
            .resume()

        val activity = controller.get()
        assertEquals(activity.getString(R.string.personalid_work_history_title), activity.supportActionBar!!.title)
    }

    @Test
    fun `an earned row shows the user's name`() {
        val activity = launchWorkHistoryScreen().get()

        val earnedList = activity.findViewById<RecyclerView>(R.id.rvEarnedWorkHistory)
        val earnedRow = earnedList.findViewHolderForAdapterPosition(0)!!.itemView
        assertEquals("Test User", earnedRow.findViewById<TextView>(R.id.tvUserName).text.toString())
    }

    private fun launchWorkHistoryScreen(): ActivityController<PersonalIdWorkHistoryActivity> {
        val controller = Robolectric.buildActivity(PersonalIdWorkHistoryActivity::class.java).setup()
        personalIdApi.drainHttp()
        ShadowLooper.idleMainLooper()
        return controller
    }

    private fun buildWorkHistoryJson(): String =
        JSONObject()
            .put(
                "credentials",
                JSONArray().put(
                    JSONObject()
                        .put("uuid", "credential-1")
                        .put("app_id", "deliver-app-1")
                        .put("opp_id", "opportunity-1")
                        .put("date", "2026-09-30T09:00:00Z")
                        .put("title", "Maternal Health Home Visits")
                        .put("issuer", "Test Organization")
                        .put("level", "12MON_ACTIVE")
                        .put("type", "DELIVER")
                        .put("issuer_environment", "production")
                        .put("slug", "credential-1"),
                ),
            ).toString()
}
