package org.commcare.androidTests


import android.app.Instrumentation
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import android.view.KeyCharacterMap
import android.view.inputmethod.EditorInfo
import android.widget.DatePicker
import android.widget.ProgressBar
import android.widget.TimePicker
import androidx.appcompat.app.AppCompatActivity
import androidx.test.espresso.Espresso
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.PositionAssertions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.contrib.PickerActions
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers
import androidx.test.espresso.matcher.RootMatchers.isPlatformPopup
import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.intent.IntentCallback
import androidx.test.runner.intent.IntentMonitorRegistry
import org.commcare.CommCareInstrumentationTestApplication
import org.commcare.annotations.BrowserstackTests
import org.commcare.dalvik.R
import org.commcare.utils.CustomMatchers
import org.commcare.utils.CustomMatchers.isPasswordHidden
import org.commcare.utils.CustomMatchers.withImageBgColor
import org.commcare.utils.InstrumentationUtility
import org.commcare.utils.isPresent
import org.hamcrest.Matchers
import org.hamcrest.Matchers.*
import org.javarosa.core.io.StreamsUtil
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.lang.Math.abs
import java.lang.Math.round
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.stream.IntStream.range


@RunWith(AndroidJUnit4::class)
@LargeTest
@BrowserstackTests
class OtherMiscellaneousTests : BaseTest() {

    companion object {
        const val CCZ_NAME = "basic_tests.ccz"
        const val APP_NAME = "Basic Tests"
    }

    @Before
    fun setup() {
        installApp(APP_NAME, CCZ_NAME)
        InstrumentationUtility.login("test1", "123")
    }


