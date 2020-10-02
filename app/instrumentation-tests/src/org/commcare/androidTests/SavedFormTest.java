package org.commcare.androidTests;

import androidx.test.espresso.action.ViewActions;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import org.commcare.dalvik.R;
import org.commcare.utils.InstrumentationUtility;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static androidx.test.espresso.Espresso.closeSoftKeyboard;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.typeText;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.isRoot;
import static androidx.test.espresso.matcher.ViewMatchers.withClassName;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.commcare.utils.InstrumentationUtility.clickListItem;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.not;
/**
 * @author $|-|!˅@M
 */
@RunWith(AndroidJUnit4.class)
@LargeTest
public class SavedFormTest extends BaseTest {

    private final String CCZ_NAME = "testSavedForm.ccz";
    private final String APP_NAME = "TestSavedForm";

    @Before
    public void login() {
        installApp(APP_NAME, CCZ_NAME);
        InstrumentationUtility.login("check", "123");
    }

    @After
    public void logout() {
        InstrumentationUtility.logout();
    }

    @Test
    public void testIncompleteForm_forMediaChanges() {
        // Create an incomplete form with an image.
        InstrumentationUtility.openForm(0, 0);
        onView(withId(R.id.nav_btn_next))
                .perform(click());

        InstrumentationUtility.chooseImage();

        onView(isRoot()).perform(ViewActions.pressBack());
        onView(withText(R.string.keep_changes)).perform(click());

        // Go to the incomplete form and confirm if image exists there.
        InstrumentationUtility.openFirstIncompleteForm();
        InstrumentationUtility.getSubViewInListItem(android.R.id.list, 1, R.id.hev_secondary_text)
                .check(matches(isDisplayed()))
                .check(matches(withText(endsWith(".jpg"))));

        // Remove image from incomplete form and exit without saving.
        clickListItem(android.R.id.list, 1);
        onView(withText(R.string.discard_image))
                .perform(click());
        onView(isRoot()).perform(ViewActions.pressBack());
        onView(withText(R.string.do_not_save)).perform(click());

        // Go back to the form and confirm image is successfully removed.
//        Utility.clickListItem(R.id.screen_entity_select_list, 0);
        InstrumentationUtility.openFirstIncompleteForm();
        InstrumentationUtility.getSubViewInListItem(android.R.id.list, 1, R.id.hev_secondary_text)
                .check(matches(not(isDisplayed())));

        // Again add an image then exit without saving and confirm image is successfully added again.
        clickListItem(android.R.id.list, 1);

        InstrumentationUtility.chooseImage();

        onView(isRoot()).perform(ViewActions.pressBack());
        onView(withText(R.string.do_not_save)).perform(click());
        InstrumentationUtility.openFirstIncompleteForm();
        InstrumentationUtility.getSubViewInListItem(android.R.id.list, 1, R.id.hev_secondary_text)
                .check(matches(isDisplayed()))
                .check(matches(withText(endsWith(".jpg"))));
    }

    @Test
    public void testIncompleteForm_forValidateCondition() {
        // Create an incomplete form.
        InstrumentationUtility.openForm(0, 0);
        onView(withId(R.id.nav_btn_next))
                .perform(click());
        onView(isRoot()).perform(ViewActions.pressBack());
        onView(withText(R.string.keep_changes)).perform(click());

        // Go to the incomplete form and confirm changing text
        // triggers validation condition and doesn't allow submitting form.
        InstrumentationUtility.openFirstIncompleteForm();
        clickListItem(android.R.id.list, 2);
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
        clickListItem(R.id.screen_entity_select_list, 0);
        clickListItem(android.R.id.list, 2);
        onView(withClassName(endsWith("EditText")))
                .check(matches(withText("")));
        onView(withId(R.id.nav_btn_finish))
                .perform(click());
        onView(withText("Start"))
                .check(matches(isDisplayed()));
    }

    /**
     *  We're going to test a form with all the required questions to make sure that the app doesn't
     *  allow you to move forward unless a required question is filled, and at the same time
     *  navigates you to the question that is failing the validation condition.
     */
    @Test
    public void testFinishButtonClick_withValidationFailure() {
        // Navigate to the second form.
        onView(withText("Start"))
                .perform(click());
        clickListItem(R.id.screen_suite_menu_list, 1);
        clickListItem(R.id.screen_suite_menu_list, 1);

        // type something in first question
        onView(withClassName(endsWith("EditText")))
                .perform(typeText("answered"));
        onView(withId(R.id.nav_btn_next))
                .perform(click());

        // confirm second question is not filled yet.
        onView(withClassName(endsWith("EditText")))
                .check(matches(withText("")));
        // So clicking on next button should show an error.
        onView(withId(R.id.nav_btn_next))
                .perform(click());
        onView(withText(R.string.required_answer_error))
                .check(matches(isDisplayed()));

        // since we can't move forward, let's save the form.
        closeSoftKeyboard();
        onView(isRoot()).perform(ViewActions.pressBack());
        onView(withText(R.string.keep_changes)).perform(click());

        // let's go back to the saved form again and this time we'll jump directly to 3rd question.
        // so the 2nd one is still not filled and has a validation failure.
        InstrumentationUtility.openFirstIncompleteForm();
        clickListItem(android.R.id.list, 2); // position starts with 0. 🙃
        onView(withClassName(endsWith("EditText")))
                .perform(typeText("answered"));
        onView(withId(R.id.nav_btn_next))
                .perform(click());

        // Now we're on final question which isn't required. let's click finish now.
        onView(withId(R.id.nav_btn_finish))
                .perform(click());
        // it should give invalid answer error correctly.
        onView(withText(R.string.required_answer_error))
                .check(matches(isDisplayed()));
        // it should've also taken you to the second question, which can be verified by the
        // absence of finish button.
        onView(withId(R.id.nav_btn_finish))
                .check(matches(not(isDisplayed())));

        // Now type something in 2nd question and then submit properly.
        onView(withClassName(endsWith("EditText")))
                .perform(typeText("answered"));
        onView(withId(R.id.nav_btn_next))
                .perform(click());
        onView(withId(R.id.nav_btn_next))
                .perform(click());
        onView(withId(R.id.nav_btn_finish))
                .perform(click());
        // It should work now.
        onView(withText("Start"))
                .check(matches(isDisplayed()));
    }

}