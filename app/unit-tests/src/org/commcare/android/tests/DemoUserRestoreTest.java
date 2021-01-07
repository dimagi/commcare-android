package org.commcare.android.tests;

import android.content.Intent;

import org.commcare.CommCareApplication;
import org.commcare.CommCareTestApplication;
import org.commcare.activities.DispatchActivity;
import org.commcare.activities.EntitySelectActivity;
import org.commcare.activities.LoginActivity;
import org.commcare.activities.StandardHomeActivity;
import org.commcare.adapters.EntityListAdapter;
import org.commcare.android.database.app.models.UserKeyRecord;
import org.commcare.android.util.ActivityLaunchUtils;
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

import androidx.test.ext.junit.runners.AndroidJUnit4;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Tests logging in as demo user with and without an app-level demo user
 * restore files
 *
 * @author Phillip Mates (pmates@dimagi.com)
 */
@Config(application = CommCareTestApplication.class)
@RunWith(AndroidJUnit4.class)
public class DemoUserRestoreTest {
    private final static String REF_BASE_DIR =
            "jr://resource/commcare-apps/demo_user_restore/";

    private static void loginAsDemoUser() {
        Intent loginActivityIntent =
                new Intent(RuntimeEnvironment.application, LoginActivity.class);
        LoginActivity loginActivity =
                Robolectric.buildActivity(LoginActivity.class, loginActivityIntent)
                        .setup().get();
        ShadowActivity shadowActivity = Shadows.shadowOf(loginActivity);
        shadowActivity.clickMenuItem(LoginActivity.MENU_DEMO);
    }

    private static ShadowActivity launchHomeActivityForDemoUser() {
        Intent homeActivityIntent =
                new Intent(RuntimeEnvironment.application, StandardHomeActivity.class);
        homeActivityIntent.putExtra(DispatchActivity.START_FROM_LOGIN, true);
        StandardHomeActivity homeActivity =
                Robolectric.buildActivity(StandardHomeActivity.class, homeActivityIntent)
                        .setup().get();
        return Shadows.shadowOf(homeActivity);
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
        ShadowActivity shadowActivity = launchHomeActivityForDemoUser();

        checkOptionsMenuVisibility(shadowActivity);

        AndroidSandbox sandbox = new AndroidSandbox(CommCareApplication.instance());
        IStorageUtilityIndexed<FormInstance> userFixtureStorage = sandbox.getUserFixtureStorage();
        assertEquals(1, userFixtureStorage.getNumRecords());

        assertEquals(1, CommCareApplication.instance().getCurrentApp().getStorage(UserKeyRecord.class).getNumRecords());

        EntitySelectActivity entitySelectActivity =
                ActivityLaunchUtils.launchEntitySelectActivity("m0-f0");

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
        entitySelectActivity = ActivityLaunchUtils.launchEntitySelectActivity("m0-f0");

        adapter = CaseLoadUtils.loadList(entitySelectActivity);
        assertEquals(1, adapter.getCount());
    }

    private void checkOptionsMenuVisibility(ShadowActivity shadowHomeActivity) {
        // Demo users shouldn't have an options menu apart from Change Language
        assertTrue(shadowHomeActivity.getOptionsMenu().findItem(StandardHomeActivity.MENU_CHANGE_LANGUAGE).isVisible());
        assertFalse(shadowHomeActivity.getOptionsMenu().findItem(StandardHomeActivity.MENU_UPDATE).isVisible());
        assertFalse(shadowHomeActivity.getOptionsMenu().findItem(StandardHomeActivity.MENU_SAVED_FORMS).isVisible());
        assertFalse(shadowHomeActivity.getOptionsMenu().findItem(StandardHomeActivity.MENU_PREFERENCES).isVisible());
        assertFalse(shadowHomeActivity.getOptionsMenu().findItem(StandardHomeActivity.MENU_ADVANCED).isVisible());
        assertFalse(shadowHomeActivity.getOptionsMenu().findItem(StandardHomeActivity.MENU_ABOUT).isVisible());
        assertFalse(shadowHomeActivity.getOptionsMenu().findItem(StandardHomeActivity.MENU_PIN).isVisible());
    }
}
