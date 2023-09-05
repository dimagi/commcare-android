package org.commcare.android.tests.formentry

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.widget.Button
import android.widget.ImageButton
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.Intents.intending
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.commcare.CommCareTestApplication
import org.commcare.activities.FormEntryActivity
import org.commcare.android.resource.installers.XFormAndroidInstaller
import org.commcare.android.util.ActivityLaunchUtils
import org.commcare.android.util.TestAppInstaller
import org.commcare.android.util.TestUtils
import org.commcare.commcaresupportlibrary.identity.BiometricIdentifier
import org.commcare.commcaresupportlibrary.identity.BiometricUtils
import org.commcare.commcaresupportlibrary.identity.IdentityResponseBuilder
import org.commcare.commcaresupportlibrary.identity.model.IdentificationMatch
import org.commcare.commcaresupportlibrary.identity.model.MatchResult
import org.commcare.commcaresupportlibrary.identity.model.MatchStrength
import org.commcare.dalvik.R
import org.commcare.provider.IdentityCalloutHandler
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

@Config(application = CommCareTestApplication::class)
@RunWith(AndroidJUnit4::class)
class IdentityCalloutTests {

    @Rule
    @JvmField
    var intentsRule = ActivityScenarioRule(FormEntryActivity::class.java)

    var templates : HashMap<BiometricIdentifier, ByteArray> = HashMap(2)

    @Before
    fun setup() {
        Intents.init()
        XFormAndroidInstaller.registerAndroidLevelFormParsers()
        TestAppInstaller.installAppAndLogin(
                "jr://resource/commcare-apps/identity_callouts/profile.ccpr",
                "test", "123")

        // Initialize biometric templates
        templates[BiometricIdentifier.LEFT_INDEX_FINGER] =
            byteArrayOf(0, 0, -21, -67, 0, -64, 25, 62, -69, -124, -91, 29, -50, -107, 58)
        templates[BiometricIdentifier.LEFT_MIDDLE_FINGER] =
            byteArrayOf(122, -91, 114, 62, 107, -95, -69, 28, 110, 123, 72, 71, -86, -117, 126)
    }

    @After
    fun tearDown() {
        Intents.release()
    }

    @Test
    fun testRegistrationAndVerification() {
        val formEntryActivity = ActivityLaunchUtils.launchFormEntry("m0-f0")

        intendRegistrationIntent()
        performIntentCallout(formEntryActivity)
        TestUtils.assertFormValue("/data/identity_guid", "test-case-unique-guid")
        TestUtils.assertFormValue("/data/duplicate_guid", "")
        TestUtils.assertFormValue("/data/duplicate_score", "")
        TestUtils.assertFormValue("/data/duplicate_strength", "")

        // Dupicate
        intendDuplicatesDuringRegistration()
        performIntentCallout(formEntryActivity)
        TestUtils.assertFormValue("/data/identity_guid", "")
        TestUtils.assertFormValue("/data/duplicate_guid", "guid-2")
        TestUtils.assertFormValue("/data/duplicate_score", "90")
        TestUtils.assertFormValue("/data/duplicate_strength", "five_stars")

        // navigate to verification
        val nextButton = formEntryActivity.findViewById<ImageButton>(R.id.nav_btn_next)
        nextButton.performClick()

        // verification
        intendVerificationIntent()
        performIntentCallout(formEntryActivity)
        TestUtils.assertFormValue("/data/verify_guid", "test-case-unique-guid")
        TestUtils.assertFormValue("/data/verify_score", "90")
        TestUtils.assertFormValue("/data/verify_strength", "five_stars")
    }

    @Test
    fun testIdentificationMatchesHandling() {
        val identificationResponse = getIdentificationIntent()
        val guidToConfidenceMap = IdentityCalloutHandler.getConfidenceMatchesFromCalloutResponse(identificationResponse)
        assertEquals(guidToConfidenceMap.keyAt(0), "guid-2")
        assertEquals(guidToConfidenceMap.elementAt(0), "★★★★★")
        assertEquals(guidToConfidenceMap.keyAt(1), "guid-1")
        assertEquals(guidToConfidenceMap.elementAt(1), "★★★★")
        assertEquals(guidToConfidenceMap.keyAt(2), "guid-3")
        assertEquals(guidToConfidenceMap.elementAt(2), "★★★")
    }

    @Test
    fun testRegistrationWithTemplates() {
        val formEntryActivity = ActivityLaunchUtils.launchFormEntry("m0-f1")

        intendRegistrationWithTemplatesIntent()
        performIntentCallout(formEntryActivity)
        TestUtils.assertFormValue("/data/guid", "test-case-unique-guid")
        TestUtils.assertFormValue("/data/templates",
            BiometricUtils.convertMapTemplatesToBase64String(templates))
    }

    private fun getIdentificationIntent(): Intent {
        val identifications = ArrayList<IdentificationMatch>()
        identifications.add(IdentificationMatch("guid-1", MatchResult(80, MatchStrength.FOUR_STARS)))
        identifications.add(IdentificationMatch("guid-2", MatchResult(90, MatchStrength.FIVE_STARS)))
        identifications.add(IdentificationMatch("guid-3", MatchResult(70, MatchStrength.THREE_STARS)))
        return IdentityResponseBuilder.identificationResponse(identifications).build()
    }

    private fun intendVerificationIntent() {
        val verification = IdentityResponseBuilder
                .verificationResponse("test-case-unique-guid", MatchResult(90, MatchStrength.FIVE_STARS))
                .build()
        val result = Instrumentation.ActivityResult(Activity.RESULT_OK, verification)
        intending(hasAction("org.commcare.identity.bioverify")).respondWith(result)
    }

    private fun intendRegistrationIntent() {
        val registration = IdentityResponseBuilder
                .registrationResponse("test-case-unique-guid")
                .build()
        val result = Instrumentation.ActivityResult(Activity.RESULT_OK, registration)
        intending(hasAction("org.commcare.identity.bioenroll")).respondWith(result)
    }

    private fun intendRegistrationWithTemplatesIntent() {
        val registration = IdentityResponseBuilder
            .registrationResponse("test-case-unique-guid", templates)
            .build()
        val result = Instrumentation.ActivityResult(Activity.RESULT_OK, registration)
        intending(hasAction("org.commcare.identity.bioenroll")).respondWith(result)
    }

    private fun intendDuplicatesDuringRegistration() {
        val result = Instrumentation.ActivityResult(Activity.RESULT_OK, getIdentificationIntent())
        intending(hasAction("org.commcare.identity.bioenroll")).respondWith(result)
    }

    private fun performIntentCallout(formEntryActivity: FormEntryActivity) {
        (formEntryActivity.odkView.widgets[0].getChildAt(2) as Button).performClick()
        ShadowLooper.shadowMainLooper().idle()
    }
}
