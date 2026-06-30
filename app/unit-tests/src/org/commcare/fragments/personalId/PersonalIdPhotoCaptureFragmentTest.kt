package org.commcare.fragments.personalId

import android.app.Activity
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers.hasExtra
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.commcare.CommCareTestApplication
import org.commcare.activities.camera.MicroImageActivity
import org.commcare.android.database.connect.models.ConnectUserRecord
import org.commcare.connect.ConnectConstants
import org.commcare.connect.database.ConnectDatabaseHelper
import org.commcare.connect.database.ConnectUserDatabaseUtil
import org.commcare.dalvik.R
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil
import org.commcare.utils.MediaUtil
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.allOf
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.Mockito
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

@Config(application = CommCareTestApplication::class)
@RunWith(AndroidJUnit4::class)
class PersonalIdPhotoCaptureFragmentTest : BasePersonalIdPhotoCaptureFragmentTest() {
    @Test
    fun `title shows the user's name`() {
        val title = fragment.view!!.findViewById<TextView>(R.id.title)
        assertEquals(
            fragment.getString(R.string.personalid_photo_capture_title, "Test User"),
            title.text.toString(),
        )
    }

    @Test
    fun `save button is disabled before a photo is taken`() {
        val saveButton = fragment.view!!.findViewById<Button>(R.id.save_photo_button)
        assertFalse("Save button should be disabled initially", saveButton.isEnabled)
    }

    @Test
    fun `take photo button is enabled before a photo is taken`() {
        val takeButton = fragment.view!!.findViewById<Button>(R.id.take_photo_button)
        assertTrue("Take photo button should be enabled initially", takeButton.isEnabled)
    }

    @Test
    fun `error message is hidden before a photo is taken`() {
        val errorView = fragment.view!!.findViewById<TextView>(R.id.errorTextView)
        assertEquals(View.GONE, errorView.visibility)
    }

    @Test
    fun `tapping take photo disables the button and launches the camera`() {
        val takeButton = fragment.view!!.findViewById<Button>(R.id.take_photo_button)

        // Act
        clickButton(R.id.take_photo_button)

        // Verify
        assertFalse("Take photo button should be disabled after click", takeButton.isEnabled)
        val cameraIntents =
            Intents.getIntents().filter {
                it.component?.className == MicroImageActivity::class.java.name
            }
        assertEquals("Expected exactly one MicroImageActivity launch", 1, cameraIntents.size)
        assertThat(
            cameraIntents.single(),
            allOf(
                hasExtra(MicroImageActivity.MICRO_IMAGE_MAX_DIMENSION_PX_EXTRA, 160),
                hasExtra(MicroImageActivity.MICRO_IMAGE_MAX_SIZE_BYTES_EXTRA, 100 * 1024),
            ),
        )
    }

    @Test
    fun `a successful capture shows the photo and enables the save button`() {
        val saveButton = fragment.view!!.findViewById<Button>(R.id.save_photo_button)
        val takeButton = fragment.view!!.findViewById<Button>(R.id.take_photo_button)
        val photoView = fragment.view!!.findViewById<ImageView>(R.id.photo_image_view)

        // Act
        takePhoto("fake-base64-photo")

        // Verify
        assertTrue("Save button should be enabled after successful capture", saveButton.isEnabled)
        assertTrue("Take photo button should re-enable after capture", takeButton.isEnabled)
        mediaUtilMock.verify {
            MediaUtil.decodeBase64EncodedBitmap("fake-base64-photo")
        }
        assertNotNull("ImageView drawable should be set", photoView.drawable)
    }

    @Test
    fun `cancelling the capture keeps save disabled and re-enables take photo`() {
        val saveButton = fragment.view!!.findViewById<Button>(R.id.save_photo_button)
        val takeButton = fragment.view!!.findViewById<Button>(R.id.take_photo_button)

        // Act
        intendPhotoCaptureResult(Activity.RESULT_CANCELED, null)
        clickButton(R.id.take_photo_button)

        // Verify
        assertFalse("Save button should remain disabled on cancel", saveButton.isEnabled)
        assertTrue("Take photo button should re-enable on cancel", takeButton.isEnabled)
        mediaUtilMock.verify(
            { MediaUtil.decodeBase64EncodedBitmap(Mockito.anyString()) },
            Mockito.never(),
        )
    }

    @Test
    fun `tapping save sends the complete-profile request and disables the buttons while in flight`() {
        val saveButton = fragment.view!!.findViewById<Button>(R.id.save_photo_button)
        val takeButton = fragment.view!!.findViewById<Button>(R.id.take_photo_button)
        val errorView = fragment.view!!.findViewById<TextView>(R.id.errorTextView)

        // Setup. No response is enqueued so the request stays in flight, making the
        // disabled-while-saving assertions deterministic.
        takePhoto("fake-base64-photo")
        activity.runOnUiThread {
            errorView.visibility = View.VISIBLE
            errorView.text = "stale error"
        }
        ShadowLooper.idleMainLooper()

        // Act
        clickSavePhoto()

        // Verify
        val request = takeRequestOrFail()
        assertEquals("/users/complete_profile", request.path)
        assertEquals("POST", request.method)

        val authHeader = request.headers["Authorization"]
        assertNotNull("Authorization header should be present", authHeader)
        assertTrue(
            "Authorization header should be a token-auth using the session token",
            authHeader!!.contains("test-token"),
        )

        val body = JSONObject(request.body.readUtf8())
        assertEquals("fake-base64-photo", body.getString("photo"))
        assertEquals("Test User", body.getString("name"))
        assertEquals("123456", body.getString("recovery_pin"))

        assertFalse("Save button disabled while save in flight", saveButton.isEnabled)
        assertFalse("Take photo button disabled while save in flight", takeButton.isEnabled)

        assertEquals(View.GONE, errorView.visibility)
        assertEquals("", errorView.text.toString())
    }

