package org.commcare.activities

import android.app.Activity.RESULT_CANCELED
import android.app.Activity.RESULT_OK
import android.content.Intent
import org.javarosa.core.services.locale.Localization
import org.junit.Assert.assertEquals
import org.junit.Test
import org.robolectric.Shadows
import org.robolectric.shadows.ShadowToast

/**
 * Characterization pins for the PIN-related arms of `onActivityResultSessionSafe`:
 * `AUTHENTICATION_FOR_PIN` routing into PIN creation, and the two `CREATE_PIN` outcomes the user
 * sees as a toast.
 *
 * Scope: `onActivityResultSessionSafe` dispatches twelve request codes plus an early `RESULT_RESTART`
 * branch, and only the PIN codes are pinned here. The rest — `PREFERENCES_ACTIVITY`,
 * `ADVANCED_ACTIONS_ACTIVITY`, `GET_INCOMPLETE_FORM`, `GET_COMMAND`, `GET_CASE`, `MODEL_RESULT`,
 * `MAKE_REMOTE_POST`, `GET_REMOTE_DATA`, `IN_APP_UPDATE_REQUEST_CODE` — all drive session navigation
 * through `startNextSessionStepSafe()` and need a seated multi-step session to be meaningful; they
 * belong with the session-navigation slices rather than here. The third `CREATE_PIN` arm
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

        val started = Shadows.shadowOf(home).nextStartedActivityForResult.intent
        assertEquals(CreatePinActivity::class.java.name, started.component!!.className)
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
