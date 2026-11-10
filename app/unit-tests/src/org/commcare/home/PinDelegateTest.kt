package org.commcare.home

import android.app.Activity.RESULT_CANCELED
import android.app.Activity.RESULT_OK
import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.commcare.CommCareApplication
import org.commcare.CommCareTestApplication
import org.commcare.activities.BaseHomeScreenActivityTest.Companion.TEST_APP_PATH
import org.commcare.activities.BaseHomeScreenActivityTest.Companion.TEST_PASSWORD
import org.commcare.activities.BaseHomeScreenActivityTest.Companion.TEST_USER
import org.commcare.activities.CreatePinActivity
import org.commcare.activities.LoginActivity
import org.commcare.activities.LoginMode
import org.commcare.android.util.TestAppInstaller
import org.commcare.preferences.DeveloperPreferences
import org.commcare.preferences.PrefValues
import org.javarosa.core.services.locale.Localization
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@Config(application = CommCareTestApplication::class)
@RunWith(AndroidJUnit4::class)
class PinDelegateTest {
    private lateinit var host: FakeHomeActivityHost
    private lateinit var session: FakeSeatedAppSession
    private lateinit var pin: PinDelegate

    @Before
    fun setUp() {
        TestAppInstaller.installAppAndLogin(TEST_APP_PATH, TEST_USER, TEST_PASSWORD)
        host = FakeHomeActivityHost()
        session = FakeSeatedAppSession()
        pin = PinDelegate(host, session)
        setOfferPinForLogin(true)
    }

    @Test
    fun `a password login offers pin creation`() {
        host.launchIntent = passwordLogin()

        pin.checkLaunchConditions()

        assertEquals(1, host.shownDialogs.size)
    }

    @Test
    fun `a password login is not offered pin creation once the user declined it for good`() {
        session.pinCreationDismissed = true
        host.launchIntent = passwordLogin()

        pin.checkLaunchConditions()

        assertTrue(host.shownDialogs.isEmpty())
    }

    @Test
    fun `switching to password mode by hand brings the offer back`() {
        session.pinCreationDismissed = true
        host.launchIntent = passwordLogin().putExtra(LoginActivity.MANUAL_SWITCH_TO_PW_MODE, true)

        pin.checkLaunchConditions()

        assertEquals(1, host.shownDialogs.size)
    }

    @Test
    fun `a password login is not offered pin creation when the app turns pins off`() {
        setOfferPinForLogin(false)
        host.launchIntent = passwordLogin()

        pin.checkLaunchConditions()

        assertTrue(host.shownDialogs.isEmpty())
        assertTrue(host.startedForResult.isEmpty())
    }

    @Test
    fun `a set pin is confirmed with a toast`() {
        assertTrue(pin.onActivityResult(HomeRequestCodes.CREATE_PIN, RESULT_OK, Intent()))

        assertEquals(listOf(Localization.get("pin.set.success")), host.shownShortToasts)
        assertEquals(0, session.closeUserSessionCount)
    }

    @Test
    fun `a cancelled pin creation says no pin was set`() {
        assertTrue(pin.onActivityResult(HomeRequestCodes.CREATE_PIN, RESULT_CANCELED, null))

        assertEquals(listOf(Localization.get("pin.not.set")), host.shownShortToasts)
    }

    @Test
    fun `choosing to remember the password ends the session without a toast`() {
        val result = Intent().putExtra(CreatePinActivity.CHOSE_REMEMBER_PASSWORD, true)

        assertTrue(pin.onActivityResult(HomeRequestCodes.CREATE_PIN, RESULT_OK, result))

        assertEquals(1, session.closeUserSessionCount)
        assertTrue(host.shownShortToasts.isEmpty())
    }

    @Test
    fun `results for other screens are left alone`() {
        assertFalse(pin.onActivityResult(HomeRequestCodes.PREFERENCES_ACTIVITY, RESULT_OK, null))

        assertTrue(host.shownShortToasts.isEmpty())
        assertTrue(host.startedForResult.isEmpty())
    }

    private fun passwordLogin(): Intent = Intent().putExtra(LoginActivity.LOGIN_MODE, LoginMode.PASSWORD)

    private fun setOfferPinForLogin(offered: Boolean) {
        CommCareApplication
            .instance()
            .currentApp.appPreferences
            .edit()
            .putString(DeveloperPreferences.OFFER_PIN_FOR_LOGIN, if (offered) PrefValues.YES else PrefValues.NO)
            .apply()
    }
}
