package org.commcare.fragments.personalId

import androidx.annotation.CallSuper
import org.commcare.android.database.connect.models.PersonalIdSessionData
import org.commcare.dalvik.R
import org.junit.After
import org.junit.Before

/**
 * Base test class for PersonalIdNameFragment tests.
 * Sets up a PersonalIdActivity, populates the session ViewModel, and replaces the
 * default start fragment with PersonalIdNameFragment under a TestNavHostController.
 */
abstract class BasePersonalIdNameFragmentTest : BasePersonalIdConfigurationTest<PersonalIdNameFragment>() {
    protected open fun buildSessionData(): PersonalIdSessionData =
        PersonalIdSessionData(
            requiredLock = PersonalIdSessionData.PIN,
            demoUser = false,
            token = TEST_SESSION_TOKEN,
        )

    @Before
    @CallSuper
    override fun setUp() {
        super.setUp()
        launchFragmentForTest(buildSessionData(), R.id.personalid_name) {
            PersonalIdNameFragment()
        }
    }

    @After
    @CallSuper
    override fun tearDown() {
        activityController.pause().stop().destroy()
        super.tearDown()
    }

    companion object {
        const val TEST_SESSION_TOKEN: String = "test_session_token_abc"
    }
}
