package org.commcare.fragments.personalId

import android.location.Location
import androidx.annotation.CallSuper
import org.commcare.dalvik.R
import org.junit.Before
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.MockitoAnnotations
import org.robolectric.shadows.ShadowLooper

/**
 * Base test class for PersonalIdPhoneFragment tests.
 * Inherits integrity-token mock setup from [BasePersonalIdConfigurationTest] and adds
 * phone-fragment-specific setup (Mockito annotations, mocked Location, activity host).
 */
abstract class BasePersonalIdPhoneFragmentTest : BasePersonalIdConfigurationTest<PersonalIdPhoneFragment>() {
    protected lateinit var mocksCloseable: AutoCloseable

    @Mock
    protected lateinit var mockLocation: Location

    @Before
    @CallSuper
    override fun setUp() {
        super.setUp()
        mocksCloseable = MockitoAnnotations.openMocks(this)
        mockLocation()
        setUpPersonalIdActivityWithFragment()
    }

    protected fun mockLocation() {
        `when`(mockLocation.latitude).thenReturn(37.7749)
        `when`(mockLocation.longitude).thenReturn(-122.4194)
        `when`(mockLocation.hasAccuracy()).thenReturn(true)
        `when`(mockLocation.accuracy).thenReturn(10.0f)
    }

    protected fun setUpPersonalIdActivityWithFragment() {
        bootActivity()
        captureNavFragment()

        activity.runOnUiThread {
            installTestNavController(fragment.requireView(), R.id.personalid_phone_fragment)
        }
        ShadowLooper.idleMainLooper()
    }

    @CallSuper
    override fun tearDown() {
        activityController.pause().stop().destroy()
        mocksCloseable.close()
        super.tearDown()
    }
}
