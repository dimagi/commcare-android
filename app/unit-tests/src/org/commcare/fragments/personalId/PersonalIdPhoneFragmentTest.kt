package org.commcare.fragments.personalId

import android.location.Location
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.Navigation
import androidx.navigation.fragment.NavHostFragment
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.commcare.CommCareTestApplication
import org.commcare.activities.connect.PersonalIdActivity
import org.commcare.dalvik.R
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.MockitoAnnotations
import org.robolectric.Robolectric
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

/**
 * Unit tests for PersonalIdPhoneFragment using Robolectric to test actual fragment UI and behavior.
 * Tests the real fragment instantiation and UI state changes.
 */
@Config(application = CommCareTestApplication::class)
@RunWith(AndroidJUnit4::class)
class PersonalIdPhoneFragmentTest {
    private lateinit var activityController: ActivityController<PersonalIdActivity>
    private lateinit var activity: PersonalIdActivity
    private lateinit var fragment: PersonalIdPhoneFragment

    @Mock
    private lateinit var mockLocation: Location

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        mockLocation()
        setUpPersonalIdActivityWithFragment()
    }

    private fun setUpPersonalIdActivityWithFragment() {
        activityController = Robolectric.buildActivity(PersonalIdActivity::class.java)
        activity =
            activityController
                .create()
                .start()
                .get()
        activityController.resume()

        val navHostFragment = activity.supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment_connectid) as NavHostFragment
        fragment = navHostFragment.childFragmentManager
            .primaryNavigationFragment as PersonalIdPhoneFragment

        ShadowLooper.idleMainLooper()
    }

    private fun mockLocation() {
        `when`(mockLocation.latitude).thenReturn(37.7749)
        `when`(mockLocation.longitude).thenReturn(-122.4194)
        `when`(mockLocation.hasAccuracy()).thenReturn(true)
        `when`(mockLocation.accuracy).thenReturn(10.0f)
    }

    @After
    fun tearDown() {
        activityController.pause().stop().destroy()
    }

    // ========== UI Initial State Tests ==========

    @Test
    fun testInitialState_continueButtonDisabled() {
        val continueButton = fragment.view?.findViewById<Button>(R.id.personalid_phone_continue_button)
        assertFalse("Continue button should be disabled initially", continueButton!!.isEnabled)
    }

    @Test
    fun testInitialState_phoneInputEmpty() {
        val phoneInput = fragment.view?.findViewById<EditText>(R.id.connect_primary_phone_input)
        assertEquals("Phone input should be empty at start", "", phoneInput!!.text.toString())
    }

    @Test
    fun testInitialState_consentCheckboxUnchecked() {
        val consentCheckbox = fragment.view?.findViewById<CheckBox>(R.id.connect_consent_check)
        assertFalse("Consent checkbox should be unchecked at start", consentCheckbox!!.isChecked)
    }

    @Test
    fun testInitialState_countryCodeSet() {
        val countryCode = fragment.view?.findViewById<EditText>(R.id.countryCode)
        assertTrue("Country code should start with +", countryCode!!.text.toString().startsWith("+"))
    }

    @Test
    fun testInitialState_locationToolTipInvisible() {
        val tooltipGroup = fragment.view?.findViewById<View>(R.id.group_tooltip)
        assertEquals("Tooltip should be invisible at start", View.GONE, tooltipGroup!!.visibility)
    }

    // ==========  Input Tests ==========

    @Test
    fun testPhoneInput_updatesContinueButtonState() {
        // Arrange
        val phoneInput = fragment.view?.findViewById<EditText>(R.id.connect_primary_phone_input)
        val continueButton = fragment.view?.findViewById<Button>(R.id.personalid_phone_continue_button)

        // Act - Enter text
        activity.runOnUiThread {
            phoneInput?.setText("1234567890")
        }
        ShadowLooper.idleMainLooper()

        // Assert - Button should still be disabled (need consent + location)
        assertFalse("Button should remain disabled without all requirements", continueButton!!.isEnabled)
    }

    @Test
    fun testConsentCheckbox_checking_updatesState() {
        // Arrange
        val consentCheckbox = fragment.view?.findViewById<CheckBox>(R.id.connect_consent_check)
        val continueButton = fragment.view?.findViewById<Button>(R.id.personalid_phone_continue_button)

        // Act
        activity.runOnUiThread {
            consentCheckbox?.isChecked = true
        }
        ShadowLooper.idleMainLooper()

        // Assert
        assertTrue("Checkbox should be checked", consentCheckbox!!.isChecked)
        // Button still disabled - need valid phone + location
        assertFalse("Button should remain disabled without phone and location", continueButton!!.isEnabled)
    }

    // ========== Location Callback Tests ==========

    @Test
    fun testLocationCallback_updatesUITooltip() {
        // Act
        activity.runOnUiThread {
            fragment.onLocationResult(mockLocation)
        }
        ShadowLooper.idleMainLooper()

        // Assert - Tooltip should be visible
        val tooltipGroup = fragment.view?.findViewById<View>(R.id.group_tooltip)
        assertEquals("Tooltip should be visible", View.VISIBLE, tooltipGroup!!.visibility)
    }

    @Test
    fun testLocationCallback_enablesButtonWithAllRequirements() {
        // Arrange
        val continueButton = fragment.view?.findViewById<Button>(R.id.personalid_phone_continue_button)
        val phoneInput = fragment.view?.findViewById<EditText>(R.id.connect_primary_phone_input)
        val countryCode = fragment.view?.findViewById<EditText>(R.id.countryCode)
        val consentCheckbox = fragment.view?.findViewById<CheckBox>(R.id.connect_consent_check)

        // Set phone and consent first
        activity.runOnUiThread {
            countryCode?.setText("+91") // Set India country code
            phoneInput?.setText("9876543210")
            consentCheckbox?.isChecked = true
        }
        ShadowLooper.idleMainLooper()

        // Act - Add location (final requirement)
        activity.runOnUiThread {
            fragment.onLocationResult(mockLocation)
        }
        ShadowLooper.idleMainLooper()

        // Assert - Now with all requirements, button should be enabled
        assertTrue("Button should be enabled with all requirements", continueButton!!.isEnabled)
    }

    @Test
    fun testLocationServiceChange_disablesButton() {
        // Arrange - First set all requirements
        val continueButton = fragment.view?.findViewById<Button>(R.id.personalid_phone_continue_button)
        val phoneInput = fragment.view?.findViewById<EditText>(R.id.connect_primary_phone_input)
        val countryCode = fragment.view?.findViewById<EditText>(R.id.countryCode)
        val consentCheckbox = fragment.view?.findViewById<CheckBox>(R.id.connect_consent_check)

        activity.runOnUiThread {
            countryCode?.setText("+91")
            phoneInput?.setText("9876543210")
            consentCheckbox?.isChecked = true
            fragment.onLocationResult(mockLocation)
        }
        ShadowLooper.idleMainLooper()

        assertTrue("Button should be enabled initially", continueButton!!.isEnabled)

        // Act - Disable location service
        activity.runOnUiThread {
            fragment.onLocationServiceChange(false)
        }
        ShadowLooper.idleMainLooper()

        // Assert - Button should now be disabled
        assertFalse("Button should be disabled when location service is off", continueButton.isEnabled)
    }

    // ========== Complete Flow with all inputs ==========

    @Test
    fun testCompleteFlow_missingConsent_keepsButtonDisabled() {
        // Arrange
        val continueButton = fragment.view?.findViewById<Button>(R.id.personalid_phone_continue_button)
        val phoneInput = fragment.view?.findViewById<EditText>(R.id.connect_primary_phone_input)

        // Act - Set phone and location but no consent
        activity.runOnUiThread {
            phoneInput?.setText("9876543210")
            fragment.onLocationResult(mockLocation)
            // No consent checked
        }
        ShadowLooper.idleMainLooper()

        // Assert
        assertFalse("Button should be disabled without consent", continueButton!!.isEnabled)
    }

    @Test
    fun testCompleteFlow_invalidPhone_keepsButtonDisabled() {
        // Arrange
        val continueButton = fragment.view?.findViewById<Button>(R.id.personalid_phone_continue_button)
        val phoneInput = fragment.view?.findViewById<EditText>(R.id.connect_primary_phone_input)
        val consentCheckbox = fragment.view?.findViewById<CheckBox>(R.id.connect_consent_check)

        // Act - Set invalid phone + consent + location
        activity.runOnUiThread {
            phoneInput?.setText("123") // Too short
            consentCheckbox?.isChecked = true
            fragment.onLocationResult(mockLocation)
        }
        ShadowLooper.idleMainLooper()

        // Assert
        assertFalse("Button should be disabled with invalid phone", continueButton!!.isEnabled)
    }
}
