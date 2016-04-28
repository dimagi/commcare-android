package org.commcare.android.tests.activities;

import android.content.Intent;

import org.commcare.CommCareApplication;
import org.commcare.CommCareTestApplication;
import org.commcare.activities.PostRequestActivity;
import org.commcare.android.CommCareTestRunner;
import org.commcare.android.util.ActivityLaunchUtils;
import org.commcare.android.util.TestAppInstaller;
import org.commcare.dalvik.BuildConfig;
import org.commcare.models.AndroidSessionWrapper;
import org.commcare.session.CommCareSession;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowActivity;

import java.util.Hashtable;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author Phillip Mates (pmates@dimagi.com)
 */
@Config(application = CommCareTestApplication.class,
        constants = BuildConfig.class)
@RunWith(CommCareTestRunner.class)
public class QueryRequestActivityTest {
    @Before
    public void setup() {
        TestAppInstaller.installAppAndLogin(
                "jr://resource/commcare-apps/case_search_and_claim/profile.ccpr",
                "test", "123");
    }

    @Test
    public void makeSuccessfulQueryRequestTest() {
        /*
        ModernHttpRequesterMock.setResponseCodes(new Integer[]{200});
        HttpRequestEndpointsMock.setCaseFetchResponseCodes(new Integer[]{200});
        DebugDataPullResponseFactory.setRequestPayloads(new String[]{"jr://resource/commcare-apps/case_search_and_claim/empty_restore.xml"});
        */

        AndroidSessionWrapper sessionWrapper =
                CommCareApplication._().getCurrentSessionWrapper();
        CommCareSession session = sessionWrapper.getSession();
        session.setCommand("patient-search");

        ShadowActivity shadowActivity =
                ActivityLaunchUtils.buildHomeActivity();

        Intent postActivityIntent = shadowActivity.getNextStartedActivity();

        String intentActivityName = postActivityIntent.getComponent().getClassName();
        assertTrue(intentActivityName.equals(PostRequestActivity.class.getName()));

        assertEquals("https://www.fake.com/claim_patient/", postActivityIntent.getStringExtra(PostRequestActivity.URL_KEY));
        Hashtable<String, String> postUrlParams =
                (Hashtable<String, String>)postActivityIntent.getSerializableExtra(PostRequestActivity.PARAMS_KEY);
        assertEquals("321", postUrlParams.get("selected_case_id"));

        PostRequestActivity postRequestActivity =
                Robolectric.buildActivity(PostRequestActivity.class).withIntent(postActivityIntent)
                        .create().start().resume().get();

        assertTrue(postRequestActivity.isFinishing());
    }
}
