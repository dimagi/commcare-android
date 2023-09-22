package org.commcare.android.tests.navigation

import android.content.Intent
import android.os.Bundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.commcare.CommCareTestApplication
import org.commcare.activities.DispatchActivity
import org.commcare.activities.FormEntryActivity
import org.commcare.activities.LoginActivity
import org.commcare.activities.StandardHomeActivity
import org.commcare.android.util.TestAppInstaller
import org.commcare.android.util.TestUtils
import org.commcare.util.screen.CommCareSessionException
import org.commcare.utils.SessionUnavailableException
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows
import org.robolectric.annotation.Config


@Config(application = CommCareTestApplication::class)
@RunWith(AndroidJUnit4::class)
class ExternalLaunchTests {


    @Before
    fun setup() {
        TestAppInstaller.installAppAndLogin(
                "jr://resource/commcare-apps/index_and_cache_test/profile.ccpr",
                "test", "123")
        TestUtils.processResourceTransactionIntoAppDb("/commcare-apps/case_list_lookup/restore.xml")
    }

    @Test
    fun testLaunchWithAppId() {
        // verify initial state
        val indexAndCacheTestAppId = "000b3b9ecb0806331fbd1526616eb718"
        assertEquals(indexAndCacheTestAppId, CommCareTestApplication.instance().currentApp.uniqueId)
        assertEquals("test", CommCareTestApplication.instance().session.loggedInUser.username)

        // Install another app, this will also seat this app as current app
        TestAppInstaller.installApp("jr://resource/commcare-apps/archive_form_tests/profile.ccpr")
        val archiveFormTestsAppId = "6a03772aedd992c9f2c9c2198a248184"
        assertEquals(archiveFormTestsAppId, CommCareTestApplication.instance().currentApp.uniqueId)
        assertEquals("test", CommCareTestApplication.instance().session.loggedInUser.username)

        // Request launch for seated app, since user is logged in already we should end up on app home screen
        launchAndVerifyCCWithAppId(archiveFormTestsAppId, StandardHomeActivity::class.java.name)

        // Request launch for non seated app, this should cause user to log out and show user Login Screen
        launchAndVerifyCCWithAppId(indexAndCacheTestAppId, LoginActivity::class.java.name)
        assertThrows(SessionUnavailableException::class.java) {
            CommCareTestApplication.instance().session.loggedInUser
        }
    }

    private fun launchAndVerifyCCWithAppId(appId: String, nextScreen: String) {
        val intent = Intent("org.commcare.dalvik.action.CommCareSession")
        intent.putExtra(DispatchActivity.SESSION_ENDPOINT_APP_ID, appId)
        var dispathActivity =
            Robolectric.buildActivity(DispatchActivity::class.java, intent).create().resume().get()
        var recordedNextIntent = Shadows.shadowOf(dispathActivity).nextStartedActivity
        var intentActivityName: String = recordedNextIntent.component!!.className
        assertEquals(nextScreen, intentActivityName)
    }

    @Test
    fun testLaunchUsingSessionEndpoint() {
        // endpoint without arguments
        var homeActivity = launchHomeActivityWithSessionEndpoint("registration_form", Bundle())
        verifyNextIntent(Shadows.shadowOf(homeActivity).nextStartedActivity, "Case MGMT > Registration")

        val argsAsBundle = Bundle()

        // endpoint with a case selction
        argsAsBundle.putString("case_id", "b319e951-03f1-4172-b662-4fb3964a0be7")
        homeActivity = launchHomeActivityWithSessionEndpoint("visit_form", argsAsBundle)
        verifyNextIntent(Shadows.shadowOf(homeActivity).nextStartedActivity, "Case MGMT > Visit > [stan]")

        // test with another case id
        argsAsBundle.clear()
        argsAsBundle.putString("case_id", "8e011880-602f-4017-b9d6-ed9dcbba7516")
        homeActivity = launchHomeActivityWithSessionEndpoint("visit_form", argsAsBundle)
        verifyNextIntent(Shadows.shadowOf(homeActivity).nextStartedActivity, "Case MGMT > Visit > [ellen]")

        // endpoint to case close form
        argsAsBundle.clear()
        argsAsBundle.putString("case_id", "c44c7ade-0cec-4401-b422-4c475f0043ae")
        homeActivity = launchHomeActivityWithSessionEndpoint("close_form", argsAsBundle)
        verifyNextIntent(Shadows.shadowOf(homeActivity).nextStartedActivity, "Case MGMT > Close > [pat]")

        // list args
        val argsAsList = ArrayList<String>()
        argsAsList.add("c44c7ade-0cec-4401-b422-4c475f0043ae")
        homeActivity = launchHomeActivityWithSessionEndpoint("close_form", argsAsList)
        verifyNextIntent(Shadows.shadowOf(homeActivity).nextStartedActivity, "Case MGMT > Close > [pat]")
    }

    private fun verifyNextIntent(nextIntent: Intent, header: String) {
        val intentActivityName: String = nextIntent.component!!.className
        assertEquals(FormEntryActivity::class.java.name, intentActivityName)
        assertEquals(nextIntent.extras!!.getString(FormEntryActivity.KEY_HEADER_STRING), header)
    }

    private fun launchHomeActivityWithSessionEndpoint(endpointId: String, args: Any): StandardHomeActivity {
        val intent = Intent("org.commcare.dalvik.action.CommCareSession")
        intent.putExtra(DispatchActivity.SESSION_ENDPOINT_ID, endpointId)

        if (args is Bundle) {
            intent.putExtra(DispatchActivity.SESSION_ENDPOINT_ARGUMENTS_BUNDLE, args)
        } else {
            intent.putStringArrayListExtra(DispatchActivity.SESSION_ENDPOINT_ARGUMENTS_LIST, args as ArrayList<String>)
        }

        intent.putExtra(DispatchActivity.WAS_EXTERNAL, true)
        return Robolectric.buildActivity(StandardHomeActivity::class.java, intent).create().resume().get()
    }

}
