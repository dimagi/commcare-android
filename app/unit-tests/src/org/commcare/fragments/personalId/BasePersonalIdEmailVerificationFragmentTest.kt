package org.commcare.fragments.personalId

import android.os.Bundle
import androidx.annotation.CallSuper
import org.commcare.android.database.connect.models.PersonalIdSessionData
import org.commcare.dalvik.R
import org.junit.Before

/**
 * Base test class for PersonalIdEmailVerificationFragment tests.
 * Inherits the integrity-token mock from [BasePersonalIdConfigurationTest] and adds
 * verification-fragment-specific setup: seeds session data and navigates the real
 * NavController to the email-verification destination with the mandatory `email` and
 * `workflow` arguments populated.
 */
abstract class BasePersonalIdEmailVerificationFragmentTest : BasePersonalIdConfigurationTest<PersonalIdEmailVerificationFragment>() {
    @Before
    @CallSuper
    override fun setUp() {
        super.setUp()
        setUpPersonalIdActivityWithEmailVerificationFragment()
    }

    protected fun setUpPersonalIdActivityWithEmailVerificationFragment(
        email: String = TEST_EMAIL,
        workflow: EmailWorkFlow = EmailWorkFlow.REGISTRATION,
    ) {
        val args =
            Bundle().apply {
                putString(ARG_EMAIL, email)
                putSerializable(ARG_WORKFLOW, workflow)
            }

        navigateToFragment(PersonalIdSessionData(token = "test-token"), R.id.personalid_email_verification, args)
    }

    @CallSuper
    override fun tearDown() {
        activityController.pause().stop().destroy()
        super.tearDown()
    }

    companion object {
        const val TEST_EMAIL = "user@example.com"

        // Safe Args reads these keys from the bundle; names match `android:name` in nav_graph_personalid.xml.
        private const val ARG_EMAIL = "email"
        private const val ARG_WORKFLOW = "workflow"
    }
}
