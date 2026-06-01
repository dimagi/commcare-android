package org.commcare.fragments.personalId

import android.app.Activity
import android.content.Intent
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.ActivityResultLauncher
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.commcare.CommCareTestApplication
import org.commcare.android.database.connect.models.ConnectUserRecord
import org.commcare.connect.ConnectConstants
import org.commcare.connect.database.ConnectDatabaseHelper
import org.commcare.connect.database.ConnectUserDatabaseUtil
import org.commcare.connect.network.ApiPersonalId
import org.commcare.connect.network.base.BaseApiHandler.PersonalIdOrConnectApiErrorCodes
import org.commcare.dalvik.R
import org.commcare.fragments.MicroImageActivity
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil
import org.commcare.utils.MediaUtil
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
    fun testInitialState_titleContainsUserName() {
        val title = fragment.view!!.findViewById<TextView>(R.id.title)
        assertEquals(
            fragment.getString(R.string.personalid_photo_capture_title, "Test User"),
            title.text.toString(),
        )
    }

    @Test
    fun testInitialState_savePhotoButtonDisabled() {
        val saveButton = fragment.view!!.findViewById<Button>(R.id.save_photo_button)
        assertFalse("Save button should be disabled initially", saveButton.isEnabled)
    }

    @Test
    fun testInitialState_takePhotoButtonEnabled() {
        val takeButton = fragment.view!!.findViewById<Button>(R.id.take_photo_button)
        assertTrue("Take photo button should be enabled initially", takeButton.isEnabled)
    }

    @Test
    fun testInitialState_errorTextViewHidden() {
        val errorView = fragment.view!!.findViewById<TextView>(R.id.errorTextView)
        assertEquals(View.GONE, errorView.visibility)
    }

    @Test
    fun testTakePhotoClick_disablesButtonAndLaunchesMicroImageActivity() {
        // Mock
        @Suppress("UNCHECKED_CAST")
        val mockLauncher =
            Mockito.mock(ActivityResultLauncher::class.java)
                as ActivityResultLauncher<Intent>
        fragment.replaceTakePhotoLauncher(mockLauncher)

        val takeButton = fragment.view!!.findViewById<Button>(R.id.take_photo_button)

        // Act
        activity.runOnUiThread { takeButton.performClick() }
        ShadowLooper.idleMainLooper()

        // Verify
        assertFalse("Take photo button should be disabled after click", takeButton.isEnabled)
        val intentCaptor = ArgumentCaptor.forClass(Intent::class.java)
        Mockito.verify(mockLauncher).launch(intentCaptor.capture())
        val launched = intentCaptor.value
        assertEquals(
            MicroImageActivity::class.java.name,
            launched.component?.className,
        )
        assertEquals(
            160,
            launched.getIntExtra(MicroImageActivity.MICRO_IMAGE_MAX_DIMENSION_PX_EXTRA, -1),
        )
        assertEquals(
            100 * 1024,
            launched.getIntExtra(MicroImageActivity.MICRO_IMAGE_MAX_SIZE_BYTES_EXTRA, -1),
        )
    }

    @Test
    fun testPhotoResult_onSuccess_displaysImageAndEnablesSaveButton() {
        val saveButton = fragment.view!!.findViewById<Button>(R.id.save_photo_button)
        val takeButton = fragment.view!!.findViewById<Button>(R.id.take_photo_button)
        val photoView = fragment.view!!.findViewById<ImageView>(R.id.photo_image_view)

        // Setup
        activity.runOnUiThread { takeButton.isEnabled = false }
        ShadowLooper.idleMainLooper()

        // Act
        activity.runOnUiThread {
            fragment.simulatePhotoResult(Activity.RESULT_OK, "fake-base64-photo")
        }
        ShadowLooper.idleMainLooper()

        // Verify
        assertTrue("Save button should be enabled after successful capture", saveButton.isEnabled)
        assertTrue("Take photo button should re-enable after capture", takeButton.isEnabled)
        mediaUtilMock.verify {
            MediaUtil.decodeBase64EncodedBitmap("fake-base64-photo")
        }
        assertNotNull("ImageView drawable should be set", photoView.drawable)
    }

    @Test
    fun testPhotoResult_onCancel_keepsSaveDisabledAndReenablesTakeButton() {
        val saveButton = fragment.view!!.findViewById<Button>(R.id.save_photo_button)
        val takeButton = fragment.view!!.findViewById<Button>(R.id.take_photo_button)

        // Setup
        activity.runOnUiThread { takeButton.isEnabled = false }
        ShadowLooper.idleMainLooper()

        // Act
        activity.runOnUiThread {
            fragment.simulatePhotoResult(Activity.RESULT_CANCELED, null)
        }
        ShadowLooper.idleMainLooper()

        // Verify
        assertFalse("Save button should remain disabled on cancel", saveButton.isEnabled)
        assertTrue("Take photo button should re-enable on cancel", takeButton.isEnabled)
        mediaUtilMock.verify(
            { MediaUtil.decodeBase64EncodedBitmap(Mockito.anyString()) },
            Mockito.never(),
        )
    }

    @Test
    fun testSavePhotoClick_callsCompleteProfileWithCorrectArgs() {
        val saveButton = fragment.view!!.findViewById<Button>(R.id.save_photo_button)
        val takeButton = fragment.view!!.findViewById<Button>(R.id.take_photo_button)
        val errorView = fragment.view!!.findViewById<TextView>(R.id.errorTextView)

        // Setup: set up the post-capture state directly.
        activity.runOnUiThread {
            fragment.setPhotoAsBase64("fake-base64-photo")
            saveButton.isEnabled = true

            errorView.visibility = View.VISIBLE
            errorView.text = "stale error"
        }
        ShadowLooper.idleMainLooper()

        // Act
        activity.runOnUiThread { saveButton.performClick() }
        ShadowLooper.idleMainLooper()

        // Verify
        apiPersonalIdMock.verify {
            ApiPersonalId.setPhotoAndCompleteProfile(
                Mockito.any(),
                Mockito.eq("Test User"),
                Mockito.eq("fake-base64-photo"),
                Mockito.eq("123456"),
                Mockito.eq("test-token"),
                Mockito.any(),
            )
        }

        assertFalse("Save button disabled while save in flight", saveButton.isEnabled)
        assertFalse("Take photo button disabled while save in flight", takeButton.isEnabled)

        assertEquals(View.GONE, errorView.visibility)
        assertEquals("", errorView.text.toString())
    }

    @Test
    fun testCompleteProfile_onSuccess_storesUserAndNavigatesToSuccess() {
        // Act
        activity.runOnUiThread {
            fragment.invokePhotoUploadSuccess("fake-base64-photo")
        }
        ShadowLooper.idleMainLooper()

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
    fun testCompleteProfile_onAccountLocked_navigatesToFailureMessageDisplay() {
        // Act
        activity.runOnUiThread {
            fragment.invokeCompleteProfileFailure(
                PersonalIdOrConnectApiErrorCodes.ACCOUNT_LOCKED_ERROR,
                null,
            )
        }
        ShadowLooper.idleMainLooper()

        // Verify
        assertEquals(R.id.personalid_message_display, navController.currentDestination?.id)
        val args = navController.backStack.last().arguments
        assertEquals(
            fragment.getString(R.string.personalid_configuration_process_failed_title),
            args?.getString("title"),
        )
        assertEquals(false, args?.getBoolean("isCancellable"))
    }

    @Test
    fun testCompleteProfile_onRetryableFailure_reenablesButtonsAndShowsError() {
        val saveButton = fragment.view!!.findViewById<Button>(R.id.save_photo_button)
        val takeButton = fragment.view!!.findViewById<Button>(R.id.take_photo_button)
        val errorView = fragment.view!!.findViewById<TextView>(R.id.errorTextView)

        // Setup: post-capture state + save click so both buttons are disabled.
        activity.runOnUiThread {
            fragment.setPhotoAsBase64("fake-base64-photo")
            saveButton.isEnabled = true
            saveButton.performClick()
        }
        ShadowLooper.idleMainLooper()

        // Act
        activity.runOnUiThread {
            fragment.invokeCompleteProfileFailure(
                PersonalIdOrConnectApiErrorCodes.SERVER_ERROR,
                RuntimeException("boom"),
            )
        }
        ShadowLooper.idleMainLooper()

        // Verify
        assertEquals(R.id.personalid_photo_capture, navController.currentDestination?.id)

        assertEquals(View.VISIBLE, errorView.visibility)
        assertEquals("Network error occurred", errorView.text.toString())

        assertTrue("Save button should re-enable on retryable failure", saveButton.isEnabled)
        assertTrue("Take photo button should re-enable on retryable failure", takeButton.isEnabled)
    }

    @Test
    fun testCompleteProfile_onNonRetryableFailure_buttonsStayDisabled() {
        val saveButton = fragment.view!!.findViewById<Button>(R.id.save_photo_button)
        val takeButton = fragment.view!!.findViewById<Button>(R.id.take_photo_button)
        val errorView = fragment.view!!.findViewById<TextView>(R.id.errorTextView)

        // Setup
        activity.runOnUiThread {
            fragment.setPhotoAsBase64("fake-base64-photo")
            saveButton.isEnabled = true
            saveButton.performClick()
        }
        ShadowLooper.idleMainLooper()

        // Act
        activity.runOnUiThread {
            fragment.invokeCompleteProfileFailure(
                PersonalIdOrConnectApiErrorCodes.TOKEN_INVALID_ERROR,
                null,
            )
        }
        ShadowLooper.idleMainLooper()

        // Verify
        assertEquals(R.id.personalid_photo_capture, navController.currentDestination?.id)
        assertEquals(View.VISIBLE, errorView.visibility)
        assertEquals("Network error occurred", errorView.text.toString())
        assertFalse("Save button should stay disabled on non-retryable failure", saveButton.isEnabled)
        assertFalse("Take photo button should stay disabled on non-retryable failure", takeButton.isEnabled)
    }
}
