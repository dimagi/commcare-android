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
 * Base test class for PersonalIdEmailFragment tests.
 * Inherits integrity-token mock setup from [BasePersonalIdConfigurationTest] and adds
 * email-fragment-specific setup: seeds session data and navigates the real NavController
 * to the email destination with the mandatory isLegacyFlow / isRecovery arguments.
 */
abstract class BasePersonalIdEmailFragmentTest : BasePersonalIdConfigurationTest() {
    protected lateinit var activityController: ActivityController<PersonalIdActivity>
    protected lateinit var activity: PersonalIdActivity
    protected lateinit var fragment: PersonalIdEmailFragment

    @Before
    @CallSuper
    override fun setUp() {
        super.setUp()
        setUpPersonalIdActivityWithEmailFragment()
    }

    protected fun setUpPersonalIdActivityWithEmailFragment(
        isLegacyFlow: Boolean = false,
        isRecovery: Boolean = false,
    ) {
        activityController = Robolectric.buildActivity(PersonalIdActivity::class.java)
        activity =
            activityController
                .create()
                .start()
                .resume()
                .get()

        // The email fragment reads PersonalIdSessionData from an activity-scoped ViewModel.
        // In production the upstream fragments (phone/name/...) populate it; in tests we
        // skip those, so seed an empty instance here so onCreateView doesn't NPE.
        ViewModelProvider(activity)
            .get(PersonalIdSessionDataViewModel::class.java)
            .personalIdSessionData = PersonalIdSessionData()

        val navHostFragment =
            activity.supportFragmentManager
                .findFragmentById(R.id.nav_host_fragment_connectid) as NavHostFragment

        val args =
            Bundle().apply {
                putBoolean(PersonalIdEmailFragment.ARG_IS_LEGACY_FLOW, isLegacyFlow)
                putBoolean(PersonalIdEmailFragment.ARG_IS_RECOVERY, isRecovery)
            }

        activity.runOnUiThread {
            navHostFragment.navController.navigate(R.id.personalid_email, args)
        }
        ShadowLooper.idleMainLooper()

        fragment =
            navHostFragment.childFragmentManager
                .primaryNavigationFragment as PersonalIdEmailFragment
    }

    @CallSuper
    override fun tearDown() {
        activityController.pause().stop().destroy()
        super.tearDown()
    }
}
