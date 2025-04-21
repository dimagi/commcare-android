package org.commcare.android.tests.activities;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import org.commcare.CommCareApplication;
import org.commcare.CommCareTestApplication;
import org.commcare.activities.QueryRequestActivity;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.commcare.android.mocks.ModernHttpRequesterMock;
import org.commcare.android.util.TestAppInstaller;
import org.commcare.core.parse.ParseUtils;
import org.commcare.dalvik.R;
import org.commcare.models.AndroidSessionWrapper;
import org.commcare.models.database.AndroidSandbox;
import org.commcare.session.CommCareSession;
import org.commcare.suite.model.ComputedDatum;
import org.commcare.utils.RobolectricUtil;
import org.javarosa.core.services.locale.Localization;
import org.javarosa.xml.util.InvalidStructureException;
import org.javarosa.xml.util.UnfullfilledRequirementsException;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;
import org.robolectric.shadows.ShadowToast;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author Phillip Mates (pmates@dimagi.com)
 */
@Config(application = CommCareTestApplication.class)
@RunWith(AndroidJUnit4.class)
public class QueryRequestActivityTest {
    private static final String EXPECTED_CASE_SEARCH_URL =
            "https://www.fake.com/patient_search/?device_id=000000000000000&multi_state=ka&"
                    + "multi_state=up&patient_id=123&district=baran&name=francisco&state=rj";

