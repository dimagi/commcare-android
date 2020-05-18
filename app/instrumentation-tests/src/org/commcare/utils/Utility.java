package org.commcare.utils;

import android.content.Context;
import android.os.RemoteException;
import androidx.annotation.IdRes;
import androidx.test.espresso.DataInteraction;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.UiDevice;
import org.commcare.dalvik.R;

import static androidx.test.espresso.Espresso.closeSoftKeyboard;
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
    public static void installApp(String cczName) {
        String location = "/storage/emulated/0/" + cczName;
        openOptionsMenu();
        onView(withText("Offline Install"))
                .perform(click());
        onView(withId(R.id.screen_multimedia_inflater_location))
                .perform(typeText(location));
        UiDevice device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        try {
            device.setOrientationLeft();
            device.setOrientationNatural();
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        closeSoftKeyboard();
        onView(withId(R.id.screen_multimedia_inflater_install))
                .perform(click());
    }

    public static void login(String userName, String password) {
        onView(withId(R.id.edit_username))
                .perform(clearText());
        onView(withId(R.id.edit_username))
                .perform(typeText(userName));
        closeSoftKeyboard();
        onView(withId(R.id.edit_password))
                .perform(typeText(password));
        closeSoftKeyboard();
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
