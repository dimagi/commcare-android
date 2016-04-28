package org.commcare.android.tests.activities;

import android.app.Activity;
import android.content.Intent;
import android.opengl.Visibility;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.commcare.CommCareApplication;
import org.commcare.CommCareTestApplication;
import org.commcare.activities.PostRequestActivity;
import org.commcare.activities.QueryRequestActivity;
import org.commcare.android.CommCareTestRunner;
import org.commcare.android.mocks.ModernHttpRequesterMock;
import org.commcare.android.util.ActivityLaunchUtils;
import org.commcare.android.util.TestAppInstaller;
import org.commcare.dalvik.BuildConfig;
import org.commcare.dalvik.R;
import org.commcare.models.AndroidSessionWrapper;
import org.commcare.session.CommCareSession;
import org.commcare.tasks.network.DebugDataPullResponseFactory;
import org.javarosa.core.services.locale.Localization;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowActivity;
import org.robolectric.util.ActivityController;

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
    public void launchQueryActivityAtWrongTimeTest() {
        Intent queryActivityIntent =
                new Intent(RuntimeEnvironment.application, QueryRequestActivity.class);
        QueryRequestActivity queryRequestActivity =
                Robolectric.buildActivity(QueryRequestActivity.class).withIntent(queryActivityIntent)
                        .create().start().resume().get();

        assertEquals(Activity.RESULT_CANCELED,
                Shadows.shadowOf(queryRequestActivity).getResultCode());
        assertTrue(queryRequestActivity.isFinishing());
    }

    @Test
    public void makeSuccessfulQueryRequestTest() {
        ModernHttpRequesterMock.setResponseCodes(new Integer[]{200});
        ModernHttpRequesterMock.setExpectedUrls(new String[]{"https://www.fake.com/patient_search/?patient_id=123&name=francisco&device_id=000000000000000"});
        ModernHttpRequesterMock.setRequestPayloads(new String[]{"jr://resource/commcare-apps/case_search_and_claim/good-query-result.xml"});

        AndroidSessionWrapper sessionWrapper =
                CommCareApplication._().getCurrentSessionWrapper();
        CommCareSession session = sessionWrapper.getSession();
        session.setCommand("patient-search");

        Intent queryActivityIntent =
                new Intent(RuntimeEnvironment.application, QueryRequestActivity.class);
        QueryRequestActivity queryRequestActivity =
                Robolectric.buildActivity(QueryRequestActivity.class).withIntent(queryActivityIntent)
                        .create().start().resume().get();

        LinearLayout promptsLayout = (LinearLayout)queryRequestActivity.findViewById(R.id.query_prompts);
        EditText patientId = (EditText)promptsLayout.getChildAt(1);
        patientId.setText("123");
        EditText patientName = (EditText)promptsLayout.getChildAt(3);
        patientName.setText("francisco");

        Button queryButton = (Button)queryRequestActivity.findViewById(R.id.request_button);
        queryButton.performClick();

        assertEquals(Activity.RESULT_OK,
                Shadows.shadowOf(queryRequestActivity).getResultCode());
        assertTrue(queryRequestActivity.isFinishing());
    }

    @Test
    public void makeQueryWithBadServerPayloadTest() {
        ModernHttpRequesterMock.setResponseCodes(new Integer[]{200});
        ModernHttpRequesterMock.setExpectedUrls(new String[]{"https://www.fake.com/patient_search/?patient_id=123&name=francisco&device_id=000000000000000"});
        ModernHttpRequesterMock.setRequestPayloads(new String[]{"jr://resource/commcare-apps/case_search_and_claim/bad-query-result.xml"});

        AndroidSessionWrapper sessionWrapper =
                CommCareApplication._().getCurrentSessionWrapper();
        CommCareSession session = sessionWrapper.getSession();
        session.setCommand("patient-search");

        Intent queryActivityIntent =
                new Intent(RuntimeEnvironment.application, QueryRequestActivity.class);

        ActivityController<QueryRequestActivity> controller =
                Robolectric.buildActivity(QueryRequestActivity.class)
                        .withIntent(queryActivityIntent).create().start().resume();
        QueryRequestActivity queryRequestActivity = controller.get();

        LinearLayout promptsLayout = (LinearLayout)queryRequestActivity.findViewById(R.id.query_prompts);
        EditText patientId = (EditText)promptsLayout.getChildAt(1);
        patientId.setText("123");
        EditText patientName = (EditText)promptsLayout.getChildAt(3);
        patientName.setText("francisco");

        Button queryButton = (Button)queryRequestActivity.findViewById(R.id.request_button);
        queryButton.performClick();

        TextView errorMessage = (TextView)queryRequestActivity.findViewById(R.id.error_message);
        assertEquals(View.VISIBLE, errorMessage.getVisibility());
        assertTrue(((String)errorMessage.getText()).contains(Localization.get("query.response.format.error", "")));

        Bundle savedInstanceState = new Bundle();
        controller.saveInstanceState(savedInstanceState);

        queryRequestActivity = Robolectric.buildActivity(QueryRequestActivity.class)
                .withIntent(queryActivityIntent).create(savedInstanceState).start().resume().get();

        errorMessage = (TextView)queryRequestActivity.findViewById(R.id.error_message);
        assertEquals(View.VISIBLE, errorMessage.getVisibility());
        assertTrue(((String)errorMessage.getText()).contains(Localization.get("query.response.format.error", "")));
    }

    @Test
    public void reloadQueryActivityStateTest() {
        AndroidSessionWrapper sessionWrapper =
                CommCareApplication._().getCurrentSessionWrapper();
        CommCareSession session = sessionWrapper.getSession();
        session.setCommand("patient-search");

        Intent queryActivityIntent =
                new Intent(RuntimeEnvironment.application, QueryRequestActivity.class);

        ActivityController<QueryRequestActivity> controller =
                Robolectric.buildActivity(QueryRequestActivity.class)
                        .withIntent(queryActivityIntent).create().start().resume();
        QueryRequestActivity queryRequestActivity = controller.get();

        LinearLayout promptsLayout = (LinearLayout)queryRequestActivity.findViewById(R.id.query_prompts);
        EditText patientId = (EditText)promptsLayout.getChildAt(1);
        patientId.setText("123");

        Bundle savedInstanceState = new Bundle();
        controller.saveInstanceState(savedInstanceState);

        queryRequestActivity = Robolectric.buildActivity(QueryRequestActivity.class)
                .withIntent(queryActivityIntent).create(savedInstanceState).start().resume().get();
        promptsLayout = (LinearLayout)queryRequestActivity.findViewById(R.id.query_prompts);
        patientId = (EditText)promptsLayout.getChildAt(1);
        assertEquals("123", patientId.getText().toString());

        patientId = (EditText)promptsLayout.getChildAt(3);
        assertEquals("", patientId.getText().toString());
    }
}