    @Before
    public void setup() {
        TestAppInstaller.installAppAndLogin(
                "jr://resource/commcare-apps/case_search_and_claim/profile.ccpr",
                "test", "123");

        try {
            ParseUtils.parseIntoSandbox(
                    this.getClass().getResourceAsStream("/commcare-apps/case_search_and_claim/user_restore.xml"),
                    new AndroidSandbox(CommCareApplication.instance()));
        } catch (InvalidStructureException e) {
            e.printStackTrace();
        } catch (UnfullfilledRequirementsException e) {
            e.printStackTrace();
        } catch (XmlPullParserException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Launch the query activity when a query datum isn't at the next needed
     * datum in the session
     */
    @Test
    public void launchQueryActivityAtWrongTimeTest() {
        QueryRequestActivity queryRequestActivity = buildQueryActivity().get();

        assertEquals(AppCompatActivity.RESULT_CANCELED,
                Shadows.shadowOf(queryRequestActivity).getResultCode());
        assertTrue(queryRequestActivity.isFinishing());
    }

    /**
     * Launch query activity, fill out query prompts, make request, and store
     * result in session.  Checks request URL is built correctly.
     */
    @Test
    public void makeSuccessfulQueryRequestTest() {
        ModernHttpRequesterMock.setResponseCodes(new Integer[]{200});
        ModernHttpRequesterMock.setExpectedUrls(
                new String[]{EXPECTED_CASE_SEARCH_URL});
        ModernHttpRequesterMock.setRequestPayloads(
                new String[]{"jr://resource/commcare-apps/case_search_and_claim/good-query-result.xml"});

        QueryRequestActivity queryRequestActivity = buildActivityAndSetViews().get();
        triggerQueryRequest(queryRequestActivity);
        int resultCode = Shadows.shadowOf(queryRequestActivity).getResultCode();
        ShadowLooper.idleMainLooper();
        assertEquals(AppCompatActivity.RESULT_OK, resultCode);
        assertTrue(queryRequestActivity.isFinishing());
    }

    /**
     * Make query whose response isn't in the correct format and check the
     * error message displayed
     */
    @Test
    public void makeQueryWithBadServerPayloadTest() {
        ModernHttpRequesterMock.setResponseCodes(new Integer[]{200});
        ModernHttpRequesterMock.setExpectedUrls(
                new String[]{EXPECTED_CASE_SEARCH_URL});
        ModernHttpRequesterMock.setRequestPayloads(
                new String[]{"jr://resource/commcare-apps/case_search_and_claim/bad-query-result.xml"});

        ActivityController<QueryRequestActivity> controller = buildActivityAndSetViews();
        QueryRequestActivity queryRequestActivity = controller.get();
        triggerQueryRequest(queryRequestActivity);
        TextView errorMessage = queryRequestActivity.findViewById(R.id.error_message);
        assertEquals(View.VISIBLE, errorMessage.getVisibility());
        String expectedErrorPart = Localization.get("query.response.format.error", "");
        assertTrue(((String)errorMessage.getText()).contains(expectedErrorPart));

        // serialize app state into bundle
        Bundle savedInstanceState = new Bundle();
        controller.saveInstanceState(savedInstanceState);

        // start new activity with serialized app state
        Intent queryActivityIntent =
                new Intent(ApplicationProvider.getApplicationContext(), QueryRequestActivity.class);
        queryRequestActivity = Robolectric.buildActivity(QueryRequestActivity.class, queryActivityIntent)
                .setup(savedInstanceState).get();

        // check that the error message is still there
        errorMessage = queryRequestActivity.findViewById(R.id.error_message);
        assertEquals(View.VISIBLE, errorMessage.getVisibility());
        assertTrue(((String)errorMessage.getText()).contains(expectedErrorPart));
    }

    @Test
    public void spinnersInterDependencyTest() {
        setSessionCommand("patient-search-complex");
        QueryRequestActivity queryRequestActivity = buildQueryActivity().get();
        LinearLayout promptsLayout = queryRequestActivity.findViewById(R.id.query_prompts);
        Spinner stateSpinner = promptsLayout.getChildAt(2).findViewById(R.id.prompt_spinner);
        Spinner districtSpinner = promptsLayout.getChildAt(3).findViewById(R.id.prompt_spinner);

        // Confirm Start State
        assertEquals(4, stateSpinner.getAdapter().getCount());
        assertEquals("", stateSpinner.getAdapter().getItem(0));
        assertEquals("karnataka", stateSpinner.getAdapter().getItem(1));
        assertEquals("Raj as than", stateSpinner.getAdapter().getItem(2));
        assertEquals("Uttar Pradesh", stateSpinner.getAdapter().getItem(3));
        assertEquals(1, districtSpinner.getAdapter().getCount());
        assertEquals("", districtSpinner.getAdapter().getItem(0));

        // Changes to state should filter district spinner accordingly
        stateSpinner.setSelection(1);
        assertEquals(3, districtSpinner.getAdapter().getCount());
        assertEquals("", districtSpinner.getAdapter().getItem(0));
        assertEquals("Bangalore", districtSpinner.getAdapter().getItem(1));
        assertEquals("Hampi", districtSpinner.getAdapter().getItem(2));

        stateSpinner.setSelection(2);
        assertEquals(3, districtSpinner.getAdapter().getCount());
        assertEquals("", districtSpinner.getAdapter().getItem(0));
        assertEquals("Baran", districtSpinner.getAdapter().getItem(1));
        assertEquals("Kota", districtSpinner.getAdapter().getItem(2));

        // changes to district doesn't affect state
        districtSpinner.setSelection(2);
        assertEquals(4, stateSpinner.getAdapter().getCount());
        assertEquals("", stateSpinner.getAdapter().getItem(0));
        assertEquals("karnataka", stateSpinner.getAdapter().getItem(1));
        assertEquals("Raj as than", stateSpinner.getAdapter().getItem(2));
        assertEquals("Uttar Pradesh", stateSpinner.getAdapter().getItem(3));
        assertEquals(2, stateSpinner.getSelectedItemPosition());

    }

    /**
     * Start filling out query parameters and then 'rotate' the screen to see
     * if text entry is restored
     */
    @Test
    public void reloadQueryActivityStateTest() {
        setSessionCommand("patient-search-complex");
        ActivityController<QueryRequestActivity> controller = buildActivityAndSetViews();

        // serialize app state into bundle
        Bundle savedInstanceState = new Bundle();
        controller.saveInstanceState(savedInstanceState);

        // start new activity with serialized app state
        Intent queryActivityIntent =
                new Intent(ApplicationProvider.getApplicationContext(), QueryRequestActivity.class);
        QueryRequestActivity queryRequestActivity = Robolectric.buildActivity(QueryRequestActivity.class, queryActivityIntent)
                .setup(savedInstanceState).get();

        // check that the query prompts are filled out still
        LinearLayout promptsLayout = queryRequestActivity.findViewById(R.id.query_prompts);
        EditText patientId = promptsLayout.getChildAt(1).findViewById(R.id.prompt_et);
        assertEquals("123", patientId.getText().toString());

        EditText patientName = promptsLayout.getChildAt(0).findViewById(R.id.prompt_et);
        assertEquals("francisco", patientName.getText().toString());

        Spinner stateSpinner = promptsLayout.getChildAt(2).findViewById(R.id.prompt_spinner);
        assertEquals(2, stateSpinner.getSelectedItemPosition());

        Spinner districtSpinner = promptsLayout.getChildAt(3).findViewById(R.id.prompt_spinner);
        assertEquals(1, districtSpinner.getSelectedItemPosition());

        LinearLayout checkboxView = promptsLayout.getChildAt(5).findViewById(R.id.prompt_checkbox);
        assertTrue(((CheckBox)checkboxView.getChildAt(0)).isChecked());
        assertFalse(((CheckBox)checkboxView.getChildAt(1)).isChecked());
        assertTrue(((CheckBox)checkboxView.getChildAt(2)).isChecked());
    }

    /**
     * Make query with empty response, which should result in a toast, not advancing forward
     */
    @Test
    public void receiveEmptyQueryResultTest() {
        ModernHttpRequesterMock.setResponseCodes(new Integer[]{200, 200, 200});
        ModernHttpRequesterMock.setExpectedUrls(
                new String[]{"https://www.fake.com/patient_search/?name=francisco&device_id=000000000000000"});
        ModernHttpRequesterMock.setRequestPayloads(
                new String[]{"jr://resource/commcare-apps/case_search_and_claim/empty-query-result-one-tag.xml",
                        "jr://resource/commcare-apps/case_search_and_claim/empty-query-result-two-tags.xml",
                        "jr://resource/commcare-apps/case_search_and_claim/single-query-result.xml"});

        setSessionCommand("patient-search-complex");
        QueryRequestActivity queryRequestActivity = buildQueryActivity().get();

        LinearLayout promptsLayout =
                queryRequestActivity.findViewById(R.id.query_prompts);
        EditText patientName = promptsLayout.getChildAt(0).findViewById(R.id.prompt_et);
        patientName.setText("francisco");

        EditText patientId = promptsLayout.getChildAt(1).findViewById(R.id.prompt_et);
        patientId.setText("");

        triggerQueryRequest(queryRequestActivity);

        Assert.assertEquals(Localization.get("query.response.empty"),
                ShadowToast.getTextOfLatestToast());

        triggerQueryRequest(queryRequestActivity);
        Assert.assertEquals(Localization.get("query.response.empty"),
                ShadowToast.getTextOfLatestToast());

        triggerQueryRequest(queryRequestActivity);
        assertEquals(AppCompatActivity.RESULT_OK,
                Shadows.shadowOf(queryRequestActivity).getResultCode());
        assertTrue(queryRequestActivity.isFinishing());
    }

    private static void setSessionCommand(String command) {
        AndroidSessionWrapper sessionWrapper =
                CommCareApplication.instance().getCurrentSessionWrapper();
        CommCareSession session = sessionWrapper.getSession();
        session.setCommand(command);
        if (session.getNeededDatum() instanceof ComputedDatum) {
            session.setComputedDatum(sessionWrapper.getEvaluationContext());
        }
    }

    private ActivityController<QueryRequestActivity> buildActivityAndSetViews() {
        setSessionCommand("patient-search-complex");

        ActivityController<QueryRequestActivity> controller = buildQueryActivity();
        QueryRequestActivity queryRequestActivity = controller.get();

        // set views
        LinearLayout promptsLayout = queryRequestActivity.findViewById(R.id.query_prompts);

        assertEquals(6, promptsLayout.getChildCount());

        EditText patientName = promptsLayout.getChildAt(0).findViewById(R.id.prompt_et);
        patientName.setText("francisco");

        //Patient ID is autoloaded from the default value pulled from the case

        Spinner stateSpinner = promptsLayout.getChildAt(2).findViewById(R.id.prompt_spinner);
        stateSpinner.setSelection(2);

        Spinner districtSpinner = promptsLayout.getChildAt(3).findViewById(R.id.prompt_spinner);
        districtSpinner.setSelection(1);

        LinearLayout checkboxView = promptsLayout.getChildAt(5).findViewById(R.id.prompt_checkbox);
        ((CheckBox)checkboxView.getChildAt(0)).setChecked(true);
        ((CheckBox)checkboxView.getChildAt(2)).setChecked(true);

        return controller;
    }

    private ActivityController<QueryRequestActivity> buildQueryActivity() {
        Intent queryActivityIntent =
                new Intent(ApplicationProvider.getApplicationContext(), QueryRequestActivity.class);
        ActivityController<QueryRequestActivity> controller =
                Robolectric.buildActivity(QueryRequestActivity.class, queryActivityIntent).setup();
        return controller;
    }

    private void triggerQueryRequest(QueryRequestActivity queryRequestActivity) {
        Button queryButton = queryRequestActivity.findViewById(R.id.request_button);
        queryButton.performClick();
        RobolectricUtil.flushBackgroundThread(queryRequestActivity);
    }
}
