package org.commcare.android.tests.activities;

import android.content.Intent;
import android.support.v4.util.Pair;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import junit.framework.Assert;

import org.commcare.CommCareApplication;
import org.commcare.CommCareTestApplication;
import org.commcare.activities.PostRequestActivity;
import org.commcare.activities.QueryRequestActivity;
import org.commcare.android.CommCareTestRunner;
import org.commcare.android.mocks.HttpRequestEndpointsMock;
import org.commcare.android.mocks.HttpURLConnectionMock;
import org.commcare.android.mocks.ModernHttpRequesterMock;
import org.commcare.android.util.ActivityLaunchUtils;
import org.commcare.android.util.TestAppInstaller;
import org.commcare.dalvik.BuildConfig;
import org.commcare.dalvik.R;
import org.commcare.models.AndroidSessionWrapper;
import org.commcare.session.CommCareSession;
import org.commcare.tasks.network.DebugDataPullResponseFactory;
import org.javarosa.core.model.instance.ExternalDataInstance;
import org.javarosa.core.services.locale.Localization;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowActivity;

import java.io.InputStream;
import java.util.Hashtable;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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
    public void postingToNonHttpsURLTest() {
        // NOTE PLM: we will eventually support 'http' urls, but won't include authentication credentials in them
        String badURL = "http://bad.url.com";

        PostRequestActivity postRequestActivity = buildPostActivity(badURL);

        assertErrorMessage(postRequestActivity, true, Localization.get("post.not.using.https", badURL));
    }

    private static void assertErrorMessage(PostRequestActivity postRequestActivity,
                                           boolean isVisible,
                                           String hasMessage) {
        TextView errorMessage =
                (TextView)postRequestActivity.findViewById(R.id.error_message);
        if (isVisible) {
            Assert.assertEquals(View.VISIBLE, errorMessage.getVisibility());
        } else {
            assertFalse(View.VISIBLE == errorMessage.getVisibility());
        }
        if (hasMessage != null) {
            Assert.assertEquals(hasMessage, errorMessage.getText());
        }
    }

    private static PostRequestActivity buildPostActivity(String url) {
        Intent postLaunchIntent = new Intent();
        if (url != null) {
            postLaunchIntent.putExtra(PostRequestActivity.URL_KEY, url);
            postLaunchIntent.putExtra(PostRequestActivity.PARAMS_KEY,
                    new Hashtable<String, String>());
        }
        return Robolectric.buildActivity(PostRequestActivity.class).withIntent(postLaunchIntent)
                .create().start().resume().get();
    }

    @Test
    public void postingToMalformedURLTest() {
        String badURL = "bad.url.com";

        PostRequestActivity postRequestActivity = buildPostActivity(badURL);

        assertErrorMessage(postRequestActivity, true, Localization.get("post.malformed.url", badURL));
    }

    @Test
    public void postingWithoutExtrasTest() {
        PostRequestActivity postRequestActivity = buildPostActivity(null);

        assertErrorMessage(postRequestActivity, true, Localization.get("post.generic.error"));
    }

    @Test
    public void unknownResponseFromServerTest() {
        int unknownResponseCode = 711028100;
        String expectedErrorMessage =
                Localization.get("post.unknown.response", unknownResponseCode + "");
        assertPostFailureMessage(expectedErrorMessage, 711028100);
    }

    private static void assertPostFailureMessage(String expectedErrorMessage,
                                                 int responseCode) {
        ModernHttpRequesterMock.setResponseCodes(new Integer[]{responseCode});

        PostRequestActivity postRequestActivity = buildPostActivity("https://www.fake.com");
        assertErrorMessage(postRequestActivity, true, expectedErrorMessage);
    }

    @Test
    public void redirectErrorResponseFromServerTest() {
        int responseCode = 320;
        assertPostFailureMessage(Localization.get("post.redirection.error", responseCode + ""), responseCode);
    }

    @Test
    public void clientErrorResponseFromServerTest() {
        int responseCode = 410;
        assertPostFailureMessage(Localization.get("post.client.error", responseCode + ""), responseCode);
    }

    @Test
    public void serverErrorResponseFromServerTest() {
        int responseCode = 550;
        assertPostFailureMessage(Localization.get("post.server.error", responseCode + ""), responseCode);
    }

    @Test
    public void ioErrorInResponseFromServerTest() {
        ModernHttpRequesterMock.setRequestPayloads(new String[]{null});
        assertPostFailureMessage(Localization.get("post.io.error", HttpURLConnectionMock.ioErrorMessage), 200);
    }

    @Test
    public void retryClaimTest() {
        ModernHttpRequesterMock.setResponseCodes(new Integer[]{500, 200});
        HttpRequestEndpointsMock.setCaseFetchResponseCodes(new Integer[]{200});
        DebugDataPullResponseFactory.setRequestPayloads(new String[]{"jr://resource/commcare-apps/case_search_and_claim/empty_restore.xml"});

        PostRequestActivity postRequestActivity = buildPostActivity("https://www.fake.com");

        Button retryButton = (Button)postRequestActivity.findViewById(R.id.request_button);
        Assert.assertEquals(View.VISIBLE, retryButton.getVisibility());
        retryButton.performClick();

        assertErrorMessage(postRequestActivity, false, null);

        assertTrue(postRequestActivity.isFinishing());
    }

    @Test
    public void makeSuccessfulPostRequestTest() {
        ModernHttpRequesterMock.setResponseCodes(new Integer[]{200});
        HttpRequestEndpointsMock.setCaseFetchResponseCodes(new Integer[]{200});
        DebugDataPullResponseFactory.setRequestPayloads(new String[]{"jr://resource/commcare-apps/case_search_and_claim/empty_restore.xml"});

        AndroidSessionWrapper sessionWrapper =
                CommCareApplication._().getCurrentSessionWrapper();
        CommCareSession session = sessionWrapper.getSession();
        session.setCommand("patient-search");
        InputStream is =
                PostRequestActivity.class.getClassLoader().getResourceAsStream("commcare-apps/case_search_and_claim/good-query-result.xml");
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

        assertTrue(postRequestActivity.isFinishing());
    }
}
