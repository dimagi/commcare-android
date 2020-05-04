package org.commcare.utils;

import android.content.Context;

import androidx.annotation.IdRes;
import androidx.test.espresso.DataInteraction;
import androidx.test.platform.app.InstrumentationRegistry;

import org.commcare.dalvik.R;

import static androidx.test.espresso.Espresso.onData;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.Espresso.openActionBarOverflowOrOptionsMenu;
import static androidx.test.espresso.action.ViewActions.clearText;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.typeText;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.Matchers.anything;
import static org.hamcrest.Matchers.startsWith;

/**
 * @author $|-|!˅@M
 */
public class Utility {
    public static void installApp(String code) {
        onView(withId(R.id.enter_app_location))
                .perform(click());
        onView(withId(R.id.edit_profile_location))
                .perform(typeText(code));
        onView(withId(R.id.start_install))
                .perform(click());
        onView(withId(R.id.btn_start_install))
                .perform(click());
    }

    public static void login(String userName, String password) {
        onView(withId(R.id.edit_username))
                .perform(clearText());
        onView(withId(R.id.edit_username))
                .perform(typeText(userName));
        onView(withId(R.id.edit_password))
                .perform(typeText(password));
        onView(withId(R.id.login_button))
                .perform(click());
    }

    public static void openFirstForm() {
        onView(withText("Start"))
                .perform(click());
        onData(anything())
                .inAdapterView(withId(R.id.screen_suite_menu_list))
                .atPosition(0)
                .perform(click());
        onData(anything())
                .inAdapterView(withId(R.id.screen_suite_menu_list))
                .atPosition(1)
                .perform(click());
    }

    public static void openOptionsMenu() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        openActionBarOverflowOrOptionsMenu(context);
    }

    public static void clickListItem(@IdRes int resId, int position) {
        onData(anything())
                .inAdapterView(withId(resId))
                .atPosition(position)
                .perform(click());
    }

    public static DataInteraction getSubViewInListItem(@IdRes int listId, int position, @IdRes int subviewId) {
        return onData(anything())
                .inAdapterView(withId(listId))
                .atPosition(position)
                .onChildView(withId(subviewId));
    }

    public static void openFirstIncompleteForm() {
        onView(withText(startsWith("Incomplete"))).perform(click());
        clickListItem(R.id.screen_entity_select_list, 0);
    }

    public static void logout() {
        onView(withText("Log out of CommCare")).perform(click());
    }

}
