package org.commcare.utils;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.provider.MediaStore;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;

import androidx.annotation.IdRes;
import androidx.test.espresso.DataInteraction;
import androidx.test.espresso.UiController;
import androidx.test.espresso.ViewAction;
import androidx.test.espresso.matcher.BoundedMatcher;
import androidx.test.espresso.util.TreeIterables;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.intent.IntentMonitorRegistry;

import org.commcare.CommCareInstrumentationTestApplication;
import org.commcare.dalvik.R;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.TimeUnit;

import static androidx.test.espresso.Espresso.closeSoftKeyboard;
import static androidx.test.espresso.Espresso.onData;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.Espresso.openActionBarOverflowOrOptionsMenu;
import static androidx.test.espresso.action.ViewActions.clearText;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.swipeUp;
import static androidx.test.espresso.action.ViewActions.typeText;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.intent.Intents.intending;
import static androidx.test.espresso.intent.matcher.IntentMatchers.hasAction;
import static androidx.test.espresso.matcher.RootMatchers.isDialog;
import static androidx.test.espresso.matcher.ViewMatchers.isRoot;
import static androidx.test.espresso.matcher.ViewMatchers.withClassName;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.Matchers.anything;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.startsWith;

/**
 * @author $|-|!˅@M
 */
public class InstrumentationUtility {

    /**
     * Installs the ccz by copying it into app-specific cache directory.
     * @param cczName
     */
    public static void installApp(String cczName) {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        File file = new File(context.getExternalCacheDir(), cczName);
        if (!file.exists()) {
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
        stubFileSelection(file.getAbsolutePath());
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

    public static void enableDeveloperMode() {
        // Click on About CommCare 4 times to become developer.
        for (int i = 0; i < 4; i++) {
            openOptionsMenu();
            onView(withText("About CommCare"))
                    .perform(click());
            onView(withText("OK"))
                    .perform(click());
        }
    }

    /**
     * Click the list item at a particular item position
     * @param resId Resource reference to the list.
     * @param position Position of the item to be clicked.
     */
    public static void clickListItem(@IdRes int resId, int position) {
        onData(anything())
                .inAdapterView(withId(resId))
                .atPosition(position)
                .perform(click());
    }

    /**
     * Returns a subview inside a particular list item.
     * @param listId Resource reference to the list.
     * @param position Position of the list item whose subview is needed.
     * @param subviewId Resource reference to the subview.
     */
    public static DataInteraction getSubViewInListItem(@IdRes int listId, int position, @IdRes int subviewId) {
        return onData(anything())
                .inAdapterView(withId(listId))
                .atPosition(position)
                .onChildView(withId(subviewId));
    }

    /**
     * Opens first incomplete form in the app.
     * Need to have incomplete-form-enabled custom parameter set.
     */
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

    /**
     * Stubs the camera intent and uses R.mipmap.ic_launcher as the image to be returned by the camera.
     */
    public static void chooseImage() {
        stubCamera();
        IntentMonitorRegistry.getInstance().addIntentCallback(InstrumentationUtility::onImageCaptureIntentSent);
        onView(withText(R.string.capture_image))
                .perform(click());
        IntentMonitorRegistry.getInstance().removeIntentCallback(InstrumentationUtility::onImageCaptureIntentSent);
    }

    /**
     * Sleep the testing thread for the specific number of seconds.
     */
    public static void sleep(int seconds) {
        onView(isRoot()).perform(sleep(TimeUnit.SECONDS.toMillis(seconds)));
    }

    /**
     * Apparently Thread.sleep() doesn't work on espresso.
     * https://youtu.be/isihPOY2vS4?t=674
     */
    private static ViewAction sleep(final long millis) {
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
     * Matches total occurrences of child inside parent with count.
     */
    public static void matchChildCount(Class<?> parent, Class<?> child, int count) {
        onView(withClassName(is(parent.getCanonicalName())))
                .check(matches(
                        CustomMatchers.withChildViewCount(count,
                                withClassName(is(child.getCanonicalName())))
                ));
    }

    /**
     * Returns the count of total number of items present in the listView.
     * @param resId Resource reference to the list.
     */
    public static int getListSize(@IdRes int resId) {
        CommCareInstrumentationTestApplication application =
                (CommCareInstrumentationTestApplication) InstrumentationRegistry
                        .getInstrumentation()
                        .getTargetContext()
                        .getApplicationContext();
        Activity activity = application.getCurrentActivity();
        ListView listView = activity.findViewById(resId);
        return listView.getAdapter().getCount();
    }

    /**
     * This method will toggle the wifi state in mobile.
     * Starting with Android Q, applications are not allowed to enable/disable Wi-Fi.
     */
    public static void changeWifi(boolean enable) {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        wifiManager.setWifiEnabled(enable);
    }

    /**
     * A wrapper around espresso's typeText api. This method will type text into the specified
     * edittext and after that it will close the keyboard.
     */
    public static void enterText(@IdRes int editTextId, String text) {
        onView(withId(editTextId))
                .perform(typeText(text));
        closeSoftKeyboard();
    }

    //region private helpers.
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

    private static void onImageCaptureIntentSent(Intent intent) {
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
    //endregion
}
