package org.commcare.connect

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.commcare.CommCareTestApplication
import org.commcare.activities.CommCareActivity
import org.commcare.android.database.connect.models.ConnectUserRecord
import org.commcare.android.util.ConnectTestUtils
import org.commcare.connect.database.ConnectAppDatabaseUtil
import org.commcare.connect.database.ConnectDatabaseHelper
import org.commcare.connect.database.ConnectUserDatabaseUtil
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.util.Date

/**
 * [AppLinkPromptHelper] offers to link an unmanaged app to PersonalID, twice at most: once on
 * first sight, then again 30 days later. These cover that schedule and the dispatch around it.
 *
 * Note the offer date is only written to the database once the user answers the dialog — the
 * helper sets it on the in-memory record and leaves persisting it to the button handlers. A
 * dialog dismissed any other way is not recorded, so the app is offered again next time.
 */
@Config(application = CommCareTestApplication::class)
@RunWith(AndroidJUnit4::class)
class AppLinkPromptHelperTest {
    private val appId = "test-app-id"
    private val username = "test-user"
    private val password = "app-password"

    @Before
    fun setUp() {
        PersonalIdSessionManager.clearInstance()
        ConnectTestUtils.createConnectDbFile()
        ConnectUserDatabaseUtil.storeUser(
            ConnectUserRecord("", username, "", "Test User", "", Date(), null, false, "", false),
        )
    }

    @After
    fun tearDown() {
        PersonalIdSessionManager.clearInstance()
        ConnectDatabaseHelper.teardown()
    }

    private fun mockActivity(): CommCareActivity<*> {
        val activity = mockk<CommCareActivity<*>>(relaxed = true)
        every { activity.applicationContext } returns CommCareTestApplication.instance()
        every { activity.getString(any()) } returns "text"
        return activity
    }

    @Test
    fun `an unmanaged app never offered before is offered and the first offer recorded`() {
        val activity = mockActivity()
        val callback = mockk<ConnectActivityCompleteListener>(relaxed = true)

        AppLinkPromptHelper.checkPersonalIdLink(activity, false, appId, username, password, callback)

        verify(exactly = 1) { activity.showAlertDialog(any()) }
    }

    @Test
    fun `an app offered recently is not offered again`() {
        ConnectAppDatabaseUtil.storeApp(appId, username, false, "", false).apply {
            linkOfferDate1 = ConnectTestUtils.daysFromNow(-1)
            ConnectAppDatabaseUtil.storeApp(this)
        }
        val activity = mockActivity()
        val callback = mockk<ConnectActivityCompleteListener>(relaxed = true)

        AppLinkPromptHelper.checkPersonalIdLink(activity, false, appId, username, password, callback)

        verify(exactly = 0) { activity.showAlertDialog(any()) }
        verify { callback.connectActivityComplete(false, null) }
    }

    @Test
    fun `an app first offered over 30 days ago is offered a second time`() {
        ConnectAppDatabaseUtil.storeApp(appId, username, false, "", false).apply {
            linkOfferDate1 = ConnectTestUtils.daysFromNow(-31)
            ConnectAppDatabaseUtil.storeApp(this)
        }
        val activity = mockActivity()
        val callback = mockk<ConnectActivityCompleteListener>(relaxed = true)

        AppLinkPromptHelper.checkPersonalIdLink(activity, false, appId, username, password, callback)

        verify(exactly = 1) { activity.showAlertDialog(any()) }
    }

    @Test
    fun `an app already offered twice is never offered again`() {
        ConnectAppDatabaseUtil.storeApp(appId, username, false, "", false).apply {
            linkOfferDate1 = ConnectTestUtils.daysFromNow(-90)
            linkOfferDate2 = ConnectTestUtils.daysFromNow(-60)
            ConnectAppDatabaseUtil.storeApp(this)
        }
        val activity = mockActivity()
        val callback = mockk<ConnectActivityCompleteListener>(relaxed = true)

        AppLinkPromptHelper.checkPersonalIdLink(activity, false, appId, username, password, callback)

        verify(exactly = 0) { activity.showAlertDialog(any()) }
        verify { callback.connectActivityComplete(false, null) }
    }

    @Test
    fun `a PersonalID managed login is not prompted to unlink`() {
        ConnectAppDatabaseUtil.storeApp(appId, username, true, password, false)
        val activity = mockActivity()
        val callback = mockk<ConnectActivityCompleteListener>(relaxed = true)

        AppLinkPromptHelper.checkPersonalIdLink(activity, true, appId, username, password, callback)

        verify(exactly = 0) { activity.showAlertDialog(any()) }
        verify { callback.connectActivityComplete(false, null) }
    }

    @Test
    fun `a PersonalID managed app logged into another way is prompted to unlink`() {
        ConnectAppDatabaseUtil.storeApp(appId, username, true, password, false)
        val activity = mockActivity()
        val callback = mockk<ConnectActivityCompleteListener>(relaxed = true)

        AppLinkPromptHelper.checkPersonalIdLink(activity, false, appId, username, password, callback)

        verify(exactly = 1) { activity.showAlertDialog(any()) }
    }
}
