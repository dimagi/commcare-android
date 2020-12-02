package org.commcare.android.tests.navigation

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import junit.framework.Assert.assertEquals
import org.commcare.CommCareApplication
import org.commcare.CommCareTestApplication
import org.commcare.activities.DispatchActivity
import org.commcare.activities.FormEntryActivity
import org.commcare.activities.StandardHomeActivity
import org.commcare.android.util.TestAppInstaller
import org.commcare.android.util.TestUtils
import org.junit.Assert
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
    fun testLaunchUsingSessionEndpoint() {
        // endpoint without arguments
        var homeActivity = launchHomeActivityWithSessionEndpoint("registration_form", ArrayList())
        verifyNextIntent(Shadows.shadowOf(homeActivity).nextStartedActivity, "Case MGMT > Registration")
        CommCareApplication.instance().currentSession.clearAllState()


        // endpoint with a case selction
        val args = ArrayList<String>()
        args.add("b319e951-03f1-4172-b662-4fb3964a0be7")
        homeActivity = launchHomeActivityWithSessionEndpoint("visit_form", args)
        verifyNextIntent(Shadows.shadowOf(homeActivity).nextStartedActivity, "Case MGMT > Visit > [stan]")
        CommCareApplication.instance().currentSession.clearAllState()

        // test with another case id
        args.clear()
        args.add("8e011880-602f-4017-b9d6-ed9dcbba7516")
        homeActivity = launchHomeActivityWithSessionEndpoint("visit_form", args)
        verifyNextIntent(Shadows.shadowOf(homeActivity).nextStartedActivity, "Case MGMT > Visit > [ellen]")
        CommCareApplication.instance().currentSession.clearAllState()

        // endpoint to case close form
        args.clear()
        args.add("c44c7ade-0cec-4401-b422-4c475f0043ae")
        homeActivity = launchHomeActivityWithSessionEndpoint("close_form", args)
        verifyNextIntent(Shadows.shadowOf(homeActivity).nextStartedActivity, "Case MGMT > Close > [pat]")
        CommCareApplication.instance().currentSession.clearAllState()
    }

    private fun verifyNextIntent(nextIntent : Intent, header: String) {
        val intentActivityName: String = nextIntent.component!!.className
        Assert.assertEquals(FormEntryActivity::class.java.name, intentActivityName)
        assertEquals(nextIntent.extras!!.getString(FormEntryActivity.KEY_HEADER_STRING), header)
    }

    private fun launchHomeActivityWithSessionEndpoint(endpointId: String, args: java.util.ArrayList<String>): StandardHomeActivity {
        val intent = Intent("org.commcare.dalvik.action.CommCareSession")
        intent.putExtra(DispatchActivity.SESSION_ENDPOINT_ID, endpointId)

        intent.putStringArrayListExtra(DispatchActivity.SESSION_ENDPOINT_ARGUMENTS, args)
        intent.putExtra(DispatchActivity.WAS_EXTERNAL, true)
        return Robolectric.buildActivity(StandardHomeActivity::class.java, intent).create().resume().get()
    }

}
