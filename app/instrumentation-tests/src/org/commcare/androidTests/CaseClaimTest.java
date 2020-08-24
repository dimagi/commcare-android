package org.commcare.androidTests;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import org.commcare.dalvik.R;
import org.commcare.utils.CustomMatchers;
import org.commcare.utils.HQApi;
import org.commcare.utils.InstrumentationUtility;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static androidx.test.espresso.Espresso.closeSoftKeyboard;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.Espresso.pressBack;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.typeText;
import static androidx.test.espresso.assertion.ViewAssertions.doesNotExist;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withClassName;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.endsWith;

/**
 * @author $|-|!˅@M
 */
@RunWith(AndroidJUnit4.class)
@LargeTest
public class CaseClaimTest extends BaseTest {

    private final String CCZ_NAME = "case_claim.ccz";
    private final String APP_NAME = "Case Search and Claim";

    @Before
    public void setup() {
        installApp(APP_NAME, CCZ_NAME);
    }

    @Test
    public void testCaseClaimByDifferentUser() {
        String name = "cordelia";
        String location = "boston";

        // Make sure we close off any existing cases due to previous test failures.
        HQApi.closeExistingCases(name, "human", "d58f7a55dbe2bf22d0b6838311ada205");

        // Waiting here cuz, HQ sometimes is out of sync and might give stale data.
        InstrumentationUtility.sleep(15);

        InstrumentationUtility.login("claim_test1", "123");

        // Make sure that a case with cordelia doesn't exist.
        gotoSearchAllCases();
        onView(CustomMatchers.find(
                allOf(withClassName(endsWith("EditText"))),
                1
        )).perform(typeText(name));
        closeSoftKeyboard();
        onView(withId(R.id.request_button))
                .perform(click());
        onView(withId(R.id.request_button))
                .check(matches(isDisplayed())); // The request doesn't do anything which confirms case doesn't exists.

        // Register a new case with name cordelia
        pressBack();
        pressBack();
        onView(withText("Registration"))
                .perform(click());
        onView(withClassName(endsWith("EditText")))
                .perform(typeText(name));

        onView(withId(R.id.nav_btn_next))
                .perform(click());

        onView(withClassName(endsWith("EditText")))
                .perform(typeText(location));
        onView(withId(R.id.nav_btn_finish))
                .perform(click());

        // Confirm newly created case can be claimed.
        onView(withText("Start"))
                .perform(click());
        onView(withText("Follow Up"))
                .perform(click());
        onView(withText(name))
                .check(matches(isDisplayed()));
        onView(withText(name))
                .perform(click());
        onView(withText("Continue"))
                .perform(click());
        onView(withText("Close"))
                .check(matches(isDisplayed()));

        // Login with another user.
        InstrumentationUtility.logout();

        // This wait it to ensure that HQ doesn't give stale data.
        InstrumentationUtility.sleep(15);

        InstrumentationUtility.login("claim_test2", "123");
        // Syncing once again to pull all the case data.
        onView(withText("Sync with Server"))
                .perform(click());

        // Search for cordelia.
        gotoSearchAllCases();

        onView(CustomMatchers.find(
                allOf(withClassName(endsWith("EditText"))),
                1
        )).perform(typeText(name));

        onView(CustomMatchers.find(
                allOf(withClassName(endsWith("EditText"))),
                2
        )).perform(typeText(location));

        closeSoftKeyboard();
        onView(withId(R.id.request_button))
                .perform(click());

        // Close the new case of cordelia with this user.
        onView(withText(name))
                .perform(click());
        onView(withText("Continue"))
                .perform(click());
        onView(withText("Close"))
                .perform(click());
        onView(withText("Yes"))
                .perform(click());
        onView(withId(R.id.nav_btn_next))
                .perform(click());
        onView(withClassName(endsWith("EditText")))
                .perform(typeText("Robot says bye"));
        onView(withId(R.id.nav_btn_finish))
                .perform(click());
        onView(withText("Sync with Server"))
                .perform(click());

        // Login with first user again.
        InstrumentationUtility.logout();

        // Again waiting to ensure HQ gives correct data.
        InstrumentationUtility.sleep(15);

        InstrumentationUtility.login("claim_test1", "123");
        // Sync with server to pull all the data.
        onView(withText("Sync with Server"))
                .perform(click());

        // Make sure the case is no longer around.
        onView(withText("Start"))
                .perform(click());
        onView(withText("Follow Up"))
                .perform(click());
        onView(withText(name))
                .check(doesNotExist());
    }

    private void gotoSearchAllCases() {
        onView(withText("Start"))
                .perform(click());
        onView(withText("Follow Up"))
                .perform(click());
        onView(withText("SEARCH ALL CASES"))
                .perform(click());
    }

}
