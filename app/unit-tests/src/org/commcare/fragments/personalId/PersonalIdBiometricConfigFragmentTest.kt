package org.commcare.fragments.personalId

import android.os.Build
import android.view.View
import android.widget.Button
import androidx.appcompat.widget.AppCompatTextView
import androidx.biometric.BiometricManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.commcare.CommCareTestApplication
import org.commcare.android.database.connect.models.PersonalIdSessionData
import org.commcare.dalvik.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.`when`
import org.robolectric.annotation.Config

@Config(application = CommCareTestApplication::class)
@RunWith(AndroidJUnit4::class)
class PersonalIdBiometricConfigFragmentTest : BasePersonalIdBiometricConfigFragmentTest() {
    @Test
    fun testFingerprintVisibility_whenBiometricNotEnrolledAndBiometricEnforced() {
        // Mock
        `when`(mockBiometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG))
            .thenReturn(BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED)

        // Act
        setUpBiometricFragment(requiredLock = PersonalIdSessionData.BIOMETRIC_TYPE)

        // Verify
        verifyFingerButtonState(View.VISIBLE, R.string.connect_verify_configure_fingerprint)
        verifyMessageState(View.VISIBLE, R.string.connect_verify_message_fingerprint)
        verifyPinButtonState(View.GONE)
    }

    @Test
    fun testFingerprintVisibility_whenBiometricNotEnrolledAndBiometricOptional() {
        // Mock
        `when`(mockBiometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG))
            .thenReturn(BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED)

        // Act
        setUpBiometricFragment()

        // Verify
        verifyFingerButtonState(View.VISIBLE, R.string.connect_verify_configure_fingerprint)
        verifyMessageState(View.VISIBLE, R.string.connect_verify_message)
        verifyPinButtonState(View.VISIBLE, R.string.connect_verify_configure_pin)
    }

    @Test
    fun testFingerprintContainerVisibility_whenNoBiometricHardwareAndBiometricEnforced() {
        // Mock
        `when`(mockBiometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG))
            .thenReturn(BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE)

        // Act
        setUpBiometricFragment(requiredLock = PersonalIdSessionData.BIOMETRIC_TYPE)

        // Verify
        verifyFingerButtonState(View.GONE, R.string.connect_verify_configure_fingerprint)
        verifyMessageState(View.VISIBLE, R.string.connect_verify_message)
        verifyPinButtonState(View.GONE, R.string.connect_verify_configure_pin)

        // Verify blocking navigation to PersonalIdMessageFragment as biometric authentication is required but hardware is not available
        assertEquals(R.id.personalid_message_display, navController.currentDestination?.id)
        val args = navController.backStack.last().arguments
        assertEquals(fragment.getString(R.string.personalid_configuration_process_failed_title), args?.getString("title"))
        assertEquals(false, args?.getBoolean("isCancellable"))
    }

    @Test
    fun testFingerprintContainerVisibility_whenNoBiometricHardwareAndBiometricOptional() {
        // Mock
        `when`(mockBiometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG))
            .thenReturn(BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE)

        // Act
        setUpBiometricFragment()

        // Verify
        verifyFingerButtonState(View.GONE, R.string.connect_verify_configure_fingerprint)
        verifyMessageState(View.VISIBLE, org.commcare.dalvik.R.string.connect_verify_message_pin)
        verifyPinButtonState(View.VISIBLE, R.string.connect_verify_configure_pin)
    }

    @Test
    fun testFingerprintContainerVisibility_whenBiometricConfiguredAndBiometricEnforced() {
        // Mock
        `when`(mockBiometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG))
            .thenReturn(BiometricManager.BIOMETRIC_SUCCESS)

        // Act
        setUpBiometricFragment(requiredLock = PersonalIdSessionData.BIOMETRIC_TYPE)

        // Verify
        verifyFingerButtonState(View.VISIBLE, R.string.connect_verify_agree)
        verifyMessageState(View.VISIBLE, R.string.connect_verify_fingerprint_configured)
        verifyPinButtonState(View.GONE)
    }

    @Test
    fun testFingerprintContainerVisibility_whenBiometricConfiguredAndBiometricOptional() {
        // Mock
        `when`(mockBiometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG))
            .thenReturn(BiometricManager.BIOMETRIC_SUCCESS)

        // Act
        setUpBiometricFragment()

        // Verify
        verifyFingerButtonState(View.VISIBLE, R.string.connect_verify_agree)
        verifyMessageState(View.VISIBLE, R.string.connect_verify_fingerprint_configured)
        verifyPinButtonState(View.GONE)
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.R]) // BiometricManager.Authenticators.DEVICE_CREDENTIAL requires API 30+
    fun testPinContainerVisibility_withBothBiometricNotEnrolledAndPinConfigured() {
        // Mock
        `when`(mockBiometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG))
            .thenReturn(BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED)
        `when`(mockBiometricManager.canAuthenticate(BiometricManager.Authenticators.DEVICE_CREDENTIAL))
            .thenReturn(BiometricManager.BIOMETRIC_SUCCESS)

        // Act
        setUpBiometricFragment()

        // Verify
        verifyFingerButtonState(View.VISIBLE, R.string.connect_verify_configure_fingerprint)
        verifyMessageState(View.VISIBLE, R.string.connect_verify_message)
        verifyPinButtonState(View.VISIBLE, R.string.connect_verify_agree)
    }

    @Test
    fun testPinContainerVisibility_withBothBiometricAndPinConfigured() {
        // Mock
        `when`(mockBiometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG))
            .thenReturn(BiometricManager.BIOMETRIC_SUCCESS)
        `when`(mockBiometricManager.canAuthenticate(BiometricManager.Authenticators.DEVICE_CREDENTIAL))
            .thenReturn(BiometricManager.BIOMETRIC_SUCCESS)

        // Act
        setUpBiometricFragment()

        // Verify
        verifyFingerButtonState(View.VISIBLE, org.commcare.dalvik.R.string.connect_verify_agree)
        verifyMessageState(View.VISIBLE, R.string.connect_verify_fingerprint_configured)
        verifyPinButtonState(View.GONE)
    }

    private fun verifyFingerButtonState(
        expectedVisibility: Int,
        expectedMessage: Int,
    ) {
        val fingerprintButton = fragment.view!!.findViewById<Button>(R.id.connect_verify_fingerprint_button)
        val fingerprintContainer = fragment.view!!.findViewById<View>(org.commcare.dalvik.R.id.connect_verify_fingerprint_container)

        assertEquals(
            expectedVisibility,
            fingerprintContainer!!.visibility,
        )

        assertEquals(
            fragment.context!!.getString(expectedMessage),
            fingerprintButton.text,
        )
    }

    private fun verifyMessageState(
        expectedVisibility: Int,
        expectedMessage: Int,
    ) {
        val verifyMessage = fragment.view!!.findViewById<AppCompatTextView>(R.id.connect_verify_message)

        assertEquals(
            fragment.context!!.getString(expectedMessage),
            verifyMessage!!.text,
        )
        assertEquals(
            expectedVisibility,
            verifyMessage.visibility,
        )
    }

    private fun verifyPinButtonState(
        expectedVisibility: Int,
        expectedMessage: Int = R.string.connect_verify_agree,
    ) {
        val verifyPinButton = fragment.view!!.findViewById<Button>(org.commcare.dalvik.R.id.connect_verify_pin_button)
        val pinContainer = fragment.view!!.findViewById<View>(org.commcare.dalvik.R.id.connect_verify_pin_container)

        assertEquals(
            expectedVisibility,
            pinContainer.visibility,
        )

        if (expectedVisibility == View.VISIBLE) {
            assertEquals(
                fragment.context!!.getString(expectedMessage),
                verifyPinButton.text,
            )
        }
    }
}
