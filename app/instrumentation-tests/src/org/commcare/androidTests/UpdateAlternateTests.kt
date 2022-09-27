package org.commcare.androidTests


import android.util.Log

import androidx.test.espresso.Espresso.*
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import org.commcare.annotations.BrowserstackTests
import org.commcare.utils.InstrumentationUtility
import org.commcare.utils.isPresent

import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith



@RunWith(AndroidJUnit4::class)
@LargeTest
@BrowserstackTests
class UpdateAlternateTests: BaseTest() {
    companion object {
        const val CCZ_NAME_OLD = "update_test_alternate_old_ver.ccz"
        const val CCZ_NAME_NEW = "update_test_alternate_latest.ccz"
        const val APP_NAME = "Update Test Alternate! Alternating test update"
    }

    @Before
    fun setup() {
        installApp(APP_NAME, CCZ_NAME_NEW)
        InstrumentationUtility.login("test1", "123")

    }

    /**
     * Adding the teardown method so the tests doesnot fail after only thefirst execution, when executed as a whole.
     */

//    @After
//    fun teardown(){
//        InstrumentationUtility.logout()
//    }

    @Test
    fun testUpdateAlternative(){

        InstrumentationUtility.enableDeveloperMode()
        updateApp("Latest saved state")
        InstrumentationUtility.gotoHome()
        InstrumentationUtility.openOptionsMenu()
        onView(withText("Update App"))
            .perform(click())
        InstrumentationUtility.waitForView(withText("App is up to date"))
        onView(withText("App is up to date")).isPresent()
        InstrumentationUtility.gotoHome()
        updateApp("Latest version")
        InstrumentationUtility.gotoHome()
        InstrumentationUtility.openOptionsMenu()
        onView(withText("Update App"))
            .perform(click())
        InstrumentationUtility.waitForView(withText("App is up to date"))
        onView(withText("App is up to date")).isPresent()
        InstrumentationUtility.gotoHome()
        InstrumentationUtility.logout()
        InstrumentationUtility.uninstallCurrentApp()
        InstrumentationUtility.installApp(CCZ_NAME_OLD)
        pressBack()
        InstrumentationUtility.login("test1","123")
        var testApp = UiDevice.getInstance(getInstrumentation())
        enableAirplaneMode()
    }

    fun updateApp(string: String){
        InstrumentationUtility.openOptionsMenu()
        onView(withText("Settings"))
            .perform(click())
        onView(withText("Developer Options"))
            .perform(click())
        onView(withText("Show Update Options Item"))
            .perform(click())
        onView(withText("Enabled"))
            .perform(click())
        InstrumentationUtility.gotoHome()
        InstrumentationUtility.openOptionsMenu()
        onView(withText("Settings"))
            .perform(click())
        onView(withText("Update Options"))
            .perform(click())
        onView(withText(string))
            .perform(click())

    }

//    private val mContext = getApplicationContext() as Context
//    fun isAirplaneModeOn(): Boolean {
//        return Settings.System.getInt(
//            mContext.getContentResolver(),
//            Settings.Global.AIRPLANE_MODE_ON, 0
//        ) !== 0
//    }

//    fun setAirPlaneMode(airplaneMode: Boolean) {
//        Log.d("setAirPlaneMode airplaneMode: ", airplaneMode.toString())
//
//        val state = if (airplaneMode) 1 else 0
//        Settings.Global.putInt(
//            mContext.getContentResolver(), Settings.Global.AIRPLANE_MODE_ON,
//            state
//        )
//        val intent = Intent(Intent.ACTION_AIRPLANE_MODE_CHANGED)
//        intent.putExtra("state", state)
//
//        mContext.sendBroadcast(intent)
//    }

//    fun setAndroidDeviceAirplaneMode(status: Boolean) {
//
//        try {
//
//            var airplaneModeStatus = "";
//
//            if (status) {
//
//                airplaneModeStatus = "1";
//
//            } else {
//
//                airplaneModeStatus = "0";
//
//            }
//            var sdkPath = System
//
//            val sdkPath = System.getenv ("ANDROID_HOME") + "/platform-tools/";
//
//            Runtime.getRuntime().exec(sdkPath + "adb shell settings put global airplane_mode_on " + airplaneModeStatus);
//
//            Thread.sleep (1000);
//
//            val process = Runtime.getRuntime()
//
//                .exec(sdkPath + "adb shell am broadcast -a android.intent.action.AIRPLANE_MODE");
//
//            process.waitFor();
//
//            Thread. sleep (4000);
//
//            if (status) {
//
//                Log.i("Android device Airplane mode status is set to ON", status.toString());
//
//            } else {
//
//                Log.i("Android device Airplane mode status is set to OFF",status.toString());
//
//            }
//
//        } catch ( e:Exception) {
//
////            System. out .println(e.getMessage());
//
//            Log.e("Unable to set android device Airplane mode.",e.toString());
//
//        }
//
//    }


//
    private var testApp = UiDevice.getInstance(getInstrumentation())

    private fun enableAirplaneMode() = apply {
        testApp.openQuickSettings()
        testApp.waitForIdle()

        var airplaneModeIcon = checkNotNull(testApp.findObject(By.desc("Airplane,mode,Off.,Button")))

        airplaneModeIcon.click()
    }

    private fun disableAirplaneMode() = apply {
        testApp.openQuickSettings()
        testApp.waitForIdle()

        var airplaneModeIcon = checkNotNull(testApp.findObject(By.desc("Airplane,mode,On.,Button")))

        airplaneModeIcon.click()
        testApp.pressRecentApps()
    }

//    fun setLocale(locale: Locale) {
//        val resources: Resources =
//            InstrumentationRegistry.getInstrumentation().getTargetContext().getResources()
//        Locale.setDefault(locale)
//        val config: Configuration = resources.getConfiguration()
//        config.locale = locale
//        resources.updateConfiguration(config, resources.getDisplayMetrics())
//    }

//    fun setAppLocale(languageFromPreference: String?, context: Context)
//    {
//
//        if (languageFromPreference != null) {
//
//            val resources: Resources = context.resources
//            val dm: DisplayMetrics = resources.displayMetrics
//            val config: Configuration = resources.configuration
//            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
//                config.setLocale(Locale(languageFromPreference.toLowerCase(Locale.ROOT)))
//            } else {
//                config.setLocale(Locale(languageFromPreference.toLowerCase(Locale.ROOT)))
//            }
//            resources.updateConfiguration(config, dm)
//        }
//    }
}