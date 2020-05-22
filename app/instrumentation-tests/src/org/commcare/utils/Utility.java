package org.commcare.utils;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.provider.MediaStore;
import android.view.View;
import androidx.annotation.IdRes;
import androidx.test.espresso.DataInteraction;
import androidx.test.espresso.UiController;
import androidx.test.espresso.ViewAction;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.intent.IntentMonitorRegistry;
import org.commcare.dalvik.R;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import static androidx.test.espresso.Espresso.closeSoftKeyboard;
import static androidx.test.espresso.Espresso.onData;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.Espresso.openActionBarOverflowOrOptionsMenu;
import static androidx.test.espresso.action.ViewActions.clearText;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.swipeUp;
import static androidx.test.espresso.action.ViewActions.typeText;
import static androidx.test.espresso.intent.Intents.intending;
import static androidx.test.espresso.intent.matcher.IntentMatchers.hasAction;
import static androidx.test.espresso.matcher.RootMatchers.isDialog;
import static androidx.test.espresso.matcher.ViewMatchers.isRoot;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.Matchers.anything;
import static org.hamcrest.Matchers.startsWith;

/**
 * @author $|-|!˅@M
 */
public class Utility {
    public static void installApp(String cczName) {
        String location = "/storage/emulated/0/" + cczName;;
        File file = new File(location);
        if (!file.exists()) {
            Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
            InputStream is = context.getClassLoader().getResourceAsStream(cczName);
            try {
                FileUtil.copyFile(is, file);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        openOptionsMenu();
        onView(withText("Offline Install"))
                .perform(click());
        stubFileSelection(location);
        onView(withId(R.id.screen_multimedia_inflater_filefetch)).perform(click());
        onView(withId(R.id.screen_multimedia_inflater_install))
                .perform(click());
    }

    public static void uninstallCurrentApp() {
        openOptionsMenu();
        onView(withText("Go To App Manager")).perform(click());
        clickListItem(R.id.apps_list_view, 0);
        onView(withText("Uninstall")).perform(click());
        onView(withText("OK")).inRoot(isDialog()).perform(click());
        onView(withId(R.id.install_app_button)).perform(click());
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
        clickListItem(R.id.screen_suite_menu_list, 0);
        clickListItem(R.id.screen_suite_menu_list, 1);
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
        onView(withId(R.id.home_gridview_buttons))
                .perform(swipeUp());
        onView(withText("Log out of CommCare"))
                .perform(click());
    }

    public static void chooseImage() {
        stubCamera();
        IntentMonitorRegistry.getInstance().addIntentCallback(Utility::onIntentSent);
        onView(withText(R.string.capture_image))
                .perform(click());
        IntentMonitorRegistry.getInstance().removeIntentCallback(Utility::onIntentSent);
    }

    /**
     * Apparently Thread.sleep() doesn't work on espresso.
     * https://youtu.be/isihPOY2vS4?t=674
     */
    public static ViewAction sleep(final long millis) {
        return new ViewAction() {
            @Override
            public Matcher<View> getConstraints() {
                return isRoot();
            }

            @Override
            public String getDescription() {
                return "Going to sleep for " + millis + "milliseconds";
            }

            @Override
            public void perform(UiController uiController, final View view) {
                uiController.loopMainThreadForAtLeast(millis);
            }
        };
    }

    /**
     * In case a view contains more than 1 items of same type,
     * use this to select the item at @param position.
     * NOTE:- position is 1 based.
     */
    public static <T> Matcher<T> find(Matcher<T> matcher, int position) {
        return new BaseMatcher<T>() {
            int count = 0;
            @Override
            public boolean matches(Object item) {
                if (matcher.matches(item)) {
                    count++;
                    return count == position;
                }
                return false;
            }

            @Override
            public void describeTo(Description description) {
                description.appendText("will return " + position + " matching item");
            }
        };
    }


    private static void stubCamera() {
        // Build a result to return from the Camera app
        Intent resultData = new Intent();
        Instrumentation.ActivityResult result = new Instrumentation.ActivityResult(Activity.RESULT_OK, resultData);

        // Stub out the Camera. When an intent is sent to the Camera, this tells Espresso to respond
        // with the ActivityResult we just created
        intending(hasAction(MediaStore.ACTION_IMAGE_CAPTURE)).respondWith(result);
    }

    private static void stubFileSelection(String filePath) {
        Intent resultData = new Intent();
        Uri fileUri = Uri.fromFile(new File(filePath));
        resultData.setData(fileUri);
        Instrumentation.ActivityResult result = new Instrumentation.ActivityResult(Activity.RESULT_OK, resultData);
        intending(hasAction(Intent.ACTION_GET_CONTENT)).respondWith(result);
    }

    private static void onIntentSent(Intent intent) {
        if (MediaStore.ACTION_IMAGE_CAPTURE.equals(intent.getAction())) {
            Uri uri = intent.getExtras().getParcelable(MediaStore.EXTRA_OUTPUT);
            Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
            Bitmap icon = BitmapFactory.decodeResource(
                    context.getResources(),
                    R.mipmap.ic_launcher);
            try (OutputStream outputStream = context.getContentResolver().openOutputStream(uri)) {
                icon.compress(Bitmap.CompressFormat.JPEG, 100, outputStream);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
