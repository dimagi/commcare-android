package org.commcare.recovery.measures;

import android.content.Intent;

import org.commcare.CommCareApplication;
import org.commcare.CommCareTestApplication;
import org.commcare.activities.DispatchActivity;
import org.commcare.activities.LoginActivity;
import org.commcare.activities.PromptApkUpdateActivity;
import org.commcare.activities.PromptCCReinstallActivity;
import org.commcare.activities.StandardHomeActivity;
import org.commcare.android.CommCareTestRunner;
import org.commcare.android.util.TestAppInstaller;
import org.commcare.models.database.SqlStorage;
import org.commcare.preferences.HiddenPreferences;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowActivity;
import org.robolectric.util.ActivityController;

import java.util.List;

import static junit.framework.Assert.assertTrue;

@Config(application = CommCareTestApplication.class)
@RunWith(CommCareTestRunner.class)
public class RecoveryMeasuresTest {

    private static final String REINSTALL_AND_UPDATE_VALID_FOR_CURRENT_APP_VERSION =  "{\"app_id\":\"ac46998a182d2e1d1fd8e75684d23903\",\"recovery_measures\": " +
            "[{\"sequence_number\":1, \"type\":\"app_reinstall_and_update\", \"cc_version_min\":\"2.44.0\", " +
            "\"cc_version_max\":\"2.90.0\", \"app_version_min\":0, \"latest_app_version\":9," +
            "\"latest_cc_version\":\"2.91.0\", \"app_version_max\":6} ]}";

    private static final String REINSTALL_AND_UPDATE_NOT_VALID_FOR_CURRENT_APP_VERSION =  "{\"app_id\":\"ac46998a182d2e1d1fd8e75684d23903\",\"recovery_measures\": " +
            "[{\"sequence_number\":2, \"type\":\"app_reinstall_and_update\", \"cc_version_min\":\"2.44.0\", " +
            "\"cc_version_max\":\"2.90.0\", \"app_version_min\":0, \"latest_app_version\":9," +
            "\"latest_cc_version\":\"2.91.0\", \"app_version_max\":5} ]}";

    private static final String CC_REINSTALL_INVALID_FOR_CURRENT_CC_VERSION =  "{\"app_id\":\"ac46998a182d2e1d1fd8e75684d23903\",\"recovery_measures\": " +
            "[{\"sequence_number\":3, \"type\":\"cc_reinstall\", \"cc_version_min\":\"2.44.0\", " +
            "\"cc_version_max\":\"2.44.0\", \"app_version_min\":0, \"latest_app_version\":9," +
            "\"latest_cc_version\":\"2.91.0\", \"app_version_max\":6} ]}";

    private static final String CC_REINSTALL_VALID_FOR_ALL_CC_VERSION =  "{\"app_id\":\"ac46998a182d2e1d1fd8e75684d23903\",\"recovery_measures\": " +
            "[{\"sequence_number\":4, \"type\":\"cc_reinstall\", \"app_version_min\":0, \"latest_app_version\":9," +
            "\"latest_cc_version\":\"2.91.0\", \"app_version_max\":6} ]}";

    private static final String APP_UPDATE_VALID_FOR_ALL_APP_VERSION =  "{\"app_id\":\"ac46998a182d2e1d1fd8e75684d23903\",\"recovery_measures\": " +
            "[{\"sequence_number\":5, \"type\":\"app_update\", \"cc_version_min\":\"2.44.0\", " +
            "\"cc_version_max\":\"2.90.0\", \"latest_app_version\":9," +
            "\"latest_cc_version\":\"2.91.0\"} ]}";

    private static final String VALID_FOR_ALL_HENCE_INVALID =  "{\"app_id\":\"ac46998a182d2e1d1fd8e75684d23903\",\"recovery_measures\": " +
            "[{\"sequence_number\":6, \"type\":\"app_update\", \"latest_app_version\":9," +
            "\"latest_cc_version\":\"2.91.0\"} ]}";

    private static final String CC_UPDATE_INVALID_DUE_TO_LATEST_VERSION =  "{\"app_id\":\"ac46998a182d2e1d1fd8e75684d23903\",\"recovery_measures\": " +
            "[{\"sequence_number\":7, \"type\":\"cc_update\", \"app_version_min\":0, \"latest_app_version\":9," +
            "\"latest_cc_version\":\"2.44.0\", \"app_version_max\":6} ]}";

