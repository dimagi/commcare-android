package org.commcare.android.tests;

import android.content.Intent;

import org.commcare.CommCareApplication;
import org.commcare.CommCareTestApplication;
import org.commcare.activities.CommCareHomeActivity;
import org.commcare.activities.DispatchActivity;
import org.commcare.activities.EntitySelectActivity;
import org.commcare.activities.LoginActivity;
import org.commcare.adapters.EntityListAdapter;
import org.commcare.android.CommCareTestRunner;
import org.commcare.android.util.CaseLoadUtils;
import org.commcare.android.util.TestAppInstaller;
import org.commcare.android.util.UpdateUtils;
import org.commcare.dalvik.BuildConfig;
import org.commcare.engine.resource.AppInstallStatus;
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
 * @author Phillip Mates (pmates@dimagi.com)
 */
@Config(application = CommCareTestApplication.class,
        constants = BuildConfig.class)
@RunWith(CommCareTestRunner.class)
public class DemoUserRestoreTest {
    private final static String REF_BASE_DIR =
            "jr://resource/commcare-apps/demo_user_restore/";

    @Test
    public void loginUsingDemoUserWithoutRestore() {
        TestAppInstaller.installApp(REF_BASE_DIR +
                "app_without_demo_user_restore/profile.ccpr");
        CommCareApplication._().getCurrentApp().setMMResourcesValidated();

        loginAsDemoUser();

        launchHomeActivity();
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

    private static void launchHomeActivity() {
        Intent homeActivityIntent =
                new Intent(RuntimeEnvironment.application, CommCareHomeActivity.class);
        homeActivityIntent.putExtra(DispatchActivity.START_FROM_LOGIN, true);
        CommCareHomeActivity homeActivity =
                Robolectric.buildActivity(CommCareHomeActivity.class)
                        .withIntent(homeActivityIntent).setup().get();
        ShadowActivity shadowActivity = Shadows.shadowOf(homeActivity);
        // demo users don't have a '...' menu
        assertFalse(shadowActivity.getOptionsMenu().hasVisibleItems());
    }

    @Test
    public void testDemoUserRestoreAndUpdate() {
        TestAppInstaller.installApp(REF_BASE_DIR +
                "app_with_demo_user_restore/profile.ccpr");
        CommCareApplication._().getCurrentApp().setMMResourcesValidated();

        loginAsDemoUser();

        launchHomeActivity();

        EntitySelectActivity entitySelectActivity =
                CaseLoadUtils.launchEntitySelectActivity("m1-f0");

        EntityListAdapter adapter = CaseLoadUtils.loadList(entitySelectActivity);
        assertEquals(2, adapter.getCount());

        String profileRef = UpdateUtils.buildResourceRef(REF_BASE_DIR,
                "update_user_restore", "profile.ccpr");
        UpdateUtils.installUpdate(profileRef,
                AppInstallStatus.UpdateStaged,
                AppInstallStatus.Installed);

        // make sure there is only 1 case
        entitySelectActivity = CaseLoadUtils.launchEntitySelectActivity("m1-f0");

        adapter = CaseLoadUtils.loadList(entitySelectActivity);
        assertEquals(1, adapter.getCount());
    }
}
