package org.commcare.utils

import androidx.appcompat.app.AppCompatActivity
import android.app.Instrumentation
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.os.RemoteException
import android.provider.MediaStore
import android.view.View
import android.widget.ListView
import androidx.annotation.IdRes
import androidx.test.espresso.DataInteraction
import androidx.test.espresso.Espresso
import androidx.test.espresso.Espresso.*
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.intent.Intents.intending
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.espresso.matcher.RootMatchers
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.intent.IntentCallback
import androidx.test.runner.intent.IntentMonitorRegistry
import androidx.test.uiautomator.UiDevice
import junit.framework.Assert
import org.commcare.CommCareInstrumentationTestApplication
import org.commcare.dalvik.R
import org.commcare.utils.CustomMatchers.withChildViewCount
import org.hamcrest.Matcher
import org.hamcrest.Matchers
import org.hamcrest.Matchers.anything
import org.hamcrest.Matchers.startsWith
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * @author $|-|!˅@M
 */
object InstrumentationUtility {
    /**
     * Installs the ccz by copying it into app-specific cache directory.
     * @param cczName
     */
    @JvmStatic
    fun installApp(cczName: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.externalCacheDir, cczName)
        if (!file.exists()) {
            val inputStream = context.classLoader.getResourceAsStream(cczName)
            try {
                FileUtil.copyFile(inputStream, file)
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
        openOptionsMenu()
        onView(withText("Offline Install"))
                .perform(click())
        stubFileSelection(file.absolutePath)
        onView(withId(R.id.screen_multimedia_inflater_filefetch))
                .perform(click())
        onView(withId(R.id.screen_multimedia_inflater_install))
                .perform(click())
    }

    @JvmStatic
    fun uninstallCurrentApp() {
        openOptionsMenu()
        onView(withText("Go To App Manager"))
                .perform(click())
        clickListItem(R.id.apps_list_view, 0)
        onView(withText("Uninstall"))
                .perform(click())
        onView(withText("OK"))
                .inRoot(RootMatchers.isDialog())
                .perform(click())
        onView(withId(R.id.install_app_button))
                .perform(click())
    }

    @JvmStatic
    fun login(userName: String, password: String) {
        enterText(R.id.edit_username, userName)
        enterText(R.id.edit_password, password)
        onView(withId(R.id.login_button))
                .perform(click())
    }

    @JvmStatic
    fun openModule(text: String) {
        onView(withText("Start"))
                .perform(click())
        onView(withText(text))
                .perform(click())
    }

    @JvmStatic
    fun openModule(module: Int) {
        onView(withText("Start"))
                .perform(click())
        clickListItem(R.id.screen_suite_menu_list, module)
    }

    @JvmStatic
    fun openForm(module: Int, form: Int) {
        openModule(module)
        clickListItem(R.id.screen_suite_menu_list, form + 1)
    }

    @JvmStatic
    fun openOptionsMenu() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        openActionBarOverflowOrOptionsMenu(context)
    }

    @JvmStatic
    fun enableDeveloperMode() {
        // Click on About CommCare 4 times to become developer.
        for (i in 0..3) {
            openOptionsMenu()
            onView(withText("About CommCare"))
                    .perform(click())
            onView(withText("OK"))
                    .perform(click())
        }
    }

    /**
     * Click the list item at a particular item position
     * @param resId Resource reference to the list.
     * @param position Position of the item to be clicked.
     */
    @JvmStatic
    fun clickListItem(@IdRes resId: Int, position: Int) {
        onData(anything())
                .inAdapterView(withId(resId))
                .atPosition(position)
                .perform(click())
    }

    /**
     * Returns a subview inside a particular list item.
     * @param listId Resource reference to the list.
     * @param position Position of the list item whose subview is needed.
     * @param subviewId Resource reference to the subview.
     */
    @JvmStatic
    fun getSubViewInListItem(@IdRes listId: Int, position: Int, @IdRes subviewId: Int): DataInteraction {
        return onData(anything())
                .inAdapterView(withId(listId))
                .atPosition(position)
                .onChildView(withId(subviewId))
    }

    /**
     * Opens first incomplete form in the app.
     * Need to have incomplete-form-enabled custom parameter set.
     */
    @JvmStatic
    fun openFirstIncompleteForm() {
        onView(withText(startsWith("Incomplete")))
                .perform(click())
        clickListItem(R.id.screen_entity_select_list, 0)
    }

    @JvmStatic
    fun logout() {
        gotoHome()
        onView(withId(R.id.home_gridview_buttons))
                .perform(swipeUp())
        onView(withText("Log out of CommCare"))
                .perform(click())
    }

    /**
     * Stubs the camera intent and uses R.mipmap.ic_launcher as the image to be returned by the camera.
     */
    @JvmStatic
    fun chooseImage() {
        stubCamera()
        IntentMonitorRegistry.getInstance().addIntentCallback(onImageCaptureIntentSent())
        onView(withText(R.string.capture_image))
                .perform(click())
        IntentMonitorRegistry.getInstance().removeIntentCallback(onImageCaptureIntentSent())
    }

    /**
     * Sleep the testing thread for the specific number of seconds.
     */
    @JvmStatic
    fun sleep(seconds: Int) {
        onView(isRoot())
                .perform(sleep(
                        TimeUnit.SECONDS.toMillis(seconds.toLong())
                ))
    }

