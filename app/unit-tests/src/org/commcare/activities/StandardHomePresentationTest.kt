package org.commcare.activities

import org.commcare.CommCareApplication
import org.commcare.preferences.PrefValues
import org.javarosa.core.services.locale.Localization
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Characterization pins for what [StandardHomeActivity] contributes as a *host*: the nav/sync/
 * progress-bar capabilities and title it answers for its base activity, plus which buttons its grid
 * renders.
 *
 * Every row is a point where the two `HomeScreenBaseActivity` hosts disagree — [RootMenuHomeActivity]
 * answers differently or has no button grid at all — which is why the host is in the class name.
 *
 * This host's Connect behaviour is pinned in [HomeConnectJobProgressTest] and [HomeConnectTileTest].
 */
class StandardHomePresentationTest : BaseHomeScreenActivityTest() {
    // region capabilities answered for the base activity

    @Test
    fun `top nav is disabled`() {
        assertFalse(buildHome().isTopNavEnabled())
    }

    @Test
    fun `sync item is not shown in action bar`() {
        assertFalse(buildHome().shouldShowSyncItemInActionBar())
    }

    @Test
    fun `does not use submission progress bar`() {
        assertFalse(buildHome().usesSubmissionProgressBar())
    }

    @Test
    fun `activity title includes logged in username`() {
        val title = buildHome().activityTitle
        assertEquals(Localization.get("home.logged.in.message", arrayOf(TEST_USER)), title)
    }

    // endregion

    // region hidden home buttons

    @Test
    fun `incomplete button hidden when incomplete forms disabled`() {
        // isIncompleteFormsEnabled() has no setter; it reads this app preference directly.
        CommCareApplication
            .instance()
            .currentApp.appPreferences
            .edit()
            .putString("cc-show-incomplete", PrefValues.NO)
            .apply()
        val home = buildVisibleHome()

        assertFalse(homeButtonLabels(home).contains(Localization.get("home.forms.incomplete")))
    }

    @Test
    fun `report button hidden by default`() {
        // isHomeReportEnabled() defaults to disabled, so this pins the default rather than a set value.
        val home = buildVisibleHome()

        assertFalse(homeButtonLabels(home).contains(Localization.get("home.report")))
    }

    @Test
    fun `grid renders the buttons a single-app profile leaves visible`() {
        // The complement of the two rows above: what a plain form_nav_tests login actually sees, so a
        // button silently disappearing from the grid fails here rather than passing an assertFalse.
        val home = buildVisibleHome()

        assertEquals(
            listOf(
                Localization.get("home.start"),
                Localization.get("home.forms.saved"),
                Localization.get("home.forms.incomplete"),
                Localization.get("home.sync"),
                Localization.get("home.logout"),
            ),
            homeButtonLabels(home),
        )
    }

    // endregion
}