    private static final String APP_UPDATE_INVALID_DUE_TO_LATEST_VERSION =  "{\"app_id\":\"ac46998a182d2e1d1fd8e75684d23903\",\"recovery_measures\": " +
            "[{\"sequence_number\":8, \"type\":\"app_update\", \"cc_version_min\":\"2.44.0\", " +
            "\"cc_version_max\":\"2.90.0\", \"latest_app_version\":6," +
            "\"latest_cc_version\":\"2.91.0\"} ]}";

    private static final String CC_UPDATE_VALID =  "{\"app_id\":\"ac46998a182d2e1d1fd8e75684d23903\",\"recovery_measures\": " +
            "[{\"sequence_number\":9, \"type\":\"cc_update\", \"app_version_min\":0, \"latest_app_version\":6," +
            "\"latest_cc_version\":\"2.91.0\", \"app_version_max\":6} ]}";


    @Before
    public void setup() {
        // The app version for this app is 95
        TestAppInstaller.installAppAndLogin(
                "jr://resource/commcare-apps/update_tests/base_app/profile.ccpr",
                "test",
                "123");
    }

    @Test
    public void OnlyValidRecoveryMeasures_ShouldGetAdded() {
        requestRecoveryMeasures(REINSTALL_AND_UPDATE_VALID_FOR_CURRENT_APP_VERSION);
        requestRecoveryMeasures(REINSTALL_AND_UPDATE_NOT_VALID_FOR_CURRENT_APP_VERSION);
        requestRecoveryMeasures(CC_REINSTALL_INVALID_FOR_CURRENT_CC_VERSION);
        requestRecoveryMeasures(CC_REINSTALL_VALID_FOR_ALL_CC_VERSION);
        requestRecoveryMeasures(APP_UPDATE_VALID_FOR_ALL_APP_VERSION);
        requestRecoveryMeasures(VALID_FOR_ALL_HENCE_INVALID);
        requestRecoveryMeasures(CC_UPDATE_INVALID_DUE_TO_LATEST_VERSION);
        requestRecoveryMeasures(APP_UPDATE_INVALID_DUE_TO_LATEST_VERSION);
        requestRecoveryMeasures(CC_UPDATE_VALID);

        // Verify that only the valid measures has been registered
        SqlStorage<RecoveryMeasure> recoveryMeasureSqlStorage = CommCareApplication.instance().getAppStorage(RecoveryMeasure.class);
        List<RecoveryMeasure> recoveryMeasures = RecoveryMeasuresHelper.getPendingRecoveryMeasuresInOrder(recoveryMeasureSqlStorage);
        assertTrue(recoveryMeasures.size() == 4);
        assertTrue(recoveryMeasures.get(0).getSequenceNumber() == 1);
        assertTrue(recoveryMeasures.get(1).getSequenceNumber() == 4);
        assertTrue(recoveryMeasures.get(2).getSequenceNumber() == 5);
        assertTrue(recoveryMeasures.get(3).getSequenceNumber() == 9);
    }

    @Test
    public void RecoveryMeasuresBeforeLastSuccesfulSequence_ShouldGetDiscarded() {
        HiddenPreferences.setLatestRecoveryMeasureExecuted(5);
        requestRecoveryMeasures(REINSTALL_AND_UPDATE_VALID_FOR_CURRENT_APP_VERSION);
        requestRecoveryMeasures(CC_REINSTALL_VALID_FOR_ALL_CC_VERSION);
        requestRecoveryMeasures(APP_UPDATE_VALID_FOR_ALL_APP_VERSION);
        requestRecoveryMeasures(CC_UPDATE_VALID);

        // Verify that only the valid measures has been registered
        SqlStorage<RecoveryMeasure> recoveryMeasureSqlStorage = CommCareApplication.instance().getAppStorage(RecoveryMeasure.class);
        List<RecoveryMeasure> recoveryMeasures = RecoveryMeasuresHelper.getPendingRecoveryMeasuresInOrder(recoveryMeasureSqlStorage);
        assertTrue(recoveryMeasures.size() == 1);
        assertTrue(recoveryMeasures.get(0).getSequenceNumber() == 9);
    }

