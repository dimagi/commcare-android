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
 * progress-bar capabilities and title it answers for its base activity, plus which home buttons its
 * UI controller suppresses.
 *
 * Why these live together and keep the host in the class name: every row below is a point where the
 * two `HomeScreenBaseActivity` hosts disagree. [RootMenuHomeActivity] returns `true` from
 * `usesSubmissionProgressBar()` where this host returns `false`, defers
 * `shouldShowSyncItemInActionBar()` to `useRootModuleMenuAsHomeScreen()`, overrides neither
 * `isTopNavEnabled()` nor `shouldHighlightSeatedApp()` nor `getActivityTitle()`, and has no button
 * grid for `getHiddenButtons()` to suppress at all. As CCCT-2685 and its sibling slices move the
 * shared launch/action logic into the coordinator, this per-host divergence is what is left behind
 * here — so these pins get *more* host-specific over time, not less. Behaviour that the coordinator
 * will own is pinned in the host-agnostic `Home*` classes instead.
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
        // HiddenPreferences.isIncompleteFormsEnabled() has no public setter; it reads the
        // "cc-show-incomplete" app preference directly, so write that preference key here.
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
        // DeveloperPreferences.isHomeReportEnabled() has no public setter, but it already
        // defaults to disabled (missing "cc-home-report" pref resolves to NO), so this pins the
        // default characterization rather than an explicitly-set one.
        val home = buildHome()

        assertFalse(homeButtonLabels(home).contains(Localization.get("home.report")))
    }

    // endregion
}
