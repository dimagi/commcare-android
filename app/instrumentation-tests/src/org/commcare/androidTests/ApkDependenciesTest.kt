package org.commcare.androidTests

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.collect.ImmutableList
import junit.framework.TestCase.assertEquals
import org.commcare.AndroidPackageUtilsMock
import org.commcare.CommCareApplication
import org.commcare.annotations.BrowserstackTests
import org.commcare.dalvik.R
import org.commcare.utils.AndroidPackageUtils
import org.commcare.utils.InstrumentationUtility
import org.commcare.utils.isPresent
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.ConcurrentHashMap

@RunWith(AndroidJUnit4::class)
@BrowserstackTests
class ApkDependenciesTest : BaseTest() {

    companion object {
        const val CCZ_NAME = "android_dependency_test.ccz"
        const val APP_NAME = "Android Dependency Test"
        const val PLAY_STORE_URL = "market://details?id=org.commcare.dalvik.reminders"
    }

    @Test
    fun keepConcurrentHashMapConstructor() {
        // This constructor is used by a Google Location API, but R8 was stripping it from the instrumentation
        // tests APK, which caused some tests to fail. This dummy test is to force R8 to retain the constructor
        ConcurrentHashMap<Any, Any>(1, 1.00f, 1)
    }

    @Test
    fun testAppDependenciesCheck() {
        installApp(APP_NAME, CCZ_NAME)
        val unstatisfiedDependencies = ImmutableList.of("Reminders", "Test")
        verifyDependencyDialog(unstatisfiedDependencies)
        InstrumentationUtility.login("test", "123");
        onView(withText("Start"))
            .perform(click())
        verifyDependencyDialog(unstatisfiedDependencies)
        verifyDialogDisimissOnBack()

        val mockAndroidUtils = CommCareApplication.instance().androidPackageUtils as AndroidPackageUtilsMock
        // mock as only one dependency is unsatisfied
        mockAndroidUtils.addInstalledPackage("org.commcare.test")
        onView(withText("Start"))
            .perform(click())
        verifyDependencyDialog(unstatisfiedDependencies)
        verifyDialogDisimissOnBack()

        // mock as all dependencies are satisfied and check dialog doesn't app
        mockAndroidUtils.addInstalledPackage("org.commcare.dalvik.reminders")
        onView(withText("Start"))
            .perform(click())
        onView(withText(R.string.dependency_missing_dialog_title)).check(doesNotExist())
        onView(withText("Case List")).isPresent()
    }

    private fun verifyDialogDisimissOnBack() {
        InstrumentationUtility.gotoHome()
        onView(withText("Start"))
            .perform(click())
        onView(withText(R.string.dependency_missing_dialog_title)).isPresent()
        pressBack()
        onView(withText(R.string.dependency_missing_dialog_title)).check(doesNotExist())
    }

    private fun verifyDependencyDialog(unstatisfiedDependencies: ImmutableList<String>) {
        if (unstatisfiedDependencies.size == 1) {
            onView(withText(R.string.dependency_missing_dialog_title)).isPresent()
            val expectedMsg = ApplicationProvider.getApplicationContext<Context>()
                .getString(R.string.dependency_missing_dialog_message, AndroidPackageUtils().getPackageName(unstatisfiedDependencies[0]))
            onView(withText(expectedMsg)).isPresent()
        } else {
            onView(withText(R.string.dependency_missing_dialog_title_plural)).isPresent()
            onView(withText(R.string.dependency_missing_dialog_message_plural)).isPresent()
            unstatisfiedDependencies.forEach { onView(withText(AndroidPackageUtils().getPackageName(it))).isPresent() }
        }
        InstrumentationUtility.stubIntentWithAction(Intent.ACTION_VIEW)
        onView(withText(R.string.dependency_missing_dialog_go_to_store)).perform(click())
        verifyPlaystoreIntent()
    }

    private fun verifyPlaystoreIntent() {
        val receivedIntents = Intents.getIntents()
        receivedIntents.findLast { intent -> intent.action ==  Intent.ACTION_VIEW}.let {
            assertEquals("Incorrect Play store url",PLAY_STORE_URL,it!!.data.toString())
        }
    }
}
