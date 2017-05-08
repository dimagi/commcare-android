package org.commcare.android.tests;

import android.content.Intent;

import org.commcare.CommCareApplication;
import org.commcare.CommCareTestApplication;
import org.commcare.activities.StandardHomeActivity;
import org.commcare.activities.DispatchActivity;
import org.commcare.activities.EntitySelectActivity;
import org.commcare.activities.LoginActivity;
import org.commcare.adapters.EntityListAdapter;
import org.commcare.android.CommCareTestRunner;
import org.commcare.android.database.app.models.UserKeyRecord;
import org.commcare.android.util.CaseLoadUtils;
import org.commcare.android.util.TestAppInstaller;
import org.commcare.android.util.UpdateUtils;
import org.commcare.engine.resource.AppInstallStatus;
import org.commcare.models.database.AndroidSandbox;
import org.javarosa.core.model.instance.FormInstance;
import org.javarosa.core.services.storage.IStorageUtilityIndexed;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowActivity;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

/**
 * Tests logging in as demo user with and without an app-level demo user
 * restore files
 *
 * @author Phillip Mates (pmates@dimagi.com)
 */
@Config(application = CommCareTestApplication.class)
@RunWith(CommCareTestRunner.class)
public class DemoUserRestoreTest {
    private final static String REF_BASE_DIR =
            "jr://resource/commcare-apps/demo_user_restore/";

    @Test
    public void loginUsingDemoUserWithoutRestore() {
        TestAppInstaller.installApp(REF_BASE_DIR +
                "app_without_demo_user_restore/profile.ccpr");
        CommCareApplication.instance().getCurrentApp().setMMResourcesValidated();
        loginAsDemoUser();
        launchHomeActivityForDemoUser();
    }

    private static void loginAsDemoUser() {
        Intent loginActivityIntent =
                new Intent(RuntimeEnvironment.application, LoginActivity.class);
        LoginActivity loginActivity =
                Robolectric.buildActivity(LoginActivity.class)
                        .withIntent(loginActivityIntent).setup().get();
        ShadowActivity shadowActivity = Shadows.shadowOf(loginActivity);
        shadowActivity.clickMenuItem(LoginActivity.MENU_DEMO);
    }

    private static void launchHomeActivityForDemoUser() {
        Intent homeActivityIntent =
                new Intent(RuntimeEnvironment.application, StandardHomeActivity.class);
        homeActivityIntent.putExtra(DispatchActivity.START_FROM_LOGIN, true);
        StandardHomeActivity homeActivity =
                Robolectric.buildActivity(StandardHomeActivity.class)
                        .withIntent(homeActivityIntent).setup().get();
        ShadowActivity shadowActivity = Shadows.shadowOf(homeActivity);

        // Demo users shouldn't have an options menu
        assertFalse(shadowActivity.getOptionsMenu().hasVisibleItems());
    }

    /**
     * Install app w/ demo user restore file, login as demo user. Perform app
     * update that changes the user restore (including the username), assert
     * that associated fixture and case data is correctly updated
     */
    @Test
    public void demoUserRestoreAndUpdateTest() {
        TestAppInstaller.installApp(REF_BASE_DIR +
                "app_with_demo_user_restore/profile.ccpr");
        CommCareApplication.instance().getCurrentApp().setMMResourcesValidated();

        loginAsDemoUser();
        launchHomeActivityForDemoUser();

        AndroidSandbox sandbox = new AndroidSandbox(CommCareApplication.instance());
        IStorageUtilityIndexed<FormInstance> userFixtureStorage = sandbox.getUserFixtureStorage();
        assertEquals(1, userFixtureStorage.getNumRecords());

        assertEquals(1, CommCareApplication.instance().getCurrentApp().getStorage(UserKeyRecord.class).getNumRecords());

        EntitySelectActivity entitySelectActivity =
                CaseLoadUtils.launchEntitySelectActivity("m0-f0");

        // check that the demo user has 2 entries in the case list
        EntityListAdapter adapter = CaseLoadUtils.loadList(entitySelectActivity);
        assertEquals(2, adapter.getCount());

        // update the app to a version with a new demo user restore
        String profileRef = UpdateUtils.buildResourceRef(REF_BASE_DIR,
                "update_user_restore", "profile.ccpr");
        UpdateUtils.installUpdate(profileRef,
                AppInstallStatus.UpdateStaged,
                AppInstallStatus.Installed);

        loginAsDemoUser();
        launchHomeActivityForDemoUser();

        // check that the user fixtures were updated
        userFixtureStorage = sandbox.getUserFixtureStorage();
        assertEquals(0, userFixtureStorage.getNumRecords());

        assertEquals(1, CommCareApplication.instance().getCurrentApp().getStorage(UserKeyRecord.class).getNumRecords());

        // make sure there is only 1 case after updating the demo user restore
        entitySelectActivity = CaseLoadUtils.launchEntitySelectActivity("m0-f0");

        adapter = CaseLoadUtils.loadList(entitySelectActivity);
        assertEquals(1, adapter.getCount());
    }
}
