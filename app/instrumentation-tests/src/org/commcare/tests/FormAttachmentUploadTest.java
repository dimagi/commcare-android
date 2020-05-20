package org.commcare.tests;

import android.Manifest;
import androidx.test.espresso.action.GeneralLocation;
import androidx.test.espresso.action.GeneralSwipeAction;
import androidx.test.espresso.action.Press;
import androidx.test.espresso.action.Swipe;
import androidx.test.espresso.intent.rule.IntentsTestRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.rule.ActivityTestRule;
import androidx.test.rule.GrantPermissionRule;
import org.commcare.CommCareApplication;
import org.commcare.activities.DispatchActivity;
import org.commcare.activities.DrawActivity;
import org.commcare.activities.FormEntryActivity;
import org.commcare.dalvik.R;
import org.commcare.modern.util.Pair;
import org.commcare.utils.HQApi;
import org.commcare.utils.Utility;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.concurrent.TimeUnit;

import static androidx.test.espresso.Espresso.onData;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.matcher.ViewMatchers.isRoot;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNotNull;
import static org.hamcrest.Matchers.anything;
import static org.hamcrest.Matchers.instanceOf;
import static junit.framework.Assert.assertTrue;

/**
 * @author $|-|!˅@M
 */
@RunWith(AndroidJUnit4.class)
@LargeTest
public class FormAttachmentUploadTest {

    @Rule
    public ActivityTestRule<DispatchActivity> activityTestRule = new ActivityTestRule<>(DispatchActivity.class);

    @Rule
    public IntentsTestRule<FormEntryActivity> intentsRule = new IntentsTestRule<>(FormEntryActivity.class);

    @Rule
    public GrantPermissionRule permissionRule = GrantPermissionRule.grant(
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CAMERA
    );

    @Before
    public void setup() {
        if (CommCareApplication.instance().getCurrentApp() == null) {
            Utility.installApp("integration_test_app.ccz");
        } else {
            // We already have an installed app. But need to make sure it's the one that we're expecting.
            if (!"Integration Tests".equals(CommCareApplication.instance().getCurrentApp().getAppRecord().getDisplayName())) {
                Utility.uninstallCurrentApp();
                Utility.installApp("integration_test_app.ccz");
            }
        }
        Utility.login("test", "123");
    }

    @Test
    public void testAttachmentUpload() {
        Long latestFormTime = HQApi.getLatestFormTime();
        assertNotNull(latestFormTime);

        onView(withText("Start"))
                .perform(click());
        onView(withText("Form Attachments"))
                .perform(click());
        onData(anything())
                .inAdapterView(withId(R.id.screen_suite_menu_list))
                .atPosition(1)
                .perform(click());

        Utility.chooseImage();

        onView(withId(R.id.nav_btn_next))
                .perform(click());

        onView(withText("Gather Signature"))
                .perform(click());
        onView(instanceOf(DrawActivity.DrawView.class))
                .perform(new GeneralSwipeAction(Swipe.SLOW, GeneralLocation.CENTER_LEFT, GeneralLocation.TOP_RIGHT, Press.FINGER));
        onView(instanceOf(DrawActivity.DrawView.class))
                .perform(new GeneralSwipeAction(Swipe.SLOW, GeneralLocation.TOP_LEFT, GeneralLocation.CENTER_RIGHT, Press.FINGER));

        onView(withText("Save and Close"))
                .perform(click());
        onView(withId(R.id.nav_btn_finish))
                .perform(click());

        onView(withText("Sync with Server"))
                .perform(click());

        // Okay, so the form API takes some time to give the updated result with the latest form.
        // So waiting here for roughly 45 seconds, just to be on a safer side.
        long start = System.currentTimeMillis();
        onView(isRoot()).perform(Utility.sleep(TimeUnit.SECONDS.toMillis(45)));
        long end = System.currentTimeMillis();

        // Just wanna make sure that it waited enough.
        assertTrue((end - start) > TimeUnit.SECONDS.toMillis(40));

        // HQ latest form should've 3 attachments viz. 1 image, 1 signature and 1 form itself.
        Pair<Long, Integer> pair = HQApi.getLatestFormTimeAndAttachmentCount();
        assertNotNull(pair);

        Long newFormSubmissionTime = pair.first;
        assertNotNull(newFormSubmissionTime);
        assertTrue(newFormSubmissionTime > latestFormTime);

        Integer attachmentCount = pair.second;
        assertNotNull(attachmentCount);
        assertEquals(attachmentCount.intValue(), 3);
    }
}
