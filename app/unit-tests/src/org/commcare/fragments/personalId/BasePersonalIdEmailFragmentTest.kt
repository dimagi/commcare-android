package org.commcare.fragments.personalId

import android.os.Bundle
import androidx.annotation.CallSuper
import org.commcare.android.database.connect.models.PersonalIdSessionData
import org.commcare.dalvik.R
import org.junit.Before

/**
 * Base test class for PersonalIdEmailFragment tests.
 * Inherits integrity-token mock setup from [BasePersonalIdConfigurationTest] and adds
 * email-fragment-specific setup: seeds session data and navigates the real NavController
 * to the email destination with the mandatory [EmailWorkFlow] argument.
 */
abstract class BasePersonalIdEmailFragmentTest : BasePersonalIdConfigurationTest<PersonalIdEmailFragment>() {
    @Before
    @CallSuper
    override fun setUp() {
        super.setUp()
        setUpPersonalIdActivityWithEmailFragment()
    }

    protected fun setUpPersonalIdActivityWithEmailFragment(workflow: EmailWorkFlow = EmailWorkFlow.REGISTRATION) {
        val args =
            Bundle().apply {
                putSerializable(PersonalIdEmailFragment.ARG_EMAIL_WORKFLOW, workflow)
            }

        // The email fragment reads PersonalIdSessionData from an activity-scoped ViewModel, so
        // seed an empty instance (upstream fragments populate it in production) to avoid an NPE.
        navigateToFragment(PersonalIdSessionData(), R.id.personalid_email, args)
    }

    @CallSuper
    override fun tearDown() {
        activityController.pause().stop().destroy()
        super.tearDown()
    }
}
