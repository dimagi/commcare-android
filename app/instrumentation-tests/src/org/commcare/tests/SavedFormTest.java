package org.commcare.tests;

import android.Manifest;
import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.provider.MediaStore;
import androidx.test.espresso.action.ViewActions;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.espresso.intent.rule.IntentsTestRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.rule.ActivityTestRule;
import androidx.test.rule.GrantPermissionRule;
import androidx.test.runner.intent.IntentCallback;
import androidx.test.runner.intent.IntentMonitorRegistry;
import org.commcare.CommCareApplication;
import org.commcare.activities.DispatchActivity;
import org.commcare.activities.FormEntryActivity;
import org.commcare.dalvik.R;
import org.commcare.utils.Utility;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.OutputStream;

import static androidx.test.espresso.Espresso.closeSoftKeyboard;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.Espresso.pressBackUnconditionally;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.typeText;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.intent.Intents.intending;
import static androidx.test.espresso.intent.matcher.IntentMatchers.hasAction;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.isRoot;
import static androidx.test.espresso.matcher.ViewMatchers.withClassName;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.not;
/**
 * @author $|-|!˅@M
 */
@RunWith(AndroidJUnit4.class)
@LargeTest
public class SavedFormTest {

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
    public void login() {
        if (CommCareApplication.instance().getCurrentApp() == null) {
            Utility.installApp("testSavedForm.ccz");
        }
        Utility.login("check", "123");
    }

    @After
    public void logout() {
        Utility.logout();
    }

    @Test
    public void testIncompleteForm_forMediaChanges() {
        // Create an incomplete form with an image.
        Utility.openFirstForm();
        onView(withId(R.id.nav_btn_next))
                .perform(click());

        Utility.chooseImage();

        onView(isRoot()).perform(ViewActions.pressBack());
        onView(withText(R.string.keep_changes)).perform(click());

        // Go to the incomplete form and confirm if image exists there.
        Utility.openFirstIncompleteForm();
        Utility.getSubViewInListItem(android.R.id.list, 1, R.id.hev_secondary_text)
                .check(matches(isDisplayed()))
                .check(matches(withText(endsWith(".jpg"))));

        // Remove image from incomplete form and exit without saving.
        Utility.clickListItem(android.R.id.list, 1);
        onView(withText(R.string.discard_image))
                .perform(click());
        onView(isRoot()).perform(ViewActions.pressBack());
        onView(withText(R.string.do_not_save)).perform(click());

        // Go back to the form and confirm image is successfully removed.
//        Utility.clickListItem(R.id.screen_entity_select_list, 0);
        Utility.openFirstIncompleteForm();
        Utility.getSubViewInListItem(android.R.id.list, 1, R.id.hev_secondary_text)
                .check(matches(not(isDisplayed())));

        // Again add an image then exit without saving and confirm image is successfully added again.
        Utility.clickListItem(android.R.id.list, 1);

        Utility.chooseImage();

        onView(isRoot()).perform(ViewActions.pressBack());
        onView(withText(R.string.do_not_save)).perform(click());
        Utility.openFirstIncompleteForm();
        Utility.getSubViewInListItem(android.R.id.list, 1, R.id.hev_secondary_text)
                .check(matches(isDisplayed()))
                .check(matches(withText(endsWith(".jpg"))));
        pressBackUnconditionally();
        pressBackUnconditionally();
    }

    @Test
    public void testIncompleteForm_forValidateCondition() {
        // Create an incomplete form.
        Utility.openFirstForm();
        onView(withId(R.id.nav_btn_next))
                .perform(click());
        onView(isRoot()).perform(ViewActions.pressBack());
        onView(withText(R.string.keep_changes)).perform(click());

        // Go to the incomplete form and confirm changing text
        // triggers validation condition and doesn't allow submitting form.
        Utility.openFirstIncompleteForm();
        Utility.clickListItem(android.R.id.list, 2);
        onView(withClassName(endsWith("EditText")))
                .perform(typeText("ANYTHING"));
        onView(withId(R.id.nav_btn_finish))
                .perform(click());
        onView(withText(R.string.invalid_answer_error))
                .check(matches(isDisplayed()));
        closeSoftKeyboard();
        onView(isRoot()).perform(ViewActions.pressBack());
        onView(withText(R.string.do_not_save)).perform(click());

        // Go back to the incomplete form and confirm form submission is allowed when there is no text.
        Utility.clickListItem(R.id.screen_entity_select_list, 0);
        Utility.clickListItem(android.R.id.list, 2);
        onView(withClassName(endsWith("EditText")))
                .check(matches(withText("")));
        onView(withId(R.id.nav_btn_finish))
                .perform(click());
        onView(withText("Start"))
                .check(matches(isDisplayed()));
    }

}