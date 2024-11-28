package org.commcare.androidTests

import android.app.Instrumentation
import android.content.Intent
import android.provider.MediaStore
import androidx.appcompat.app.AppCompatActivity
import androidx.test.espresso.Espresso
import androidx.test.espresso.action.ViewActions
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers
import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.ext.truth.content.IntentSubject
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import org.commcare.CommCareApplication
import org.commcare.activities.components.FormEntryInstanceState
import org.commcare.annotations.BrowserstackTests
import org.commcare.dalvik.R
import org.commcare.preferences.HiddenPreferences
import org.commcare.utils.FileUtil
import org.commcare.utils.InstrumentationUtility
import org.commcare.utils.InstrumentationUtility.chooseImage
import org.commcare.utils.InstrumentationUtility.login
import org.commcare.utils.InstrumentationUtility.nextPage
import org.commcare.utils.InstrumentationUtility.openForm
import org.commcare.views.widgets.ImageWidget
import org.commcare.views.widgets.MediaWidget
import org.hamcrest.CoreMatchers
import org.javarosa.core.io.StreamsUtil
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

@RunWith(AndroidJUnit4::class)
@LargeTest
@BrowserstackTests
class MediaEncryptionTest : BaseTest() {
    private val CCZ_NAME = "media_capture.ccz"
    private val APP_NAME = "Media Capture Test"

    @Before
    fun setup() {
        installApp(APP_NAME, CCZ_NAME, false)
        login("test_user_5", "123")

        // reset encryption setting
        CommCareApplication.instance().currentApp.appPreferences.edit()
                .remove(HiddenPreferences.ENCRYPT_CAPTURED_MEDIA)
                .apply()

        // Ensure test image has valid format for EXIF
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val testImage = File(context.externalCacheDir.toString() + "/test.jpg") // Use .jpg instead of .png
        // ... rest of setup
    }

    @After
    fun logout() {
        // Exit form using do not save.
        Espresso.pressBack()
        Espresso.onView(ViewMatchers.withText(R.string.do_not_save))
                .perform(ViewActions.click())

        InstrumentationUtility.logout()
    }

    @After
    fun cleanup() {
        // Clean up any test files
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        FileUtil.deleteFileOrDir(context.externalCacheDir)
        
        // ... existing cleanup code ...
    }

    @Test
    fun testImageEncryption() {
        // navigate and add an image
        openForm(0, 0)
        nextPage()
        nextPage()
        nextPage()
        chooseImage()

        // confirm there is an .aes file present for the choses image
        assertTrue("No encrypted file found for the chosen media", isEncryptedFilePresent())
        InstrumentationUtility.stubIntentWithAction(Intent.ACTION_VIEW)
        Espresso.onView(ViewMatchers.withTagValue(CoreMatchers.`is`(ImageWidget.IMAGE_VIEW_TAG)))
                .perform(ViewActions.click())
        verifyActionViewIntent()

        // turn off encryption
        CommCareApplication.instance().currentApp.appPreferences.edit()
                .putString(HiddenPreferences.ENCRYPT_CAPTURED_MEDIA, "no")
                .apply()
        chooseImage()
        assertTrue("Encrypted file found even when encryption is off", !isEncryptedFilePresent())
    }

    @Test
    fun testAudioEncryption() {
        // navigate and add an audio
        openForm(0, 0)
        nextPage()
        chooseAudio()

        // confirm there is an .aes file present for the choses audio
        assertTrue("No encrypted file found for the chosen media", isEncryptedFilePresent())
        InstrumentationUtility.stubIntentWithAction(Intent.ACTION_VIEW)
        Espresso.onView(ViewMatchers.withText(R.string.play_audio))
                .perform(ViewActions.click())
        verifyActionViewIntent()

        // turn off encryption
        CommCareApplication.instance().currentApp.appPreferences.edit()
                .putString(HiddenPreferences.ENCRYPT_CAPTURED_MEDIA, "no")
                .apply()
        chooseAudio()
        assertTrue("Encrypted file found even when encryption is off", !isEncryptedFilePresent())
    }

    // confirm that action view intent is fired
    private fun verifyActionViewIntent() {
        val receivedIntents = Intents.getIntents()
        val receivedIntent = receivedIntents[receivedIntents.size - 1]
        IntentSubject.assertThat(receivedIntent).hasAction(Intent.ACTION_VIEW)
        IntentSubject.assertThat(receivedIntent).hasFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        assertFalse("Intent Action View should not launch with encrypted file",
                receivedIntent.data.toString().endsWith(MediaWidget.AES_EXTENSION))
    }

    private fun isEncryptedFilePresent(): Boolean {
        val formInstance = File(FormEntryInstanceState.getInstanceFolder())
        val files = formInstance.listFiles()
        files?.let {
            for (file in files) {
                if(file.path.endsWith(MediaWidget.AES_EXTENSION)){
                    return true
                }
            }
        }
        return false
    }

    private fun chooseAudio() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val audio = File(context.externalCacheDir.toString() + "/test.mp3")
        val inputStream = context.classLoader.getResourceAsStream("test.mp3")
        val outputStream = FileOutputStream(audio)
        try {
            StreamsUtil.writeFromInputToOutputUnmanaged(inputStream, outputStream)
        } finally {
            inputStream.close()
            outputStream.close()
        }

        // Create intent with copied file and return it
        val uri = FileUtil.getUriForExternalFile(InstrumentationRegistry.getInstrumentation().targetContext, audio)
        val intent = Intent()
        intent.data = uri
        val result = Instrumentation.ActivityResult(AppCompatActivity.RESULT_OK, intent)
        Intents.intending(IntentMatchers.hasAction(MediaStore.Audio.Media.RECORD_SOUND_ACTION)).respondWith(result)

        Espresso.onView(ViewMatchers.withText(R.string.capture_audio))
                .perform(ViewActions.click())
    }
}