     @Test
    fun testGroups() {
         InstrumentationUtility.openForm(0, 7)
         onView(withText("OK. Please continue.")).perform(click())
         InstrumentationUtility.nextPage()
         onView(
             CustomMatchers.find(
                 allOf(withClassName(endsWith("RadioButton"))),
                 1
             )).perform(click())
         onView(
             CustomMatchers.find(
                 allOf(withClassName(endsWith("RadioButton"))),
                 4
             )).perform(click())
         onView(
             CustomMatchers.find(
                 allOf(withClassName(endsWith("RadioButton"))),
                 8
             )).perform(click())
         onView(
             CustomMatchers.find(
                 allOf(withClassName(endsWith("RadioButton"))),
                 11
             )).perform(click())
         InstrumentationUtility.nextPage()
         onView(withText("Display a new text question"))
             .perform(click())
         onView(withClassName(endsWith("EditText"))).check(matches(isDisplayed()))
         onView(withClassName(endsWith("EditText")))
             .perform(typeText("Test1"))
         InstrumentationUtility.nextPage()
         onView(withText("Choice 1"))
             .perform(click())
         assertTrue(onView(withText("You selected choice_value_1")).isPresent())
         onView(withText("CLEAR")).perform(click())
         onView(withText("Choice 2"))
             .perform(click())
         assertTrue(onView(withText("You selected choice_value_2")).isPresent())
         onView(withText("CLEAR")).perform(click())
         onView(withText("Choice 3"))
             .perform(click())
         assertTrue(onView(withText("You selected choice_value_3")).isPresent())
         InstrumentationUtility.nextPage()
         onView(withText("Suffolk"))
             .perform(click())
         assertTrue(onView(withText("Selected county was: sf")).isPresent())
         onView(allOf(withClassName(endsWith("RadioButton")),
             withText("Boston")))
             .perform(scrollTo())
             .check(matches(isDisplayed()))
         onView(allOf(withClassName(endsWith("RadioButton")),
             withText("Winthrop")))
             .perform(scrollTo())
             .check(matches(isDisplayed()))
         onView(CustomMatchers.find(withText("CLEAR"),1)).perform(click())
         onView(withText("Middlesex"))
             .perform(scrollTo(),click())
         assertTrue(onView(withText("Selected county was: mx")).isPresent())
         onView(allOf(withClassName(endsWith("RadioButton")),
             withText("Billerica")))
             .perform(scrollTo())
             .check(matches(isDisplayed()))
         onView(allOf(withClassName(endsWith("RadioButton")),
             withText("Wilmington")))
             .perform(scrollTo())
             .check(matches(isDisplayed()))
         onView(allOf(withClassName(endsWith("RadioButton")),
             withText("Cambridge")))
             .perform(scrollTo())
             .check(matches(isDisplayed()))
         onView(CustomMatchers.find(withText("CLEAR"),1)).perform(click())
         onView(withText("Essex"))
             .perform(scrollTo(),click())
         assertTrue(onView(withText("Selected county was: ex")).isPresent())
         onView(allOf(withClassName(endsWith("RadioButton")),
             withText("Saugus")))
             .perform(scrollTo())
             .check(matches(isDisplayed()))
         onView(allOf(withClassName(endsWith("RadioButton")),
             withText("Andover")))
             .perform(scrollTo())
             .check(matches(isDisplayed()))
         onView(allOf(withClassName(endsWith("RadioButton")),
             withText("Saugus")))
             .perform(click())
         InstrumentationUtility.nextPage()
         onView(withText("Yes")).perform(click())
         InstrumentationUtility.nextPage()
         onView(withText("Inner")).perform(click())
         InstrumentationUtility.nextPage()
         onView(withText("OK. Please continue.")).perform(click())
         InstrumentationUtility.nextPage()
         onView(withText("OK. Please continue.")).perform(click())
         InstrumentationUtility.nextPage()
         InstrumentationUtility.nextPage()
         onView(withText("One")).perform(click())
         InstrumentationUtility.submitForm()

         InstrumentationUtility.clickListItem(R.id.screen_suite_menu_list,7)
         onView(withText("OK. Please continue.")).perform(click())
         InstrumentationUtility.nextPage()
         onView(
             CustomMatchers.find(
                 allOf(withClassName(endsWith("RadioButton"))),
                 2
             )).perform(click())
         onView(
             CustomMatchers.find(
                 allOf(withClassName(endsWith("RadioButton"))),
                 6
             )).perform(click())
         onView(
             CustomMatchers.find(
                 allOf(withClassName(endsWith("RadioButton"))),
                 7
             )).perform(click())
         onView(
             CustomMatchers.find(
                 allOf(withClassName(endsWith("RadioButton"))),
                 12
             )).perform(click())
         InstrumentationUtility.nextPage()
         onView(withText("Display a new multiple choice question"))
             .perform(click())
         onView(allOf(withClassName(endsWith("RadioButton")),
             withText("Other")))
             .perform(scrollTo())
             .check(matches(isDisplayed()))
         onView(allOf(withClassName(endsWith("RadioButton")),
             withText("Other")))
             .perform(click())
         onView(allOf(
             withClassName(endsWith("CheckBox"))
             ,withText("OK. Please continue.")))
             .perform(scrollTo())
             .check(isCompletelyBelow(withText("Please continue.")))
         onView(allOf(
             withClassName(endsWith("CheckBox"))
             ,withText("OK. Please continue."))).perform(click())
         InstrumentationUtility.nextPage()
         onView(withText("Choice 1"))
             .perform(click())
         assertTrue(onView(withText("You selected choice_value_1")).isPresent())
         onView(withText("CLEAR")).perform(click())
         onView(withText("Choice 2"))
             .perform(click())
         assertTrue(onView(withText("You selected choice_value_2")).isPresent())
         onView(withText("CLEAR")).perform(click())
         onView(withText("Choice 3"))
             .perform(click())
         assertTrue(onView(withText("You selected choice_value_3")).isPresent())
         onView(withText("CLEAR")).perform(click())
         onView(withText("Choice 1"))
             .perform(click())
         assertTrue(onView(withText("You selected choice_value_1")).isPresent())
         InstrumentationUtility.nextPage()
         onView(withText("Suffolk"))
             .perform(click())
         assertTrue(onView(withText("Selected county was: sf")).isPresent())
         onView(allOf(withClassName(endsWith("RadioButton")),
             withText("Boston")))
             .perform(scrollTo())
             .check(matches(isDisplayed()))
         onView(allOf(withClassName(endsWith("RadioButton")),
             withText("Winthrop")))
             .perform(scrollTo())
             .check(matches(isDisplayed()))
         onView(CustomMatchers.find(withText("CLEAR"),1)).perform(click())
         onView(withText("Middlesex"))
             .perform(click())
         assertTrue(onView(withText("Selected county was: mx")).isPresent())
         onView(allOf(withClassName(endsWith("RadioButton")),
             withText("Billerica")))
             .perform(scrollTo())
             .check(matches(isDisplayed()))
         onView(allOf(withClassName(endsWith("RadioButton")),
             withText("Wilmington")))
             .perform(scrollTo())
             .check(matches(isDisplayed()))
         onView(allOf(withClassName(endsWith("RadioButton")),
             withText("Cambridge")))
             .perform(scrollTo())
             .check(matches(isDisplayed()))
         onView(CustomMatchers.find(withText("CLEAR"),1)).perform(click())
         onView(withText("Essex"))
             .perform(click())
         assertTrue(onView(withText("Selected county was: ex")).isPresent())
         onView(allOf(withClassName(endsWith("RadioButton")),
             withText("Saugus")))
             .perform(scrollTo())
             .check(matches(isDisplayed()))
         onView(allOf(withClassName(endsWith("RadioButton")),
             withText("Andover")))
             .perform(scrollTo())
             .check(matches(isDisplayed()))
         onView(CustomMatchers.find(withText("CLEAR"),1)).perform(click())
         onView(withText("Suffolk"))
             .perform(click())
         assertTrue(onView(withText("Selected county was: sf")).isPresent())
         onView(allOf(withClassName(endsWith("RadioButton")),
             withText("Boston")))
             .perform(scrollTo(),click())
         InstrumentationUtility.nextPage()
         onView(withText("No")).perform(click())
         InstrumentationUtility.nextPage()
         onView(withText("This item belongs to Group One.")).check(matches(isDisplayed()))
         InstrumentationUtility.nextPage()
         onView(withText("Outer and Inner")).perform(click())
         InstrumentationUtility.nextPage()
         onView(withText("OK. Please continue.")).perform(click())
         InstrumentationUtility.nextPage()
         InstrumentationUtility.nextPage()
         onView(withText("Two")).perform(click())
         InstrumentationUtility.submitForm()
         InstrumentationUtility.gotoHome()

     }

//    @Test
//    fun testUploads(){
//        InstrumentationUtility.openForm(17,1)
//        uploadImage("heic_file.heic")
//        InstrumentationUtility.nextPage()
//        uploadImage("bmp_file.BMP")
//        InstrumentationUtility.nextPage()
//        uploadImage("wbmp_file.wbmp")
//        InstrumentationUtility.nextPage()
//        uploadImage("JPg_file.jpg")
//        InstrumentationUtility.nextPage()
//        uploadImage("JPG_file(1).JPG")
//        InstrumentationUtility.nextPage()
//        uploadImage("webp_file.webp")
//        InstrumentationUtility.nextPage()
//        uploadImage("jpeg_file.jpeg")
//        InstrumentationUtility.nextPage()
//
//
//
//    }
//
//    private fun uploadImage(fileName: String){
//
////        onImageCaptureIntentSent(fileName)
////        onView(withText("CHOOSE IMAGE")).perform(click())
//
//        stubCamera()
//        var intentCallback = onImageCaptureIntentSent(fileName)
//        IntentMonitorRegistry.getInstance().addIntentCallback(intentCallback)
//        onView(withText(R.string.capture_image))
//            .perform(click())
//        IntentMonitorRegistry.getInstance().removeIntentCallback(intentCallback)
//
//    }
//    private fun onImageCaptureIntentSent(fileName: String) = IntentCallback { intent ->
//        val uri = intent.extras!!.getParcelable<Uri>(MediaStore.ACTION_IMAGE_CAPTURE)
//        val context = InstrumentationRegistry.getInstrumentation().targetContext
//        val inputStream = context.classLoader.getResourceAsStream(fileName)
//        val outputStream = context.contentResolver.openOutputStream(uri!!)
//        try {
//            StreamsUtil.writeFromInputToOutputUnmanaged(inputStream, outputStream)
//        } finally {
//            inputStream.close()
//            outputStream!!.close()
//        }
//    }
//    private fun stubCamera() {
//        // Build a result to return from the Camera app
//        val resultData = Intent()
//        val result = Instrumentation.ActivityResult(AppCompatActivity.RESULT_OK, resultData)
//
//        // Stub out the Camera. When an intent is sent to the Camera, this tells Espresso to respond
//        // with the ActivityResult we just created
//        Intents.intending(IntentMatchers.hasAction(Intent.ACTION_CHOOSER)).respondWith(result)
//    }

}
