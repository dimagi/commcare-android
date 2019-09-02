package org.commcare.android.tests.activities;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.widget.TextView;

import org.commcare.CommCareApplication;
import org.commcare.CommCareNoficationManager;
import org.commcare.CommCareTestApplication;
import org.commcare.activities.InstallArchiveActivity;
import org.commcare.activities.UpdateActivity;
import org.commcare.android.CommCareTestRunner;
import org.commcare.android.util.TestAppInstaller;
import org.commcare.dalvik.R;
import org.javarosa.core.services.locale.Localization;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowActivity;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/**
 * Test performing updates through the update activity
 *
 * @author Phillip Mates (pmates@dimagi.com)
 */

// Using sdk 19 to get past NsdManager because of a bug in robolectric that causes NsdManager
// to get initialized with a null context resulting in a NPE
@Config(application = CommCareTestApplication.class, sdk = 19)
@RunWith(CommCareTestRunner.class)
public class UpdateActivityTest {
    @Before
    public void setup() {
        TestAppInstaller.installAppAndLogin(
                "jr://resource/commcare-apps/update_tests/base_app/profile.ccpr",
                "test", "123");
    }

    /**
     * Use the update activity to update to a ccz with an invalid suite file.
     * Assert that an error is shown and pinned notification is created w/ more details
     */
    @Test
    public void invalidUpdateTest() {
        String invalidUpdateReference =
                "jr://resource/commcare-apps/update_tests/invalid_suite_update/profile.ccpr";

        // start the update activity
        Intent updateActivityIntent =
                new Intent(RuntimeEnvironment.application, UpdateActivity.class);

        UpdateActivity updateActivity =
                Robolectric.buildActivity(UpdateActivity.class, updateActivityIntent)
                        .setup().get();

        // click the 'offline install' menu item
        ShadowActivity shadowActivity = Shadows.shadowOf(updateActivity);
        shadowActivity.clickMenuItem(UpdateActivity.MENU_UPDATE_FROM_CCZ);

        // Make sure there are no pinned notifications before we start
        NotificationManager notificationManager =
                (NotificationManager)RuntimeEnvironment.application
                        .getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.cancel(CommCareNoficationManager.MESSAGE_NOTIFICATION);
        Notification notification = Shadows.shadowOf(notificationManager)
                .getNotification(CommCareNoficationManager.MESSAGE_NOTIFICATION);
        assertNull(notification);

        // mock receiving the offline app reference and start the update
        Intent referenceIntent = new Intent();
        referenceIntent.putExtra(InstallArchiveActivity.ARCHIVE_JR_REFERENCE, invalidUpdateReference);
        shadowActivity.receiveResult(shadowActivity.getNextStartedActivity(), Activity.RESULT_OK,
                referenceIntent);

        Robolectric.flushBackgroundThreadScheduler();
        Robolectric.flushForegroundThreadScheduler();

        // assert that we get the right error message
        String errorMessage = (String)((TextView)updateActivity
                .findViewById(R.id.update_progress_text)).getText();
        assertEquals(Localization.get("updates.check.failed"), errorMessage);

        // check that a pinned notification was created for invalid update
        // NOTE: it is way more work to assert the notification body is correct, so skip over that
        notification = Shadows.shadowOf(notificationManager)
                .getNotification(CommCareNoficationManager.MESSAGE_NOTIFICATION);
        assertNotNull(notification);
    }
}