    /**
     * Matches total occurrences of child inside parent with count.
     */
    @JvmStatic
    fun matchChildCount(parent: Class<*>, child: Class<*>, count: Int) {
        onView(withClassName(Matchers.`is`(parent.canonicalName)))
                .check(matches(
                        withChildViewCount(count,
                                withClassName(Matchers.`is`(child.canonicalName)))
                ))
    }

    /**
     * Returns the count of total number of items present in the listView.
     * @param resId Resource reference to the list.
     */
    @JvmStatic
    fun getListSize(@IdRes resId: Int): Int {
        val application = InstrumentationRegistry
                .getInstrumentation()
                .targetContext
                .applicationContext as CommCareInstrumentationTestApplication
        val activity = application.currentActivity
        val listView = activity.findViewById<ListView>(resId)
        return listView.adapter.count
    }

    /**
     * This method will toggle the wifi state in mobile.
     * Starting with Android Q, applications are not allowed to enable/disable Wi-Fi.
     */
    @JvmStatic
    fun changeWifi(enable: Boolean) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
            wifiManager.isWifiEnabled = enable
        } else {
            throw IllegalAccessException("changeWifi should only be called in pre-android Q devices")
        }
    }

    /**
     * A wrapper around espresso's typeText api. This method will type text into the specified
     * edittext and after that it will close the keyboard.
     */
    @JvmStatic
    fun enterText(@IdRes editTextId: Int, text: String) {
        onView(withId(editTextId))
                .perform(clearText())
        onView(withId(editTextId))
                .perform(typeText(text))
        Espresso.closeSoftKeyboard()
    }

    @JvmStatic
    @Throws(RemoteException::class)
    fun rotatePortrait() {
        val uiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        uiDevice.setOrientationNatural()
        sleep(2)
    }

    @JvmStatic
    @Throws(RemoteException::class)
    fun rotateLeft() {
        val uiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        uiDevice.setOrientationLeft()
        sleep(2)
    }

    /**
     * A workaround to Failed resolution of: Lkotlin/_Assertions;
     * This will fail the test if the value is false.
     */
    @JvmStatic
    fun assert(value: Boolean, failMsg: String) {
        if (!value) {
            Assert.fail("Assertion Failed: $failMsg")
        }
    }

    /**
     * A utility to pressBack until Home screen is reached at most 6 times.
     */
    @JvmStatic
    fun gotoHome() {
        for (i in 0..5) { // Try atmost 6 times.
            if (onView(withId(R.id.home_gridview_buttons)).isPresent()) {
                return
            } else {
                Espresso.pressBack()
            }
        }
    }

    /**
     * The method does following in order:
     * 1. Closes keyboard.
     * 2. Presses Back Button.
     * 3. Selects the backOption: R.id.donotsave or R.id.saveincomplete.
     */
    @JvmStatic
    fun exitForm(@IdRes backOption: Int) {
        Espresso.closeSoftKeyboard()
        Espresso.pressBack()
        onView(withText(backOption))
                .perform(click())
    }

    /**
     * A utility to select an item from the options menu.
     * Caller need to pass the matcher for the item to be selected. It could be withText("") or
     * withId(R.id.*)
     */
    @JvmStatic
    fun selectOptionItem(matcher: Matcher<View>) {
        openOptionsMenu()
        onView(matcher)
                .perform(click())
    }


    //region private helpers.
    /**
     * Apparently Thread.sleep() doesn't work on
     * https://youtu.be/isihPOY2vS4?t=674
     */
    private fun sleep(millis: Long): ViewAction {
        return object : ViewAction {
            override fun getConstraints(): Matcher<View> {
                return isRoot()
            }

            override fun getDescription(): String {
                return "Going to sleep for " + millis + "milliseconds"
            }

            override fun perform(uiController: UiController, view: View) {
                uiController.loopMainThreadForAtLeast(millis)
            }
        }
    }

    private fun stubCamera() {
        // Build a result to return from the Camera app
        val resultData = Intent()
        val result = Instrumentation.ActivityResult(AppCompatActivity.RESULT_OK, resultData)

        // Stub out the Camera. When an intent is sent to the Camera, this tells Espresso to respond
        // with the ActivityResult we just created
        intending(hasAction(MediaStore.ACTION_IMAGE_CAPTURE)).respondWith(result)
    }

    private fun stubFileSelection(filePath: String) {
        val resultData = Intent()
        val fileUri = Uri.fromFile(File(filePath))
        resultData.data = fileUri
        val result = Instrumentation.ActivityResult(AppCompatActivity.RESULT_OK, resultData)
        intending(hasAction(Intent.ACTION_GET_CONTENT)).respondWith(result)
    }

    private fun onImageCaptureIntentSent() = IntentCallback { intent ->
        if (MediaStore.ACTION_IMAGE_CAPTURE == intent.action) {
            val uri = intent.extras!!.getParcelable<Uri>(MediaStore.EXTRA_OUTPUT)
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val icon = BitmapFactory.decodeResource(
                    context.resources,
                    R.mipmap.ic_launcher)
            try {
                context.contentResolver.openOutputStream(uri!!).use {
                    outputStream -> icon.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    //endregion
}
