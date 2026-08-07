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
class StandardHomePresentationTest : HomeScreenActivityTest() {
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
    fun `seated app is highlighted`() {
        assertTrue(buildHome().shouldHighlightSeatedApp())
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
        val home = buildHome()

        assertFalse(homeButtonLabels(home).contains(Localization.get("home.forms.incomplete")))
    }

    @Test
    fun `report button hidden by default`() {
        // isHomeReportEnabled() defaults to disabled, so this pins the default rather than a set value.
        val home = buildHome()

        assertFalse(homeButtonLabels(home).contains(Localization.get("home.report")))
    }

    // endregion
}
