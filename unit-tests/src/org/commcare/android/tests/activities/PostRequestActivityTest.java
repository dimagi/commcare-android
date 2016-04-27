package org.commcare.android.tests.activities;

import android.content.Intent;
import android.support.v4.util.Pair;

import org.commcare.CommCareApplication;
import org.commcare.CommCareTestApplication;
import org.commcare.activities.PostRequestActivity;
import org.commcare.activities.QueryRequestActivity;
import org.commcare.android.CommCareTestRunner;
import org.commcare.android.mocks.ModernHttpRequesterMock;
import org.commcare.android.util.ActivityLaunchUtils;
import org.commcare.android.util.TestAppInstaller;
import org.commcare.dalvik.BuildConfig;
import org.commcare.models.AndroidSessionWrapper;
import org.commcare.session.CommCareSession;
import org.javarosa.core.model.instance.ExternalDataInstance;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowActivity;
import org.robolectric.shadows.ShadowToast;

import java.io.InputStream;
import java.util.Hashtable;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author Phillip Mates (pmates@dimagi.com)
 */
@Config(application = CommCareTestApplication.class,
        constants = BuildConfig.class)
@RunWith(CommCareTestRunner.class)
public class PostRequestActivityTest {
    @Before
    public void setup() {
        TestAppInstaller.installAppAndLogin(
                "jr://resource/commcare-apps/case_search_and_claim/profile.ccpr",
                "test", "123");
    }

    @Test
    public void makeSuccessfulPostRequestTest() {
        ModernHttpRequesterMock.setResponseCodes(new Integer[] {200});

        AndroidSessionWrapper sessionWrapper =
                CommCareApplication._().getCurrentSessionWrapper();
        CommCareSession session = sessionWrapper.getSession();
        session.setCommand("patient-search");
        InputStream is =
                PostRequestActivity.class.getClassLoader().getResourceAsStream("commcare-apps/case_search_and_claim/patients.xml");
        Pair<ExternalDataInstance, String> instanceOrError =
                QueryRequestActivity.buildExternalDataInstance(is, "patients");
        session.setQueryDatum(instanceOrError.first);
        session.setDatum("case_id", "321");

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
        Robolectric.flushBackgroundThreadScheduler();
        Robolectric.flushForegroundThreadScheduler();
        Assert.assertEquals("claim successful", ShadowToast.getTextOfLatestToast());

        /*
        ShadowActivity shadowFormEntryActivity = navigateFormEntry(formEntryIntent);

        // trigger CommCareHomeActivity.onActivityResult for the completion of
        // FormEntryActivity
        shadowActivity.receiveResult(formEntryIntent,
                shadowFormEntryActivity.getResultCode(),
                shadowFormEntryActivity.getResultIntent());
        */
    }
}