    @Test
    public void LoginActivity_ShouldFinish_WhenRecoveryMeasuresArePending() {
        requestRecoveryMeasures(REINSTALL_AND_UPDATE_VALID_FOR_CURRENT_APP_VERSION);
        LoginActivity loginActivity =
                Robolectric.buildActivity(LoginActivity.class, null).create().resume().get();
        ShadowActivity shadowActivity = Shadows.shadowOf(loginActivity);
        assertTrue(shadowActivity.isFinishing());
    }

    @Test
    public void executeRecoveryMeasuresActivity_ShouldGetLaunched_WhenRecoveryMeasuresArePending() {
        requestRecoveryMeasures(REINSTALL_AND_UPDATE_VALID_FOR_CURRENT_APP_VERSION);

        // Set resources as valid so that dispatch doesn't fire VerificationActivity
        CommCareApplication.instance().getCurrentApp().getAppRecord().setResourcesStatus(true);
        ActivityController<DispatchActivity> dispatchActivityController
                = Robolectric.buildActivity(DispatchActivity.class).create().resume().start();

        DispatchActivity dispatchActivity = dispatchActivityController.get();
        ShadowActivity shadowDispatchActivity = Shadows.shadowOf(dispatchActivity);

        // Verify that Dispatch fires HomeActivity
        Intent homeActivityIntent = shadowDispatchActivity.getNextStartedActivity();
        String intentActivityName = homeActivityIntent.getComponent().getClassName();
        assertTrue(intentActivityName.contentEquals(StandardHomeActivity.class.getName()));

        // launch home activty
        StandardHomeActivity homeActivity =
                Robolectric.buildActivity(StandardHomeActivity.class).withIntent(homeActivityIntent)
                        .create().start().resume().get();
        ShadowActivity shadowHomeActivity = Shadows.shadowOf(homeActivity);

        // Verify HomeActivity finishes since recovery measures are pending
        assertTrue(shadowHomeActivity.isFinishing());

        shadowDispatchActivity.receiveResult(homeActivityIntent,
                shadowHomeActivity.getResultCode(),
                shadowHomeActivity.getResultIntent());

        // Verify that Dispatch fires up the ExecuteRecoveryMeasuresActivity this time
        dispatchActivityController.resume();
        Intent executeRecoveryMeasuresActivityIntent = shadowDispatchActivity.getNextStartedActivity();
        intentActivityName  = executeRecoveryMeasuresActivityIntent.getComponent().getClassName();
        assertTrue(intentActivityName.contentEquals(ExecuteRecoveryMeasuresActivity.class.getName()));
    }


    @Test
    public void ccReinstallMeasure_ShouldlaunchCCReinstallPrompt() {
        requestRecoveryMeasures(CC_REINSTALL_VALID_FOR_ALL_CC_VERSION);
        ShadowActivity shadowRecoveryMeasuresActivity = launchRecoveryMeasuresActivity();
        Intent intent = shadowRecoveryMeasuresActivity.getNextStartedActivity();
        assertTrue(getIntentActivityName(intent).contentEquals(PromptCCReinstallActivity.class.getName()));
    }

    @Test
    public void ccUpdateMeasure_ShouldlaunchAokUpdatePrompt() {
        requestRecoveryMeasures(CC_UPDATE_VALID);
        ShadowActivity shadowRecoveryMeasuresActivity = launchRecoveryMeasuresActivity();
        Intent intent = shadowRecoveryMeasuresActivity.getNextStartedActivity();
        assertTrue(getIntentActivityName(intent).contentEquals(PromptApkUpdateActivity.class.getName()));
    }

    private String getIntentActivityName(Intent intent) {
        return intent.getComponent().getClassName();
    }

    private ShadowActivity launchRecoveryMeasuresActivity() {
        ExecuteRecoveryMeasuresActivity executeRecoveryMeasuresActivity =
                Robolectric.buildActivity(ExecuteRecoveryMeasuresActivity.class).create().get();
        return Shadows.shadowOf(executeRecoveryMeasuresActivity);
    }

    private static void requestRecoveryMeasures(String responseStringToUse) {
        requestRecoveryMeasures(new String[]{responseStringToUse});
    }

    private static void requestRecoveryMeasures(String[] responseStringsToUse) {
        TestRecoveryMeasureRequester.setNextResponseStrings(responseStringsToUse);
        new TestRecoveryMeasureRequester().makeRequest();
    }
}
