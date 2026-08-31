package org.commcare.utils

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.every
import io.mockk.mockk
import org.commcare.CommCareTestApplication
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@Config(application = CommCareTestApplication::class)
@RunWith(AndroidJUnit4::class)
class PhoneNumberHelperCountryCodeTest {

    private lateinit var helper: PhoneNumberHelper
    private lateinit var provider: CountryCodeSignalProvider

    @Before
    fun setUp() {
        helper = PhoneNumberHelper.getInstance(ApplicationProvider.getApplicationContext())
        provider = mockk()
    }

    @Test
    fun simPresent_returnsSimBasedCode() {
        every { provider.simCountryIso } returns "in"
        every { provider.networkCountryIso } returns "ke"
        every { provider.localeCountry } returns "US"

        val result = helper.getDefaultCountryCode(provider)
        assertEquals("+91", result)
    }

    @Test
    fun simAbsent_networkPresent_returnsNetworkBasedCode() {
        every { provider.simCountryIso } returns ""
        every { provider.networkCountryIso } returns "ke"
        every { provider.localeCountry } returns "US"

        val result = helper.getDefaultCountryCode(provider)
        assertEquals("+254", result)
    }

    @Test
    fun simAndNetworkAbsent_localePresent_returnsLocaleBasedCode() {
        every { provider.simCountryIso } returns ""
        every { provider.networkCountryIso } returns ""
        every { provider.localeCountry } returns "US"

        val result = helper.getDefaultCountryCode(provider)
        assertEquals("+1", result)
    }

    @Test
    fun allSignalsAbsent_returnsEmpty() {
        every { provider.simCountryIso } returns ""
        every { provider.networkCountryIso } returns ""
        every { provider.localeCountry } returns ""

        val result = helper.getDefaultCountryCode(provider)
        assertEquals("", result)
    }

    @Test
    fun simUnrecognizedIso_fallsThrough_toNetwork() {
        every { provider.simCountryIso } returns "zz"
        every { provider.networkCountryIso } returns "ke"
        every { provider.localeCountry } returns "US"

        val result = helper.getDefaultCountryCode(provider)
        assertEquals("+254", result)
    }

    @Test
    fun priorityOrder_simWinsOverNetworkAndLocale() {
        every { provider.simCountryIso } returns "gb"
        every { provider.networkCountryIso } returns "de"
        every { provider.localeCountry } returns "FR"

        val result = helper.getDefaultCountryCode(provider)
        assertEquals("+44", result)
    }

    @Test
    fun priorityOrder_networkWinsOverLocale() {
        every { provider.simCountryIso } returns ""
        every { provider.networkCountryIso } returns "de"
        every { provider.localeCountry } returns "FR"

        val result = helper.getDefaultCountryCode(provider)
        assertEquals("+49", result)
    }

    @Test
    fun uppercaseIso_worksCorrectly() {
        every { provider.simCountryIso } returns "IN"
        every { provider.networkCountryIso } returns ""
        every { provider.localeCountry } returns ""

        val result = helper.getDefaultCountryCode(provider)
        assertEquals("+91", result)
    }
}
