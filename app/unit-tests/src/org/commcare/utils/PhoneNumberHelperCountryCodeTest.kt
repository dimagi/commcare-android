package org.commcare.utils

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
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
    private lateinit var fakeProvider: FakeCountryCodeSignalProvider

    @Before
    fun setUp() {
        helper = PhoneNumberHelper.getInstance(ApplicationProvider.getApplicationContext())
        fakeProvider = FakeCountryCodeSignalProvider()
    }

    @Test
    fun simPresent_returnsSimBasedCode() {
        fakeProvider.simCountryIso = "in"
        fakeProvider.networkCountryIso = "ke"
        fakeProvider.localeCountry = "US"

        val result = helper.getDefaultCountryCode(
            ApplicationProvider.getApplicationContext(), fakeProvider)
        assertEquals("+91", result)
    }

    @Test
    fun simAbsent_networkPresent_returnsNetworkBasedCode() {
        fakeProvider.simCountryIso = ""
        fakeProvider.networkCountryIso = "ke"
        fakeProvider.localeCountry = "US"

        val result = helper.getDefaultCountryCode(
            ApplicationProvider.getApplicationContext(), fakeProvider)
        assertEquals("+254", result)
    }

    @Test
    fun simAndNetworkAbsent_localePresent_returnsLocaleBasedCode() {
        fakeProvider.simCountryIso = ""
        fakeProvider.networkCountryIso = ""
        fakeProvider.localeCountry = "US"

        val result = helper.getDefaultCountryCode(
            ApplicationProvider.getApplicationContext(), fakeProvider)
        assertEquals("+1", result)
    }

    @Test
    fun allSignalsAbsent_returnsEmpty() {
        fakeProvider.simCountryIso = ""
        fakeProvider.networkCountryIso = ""
        fakeProvider.localeCountry = ""

        val result = helper.getDefaultCountryCode(
            ApplicationProvider.getApplicationContext(), fakeProvider)
        assertEquals("", result)
    }

    @Test
    fun nullSignals_returnsEmpty() {
        fakeProvider.simCountryIso = null
        fakeProvider.networkCountryIso = null
        fakeProvider.localeCountry = null

        val result = helper.getDefaultCountryCode(
            ApplicationProvider.getApplicationContext(), fakeProvider)
        assertEquals("", result)
    }

    @Test
    fun simUnrecognizedIso_fallsThrough_toNetwork() {
        fakeProvider.simCountryIso = "zz"
        fakeProvider.networkCountryIso = "ke"
        fakeProvider.localeCountry = "US"

        val result = helper.getDefaultCountryCode(
            ApplicationProvider.getApplicationContext(), fakeProvider)
        assertEquals("+254", result)
    }

    @Test
    fun priorityOrder_simWinsOverNetworkAndLocale() {
        fakeProvider.simCountryIso = "gb"
        fakeProvider.networkCountryIso = "de"
        fakeProvider.localeCountry = "FR"

        val result = helper.getDefaultCountryCode(
            ApplicationProvider.getApplicationContext(), fakeProvider)
        assertEquals("+44", result)
    }

    @Test
    fun priorityOrder_networkWinsOverLocale() {
        fakeProvider.simCountryIso = ""
        fakeProvider.networkCountryIso = "de"
        fakeProvider.localeCountry = "FR"

        val result = helper.getDefaultCountryCode(
            ApplicationProvider.getApplicationContext(), fakeProvider)
        assertEquals("+49", result)
    }

    @Test
    fun uppercaseIso_worksCorrectly() {
        fakeProvider.simCountryIso = "IN"
        fakeProvider.networkCountryIso = ""
        fakeProvider.localeCountry = ""

        val result = helper.getDefaultCountryCode(
            ApplicationProvider.getApplicationContext(), fakeProvider)
        assertEquals("+91", result)
    }
}