    @Test
    fun `completing the profile stores the user and navigates to the success screen`() {
        // Setup
        takePhoto("fake-base64-photo")
        enqueueCompleteProfileSuccess()

        // Act
        clickSavePhoto()
        drainHttp()

        // Verify
        connectDatabaseHelperMock.verify {
            ConnectDatabaseHelper.handleReceivedDbPassphrase(Mockito.any(), Mockito.eq("test-db-key"))
        }

        val userCaptor = ArgumentCaptor.forClass(ConnectUserRecord::class.java)
        connectUserDatabaseUtilMock.verify {
            ConnectUserDatabaseUtil.storeUser(Mockito.any(), userCaptor.capture())
        }
        val storedUser = userCaptor.value
        assertEquals("Test User", storedUser.name)
        assertEquals("test-personal-id", storedUser.userId)
        assertEquals("fake-base64-photo", storedUser.photo)
        assertEquals("+11234567890", storedUser.primaryPhone)

        firebaseAnalyticsUtilMock.verify {
            FirebaseAnalyticsUtil.reportPersonalIdAccountCreated()
        }

        assertEquals(R.id.personalid_message_display, navController.currentDestination?.id)
        val args = navController.backStack.last().arguments
        assertEquals(
            fragment.getString(R.string.connect_register_success_title),
            args?.getString("title"),
        )
        assertEquals(
            fragment.getString(R.string.connect_register_success_message),
            args?.getString("message"),
        )
        assertEquals(false, args?.getBoolean("isCancellable"))
        assertEquals(
            ConnectConstants.PERSONALID_REGISTRATION_SUCCESS,
            args?.getInt("callingClass"),
        )
    }

    @Test
    fun `a locked account navigates to the failure screen`() {
        // Setup
        takePhoto("fake-base64-photo")
        enqueueCompleteProfileFailure(400, """{"error_code":"LOCKED_ACCOUNT"}""")

        // Act
        clickSavePhoto()
        drainHttp()

        // Verify
        assertEquals(R.id.personalid_message_display, navController.currentDestination?.id)
        val args = navController.backStack.last().arguments
        assertEquals(
            fragment.getString(R.string.personalid_configuration_process_failed_title),
            args?.getString("title"),
        )
        assertEquals(
            fragment.getString(R.string.personalid_configuration_locked_account),
            args?.getString("message"),
        )
        assertEquals(false, args?.getBoolean("isCancellable"))
    }

    @Test
    fun `a retryable failure re-enables the buttons and shows an error`() {
        val saveButton = fragment.view!!.findViewById<Button>(R.id.save_photo_button)
        val takeButton = fragment.view!!.findViewById<Button>(R.id.take_photo_button)
        val errorView = fragment.view!!.findViewById<TextView>(R.id.errorTextView)

        // Setup
        takePhoto("fake-base64-photo")
        enqueueCompleteProfileFailure(500, "Internal Server Error")

        // Act
        clickSavePhoto()
        drainHttp()

        // Verify
        assertEquals(R.id.personalid_photo_capture, navController.currentDestination?.id)

        assertEquals(View.VISIBLE, errorView.visibility)
        assertEquals(
            fragment.getString(R.string.recovery_network_server_error),
            errorView.text.toString(),
        )

        assertTrue("Save button should re-enable on retryable failure", saveButton.isEnabled)
        assertTrue("Take photo button should re-enable on retryable failure", takeButton.isEnabled)
    }

    @Test
    fun `a non-retryable failure leaves the buttons disabled`() {
        val saveButton = fragment.view!!.findViewById<Button>(R.id.save_photo_button)
        val takeButton = fragment.view!!.findViewById<Button>(R.id.take_photo_button)
        val errorView = fragment.view!!.findViewById<TextView>(R.id.errorTextView)

        // Setup
        takePhoto("fake-base64-photo")
        enqueueCompleteProfileFailure(403, """{"error_code":"PHONE_NOT_VALIDATED"}""")

        // Act
        clickSavePhoto()
        drainHttp()

        // Verify
        assertEquals(R.id.personalid_photo_capture, navController.currentDestination?.id)
        assertEquals(View.VISIBLE, errorView.visibility)
        assertEquals(
            fragment.getString(R.string.network_forbidden_error),
            errorView.text.toString(),
        )
        assertFalse("Save button should stay disabled on non-retryable failure", saveButton.isEnabled)
        assertFalse("Take photo button should stay disabled on non-retryable failure", takeButton.isEnabled)
    }
}
