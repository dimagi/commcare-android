package org.commcare.fragments.personalId

import android.os.Bundle
import androidx.annotation.CallSuper
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.NavHostFragment
import org.commcare.activities.connect.PersonalIdActivity
import org.commcare.activities.connect.viewmodel.PersonalIdSessionDataViewModel
import org.commcare.android.database.connect.models.PersonalIdSessionData
import org.commcare.dalvik.R
import org.junit.Before
import org.robolectric.Robolectric
import org.robolectric.android.controller.ActivityController
import org.robolectric.shadows.ShadowLooper

/**
 * Base test class for PersonalIdEmailVerificationFragment tests.
 * Inherits the integrity-token mock from [BasePersonalIdConfigurationTest] and adds
 * verification-fragment-specific setup: seeds session data and navigates the real
 * NavController to the email-verification destination with the mandatory `email` and
 * `workflow` arguments populated.
 */
abstract class BasePersonalIdEmailVerificationFragmentTest : BasePersonalIdConfigurationTest() {
    protected lateinit var activityController: ActivityController<PersonalIdActivity>
    protected lateinit var activity: PersonalIdActivity
    protected lateinit var fragment: PersonalIdEmailVerificationFragment

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
        activityController = Robolectric.buildActivity(PersonalIdActivity::class.java)
        activity =
            activityController
                .create()
                .start()
                .resume()
                .get()

        ViewModelProvider(activity)
            .get(PersonalIdSessionDataViewModel::class.java)
            .personalIdSessionData = PersonalIdSessionData(token = "test-token")

        val navHostFragment =
            activity.supportFragmentManager
                .findFragmentById(R.id.nav_host_fragment_connectid) as NavHostFragment

        val args =
            Bundle().apply {
                putString(ARG_EMAIL, email)
                putSerializable(ARG_WORKFLOW, workflow)
            }

        activity.runOnUiThread {
            navHostFragment.navController.navigate(R.id.personalid_email_verification, args)
        }
        ShadowLooper.idleMainLooper()

        fragment =
            navHostFragment.childFragmentManager
                .primaryNavigationFragment as PersonalIdEmailVerificationFragment
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
