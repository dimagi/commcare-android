package org.commcare.activities

import android.app.Activity.RESULT_CANCELED
import android.app.Activity.RESULT_OK
import android.content.Intent
import org.commcare.android.util.ActivityAssertions.assertStartedForResult
import org.javarosa.core.services.locale.Localization
import org.junit.Assert.assertEquals
import org.junit.Test
import org.robolectric.shadows.ShadowToast

/**
 * Characterization pins for the PIN-related arms of `onActivityResultSessionSafe`:
 * `AUTHENTICATION_FOR_PIN` routing into PIN creation, and the two `CREATE_PIN` outcomes the user
 * sees as a toast.
 *
 * The session-navigation arms (`GET_COMMAND`, `GET_CASE`, `MODEL_RESULT`) live in
 * [HomeSessionNavigationTest]. `PREFERENCES_ACTIVITY` and `ADVANCED_ACTIONS_ACTIVITY` are unpinned:
 * both only act on result codes the preference screens set. The third `CREATE_PIN` arm
 * (`CHOSE_REMEMBER_PASSWORD` -> `closeUserSession()`) is likewise unpinned: it tears down the session
 * the base fixture installs, so it needs a fixture that can assert on a closed session.
 */
class HomeActivityResultTest : BaseHomeScreenActivityTest() {
    @Test
    fun `pin authentication success launches create pin screen`() {
        val home = buildHome()

        home.onActivityResultSessionSafe(
            HomeScreenBaseActivity.AUTHENTICATION_FOR_PIN,
            RESULT_OK,
            Intent(),
        )

        assertStartedForResult(home, CreatePinActivity::class.java)
    }

    @Test
    fun `create pin success shows success toast`() {
        val home = buildHome()

        home.onActivityResultSessionSafe(HomeScreenBaseActivity.CREATE_PIN, RESULT_OK, Intent())

        assertEquals(Localization.get("pin.set.success"), ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun `create pin failure shows not-set toast`() {
        val home = buildHome()

        home.onActivityResultSessionSafe(HomeScreenBaseActivity.CREATE_PIN, RESULT_CANCELED, Intent())

        assertEquals(Localization.get("pin.not.set"), ShadowToast.getTextOfLatestToast())
    }
}
