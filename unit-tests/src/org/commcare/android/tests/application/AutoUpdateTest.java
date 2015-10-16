package org.commcare.android.tests.application;

import android.text.format.DateUtils;

import org.commcare.android.CommCareTestRunner;
import org.commcare.android.resource.AppInstallStatus;
import org.commcare.android.resource.ResourceInstallUtils;
import org.commcare.android.tasks.TaskListener;
import org.commcare.android.tasks.TaskListenerRegistrationException;
import org.commcare.android.tasks.UpdateTask;
import org.commcare.android.util.TestAppInstaller;
import org.commcare.dalvik.BuildConfig;
import org.commcare.dalvik.application.CommCareApp;
import org.commcare.dalvik.application.CommCareApplication;
import org.commcare.dalvik.preferences.CommCarePreferences;
import org.commcare.suite.model.Profile;
import org.javarosa.core.reference.ReferenceManager;
import org.javarosa.core.reference.ResourceReferenceFactory;
import org.joda.time.DateTime;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.annotation.Config;

import static org.junit.Assert.fail;

/**
 * Test auto-update triggering logic and update downloading retry logic once
 * the auto-update is triggered.
 *
 * @author Phillip Mates (pmates@dimagi.com).
 */
@Config(application = org.commcare.dalvik.application.CommCareApplication.class,
        constants = BuildConfig.class)
@RunWith(CommCareTestRunner.class)
public class AutoUpdateTest {
    private final static String REF_BASE_DIR = "jr://resource/commcare-apps/update_tests/";
    private final static String username = "test";
    private final static String password = "123";

    @Before
    public void setup() {
        // needed to resolve "jr://resource" type references
        ReferenceManager._().addReferenceFactory(new ResourceReferenceFactory());

        TestAppInstaller.setupPrototypeFactory();

        TestAppInstaller appTestInstaller =
                new TestAppInstaller(buildResourceRef("base_app", "profile.ccpr"),
                        username, password);
        appTestInstaller.installAppAndLogin();

        Profile p = CommCareApplication._().getCommCarePlatform().getCurrentProfile();
        Assert.assertTrue(p.getVersion() == 6);
    }

    @After
    public void tearDown() {
        UpdateTask.clearTaskInstance();
    }

    private String buildResourceRef(String app, String resource) {
        return REF_BASE_DIR + app + "/" + resource;
    }

    /**
     * Check that logging out after an auto-update check failed once should
     * trigger the auto-update to resume.
     */
    @Test
    public void testAppAutoUpdateLogoutRetry() {
        CommCareApp app = CommCareApplication._().getCurrentApp();
        Assert.assertFalse(ResourceInstallUtils.shouldAutoUpdateResume(app));

        // try to update to an app with syntax errors
        UpdateTask updateTask = UpdateTask.getNewInstance();
        updateTask.setAsAutoUpdate();
        try {
            TaskListener<Integer, AppInstallStatus> listener =
                    logOutAndInOnCompletionListener(AppInstallStatus.MissingResourcesWithMessage);
            updateTask.registerTaskListener(listener);
        } catch (TaskListenerRegistrationException e) {
            fail("failed to register listener for update task");
        }
        updateTask.execute(buildResourceRef("invalid_update", "profile.ccpr"));

        Robolectric.flushBackgroundThreadScheduler();
        Robolectric.flushForegroundThreadScheduler();

        // auto-update should now want to resume
        Assert.assertTrue(ResourceInstallUtils.shouldAutoUpdateResume(app));
    }

    /**
     * Test the auto-update pending calculations, which have several edge
     * cases.
     */
    @Test
    public void testAutoUpdateCalc() {
        // should be ready for update if last check was 3 days ago
        long checkedThreeDaysAgo = DateTime.now().minusDays(3).getMillis();
        Assert.assertTrue(CommCareApplication._().isTimeForAutoUpdateCheck(checkedThreeDaysAgo,
                CommCarePreferences.FREQUENCY_DAILY));

        // shouldn't be ready for update if last check was 3 hours ago
        long checkedThreeHoursAgo = DateTime.now().minusHours(3).getMillis();
        Assert.assertFalse(CommCareApplication._().isTimeForAutoUpdateCheck(checkedThreeHoursAgo,
                CommCarePreferences.FREQUENCY_DAILY));

        // test different calendar day less than 24 hours ago trigger when
        // checking every day
        DateTime yesterdayNearMidnight = new DateTime();
        yesterdayNearMidnight =
                yesterdayNearMidnight.minusDays(1).withTimeAtStartOfDay().plusHours(23);
        DateTime now = new DateTime();
        long diff = yesterdayNearMidnight.minus(now.getMillis()).getMillis();
        Assert.assertTrue(diff < DateUtils.DAY_IN_MILLIS);
        Assert.assertTrue(CommCareApplication._().isTimeForAutoUpdateCheck(yesterdayNearMidnight.getMillis(),
                CommCarePreferences.FREQUENCY_DAILY));

        // test timeshift a couple of hours in the future, shouldn't be enough
        // to warrant a update trigger
        long hoursInTheFuture = DateTime.now().plusHours(2).getMillis();
        Assert.assertFalse(CommCareApplication._().isTimeForAutoUpdateCheck(hoursInTheFuture,
                CommCarePreferences.FREQUENCY_DAILY));

        // test timeshift where if we last checked more than one day in the
        // future then we trigger
        long daysLater = DateTime.now().plusDays(2).getMillis();
        Assert.assertTrue(CommCareApplication._().isTimeForAutoUpdateCheck(daysLater,
                CommCarePreferences.FREQUENCY_DAILY));

        long weekLater = DateTime.now().plusWeeks(1).getMillis();
        Assert.assertTrue(CommCareApplication._().isTimeForAutoUpdateCheck(weekLater,
                CommCarePreferences.FREQUENCY_DAILY));
    }

    private TaskListener<Integer, AppInstallStatus> logOutAndInOnCompletionListener(final AppInstallStatus expectedResult) {
        return new TaskListener<Integer, AppInstallStatus>() {
            @Override
            public void handleTaskUpdate(Integer... updateVals) {
            }

            @Override
            public void handleTaskCompletion(AppInstallStatus result) {
                Assert.assertTrue(result == expectedResult);
                logoutAndIntoApp();
            }

            @Override
            public void handleTaskCancellation(AppInstallStatus result) {
            }
        };
    }

    private void logoutAndIntoApp() {
        CommCareApplication._().closeUserSession();

        Robolectric.flushBackgroundThreadScheduler();
        Robolectric.flushForegroundThreadScheduler();

        TestAppInstaller.login(username, password);
    }
}
