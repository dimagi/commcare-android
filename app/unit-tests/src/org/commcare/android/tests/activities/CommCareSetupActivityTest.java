package org.commcare.android.tests.activities;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;

import org.commcare.CommCareNoficationManager;
import org.commcare.CommCareTestApplication;
import org.commcare.activities.CommCareSetupActivity;
import org.commcare.activities.InstallArchiveActivity;
import org.commcare.utils.RobolectricUtil;
import org.javarosa.core.services.locale.Localization;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowActivity;

import androidx.appcompat.app.AppCompatActivity;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Test performing app installs through the setup activity
 *
 * @author Phillip Mates (pmates@dimagi.com)
 */
@Config(application = CommCareTestApplication.class)
@RunWith(AndroidJUnit4.class)
public class CommCareSetupActivityTest {


    /**
     * Test that trying to install an app with an invalid suite file results in
     * the appropriate error message and a pinned notification with more
     * details
     */
    @Test
    public void invalidAppInstall() {
        String invalidUpdateReference = "jr://resource/commcare-apps/update_tests/invalid_suite_update/profile.ccpr";


        // start the setup activity
        Intent setupIntent =
                new Intent(ApplicationProvider.getApplicationContext(), CommCareSetupActivity.class);

        CommCareSetupActivity setupActivity =
                Robolectric.buildActivity(CommCareSetupActivity.class, setupIntent)
                        .setup().get();

        // click the 'offline install' menu item
        ShadowActivity shadowActivity = Shadows.shadowOf(setupActivity);
        shadowActivity.clickMenuItem(CommCareSetupActivity.MENU_OFFLINE_INSTALL);

        // make sure there are no pinned notifications
        NotificationManager notificationManager =
                (NotificationManager)ApplicationProvider.getApplicationContext().getSystemService(Context.NOTIFICATION_SERVICE);
        Notification notification = Shadows.shadowOf(notificationManager).getNotification(CommCareNoficationManager.MESSAGE_NOTIFICATION);
        assertNull(notification);

        // mock receiving the offline app reference and start the install
        Intent referenceIntent = new Intent();
        referenceIntent.putExtra(InstallArchiveActivity.ARCHIVE_JR_REFERENCE, invalidUpdateReference);
        shadowActivity.receiveResult(shadowActivity.getNextStartedActivity(), AppCompatActivity.RESULT_OK, referenceIntent);
        RobolectricUtil.flushBackgroundThread(setupActivity);

        // assert that we get the right error message
        assertTrue(setupActivity.getErrorMessageToDisplay().contains(Localization.get("notification.install.invalid.title")));

        // check that a pinned notification was created for invalid update
        // NOTE: it is way more work to assert the notification body is correct, so skip over that
        notification = Shadows.shadowOf(notificationManager).getNotification(CommCareNoficationManager.MESSAGE_NOTIFICATION);
        assertNotNull(notification);
    }
}
