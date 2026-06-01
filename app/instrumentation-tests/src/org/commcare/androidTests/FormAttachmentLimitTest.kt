package org.commcare.androidTests

import androidx.test.espresso.Espresso
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withTagValue
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.commcare.activities.FormEntryActivity
import org.commcare.annotations.BrowserstackTests
import org.commcare.dalvik.R
import org.commcare.utils.CustomMatchers.hasDrawable
import org.commcare.utils.InstrumentationUtility
import org.commcare.utils.InstrumentationUtility.chooseImage
import org.commcare.utils.InstrumentationUtility.login
import org.commcare.utils.InstrumentationUtility.nextPage
import org.commcare.utils.InstrumentationUtility.openForm
import org.commcare.utils.InstrumentationUtility.prevPage
import org.commcare.views.widgets.ImageWidget.IMAGE_VIEW_TAG
import org.hamcrest.Matchers.equalTo
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
@BrowserstackTests
class FormAttachmentLimitTest : BaseTest() {
    private val CCZ_NAME = "media_capture.ccz"
    private val APP_NAME = "Media Capture Test"

    @Before
    fun setup() {
        installApp(APP_NAME, CCZ_NAME, false)
        login("test_user_5", "123")

        // Change limit to 5
        FormEntryActivity.setMaxFormAttachmentsForTesting()
    }

    @Test
    fun testFormAttachmentLimit() {
        openForm(0, 1)

        // add 6 images, which is above the limit of 5
        chooseImage()
        nextPage()
        chooseImage()
        nextPage()
        chooseImage()
        nextPage()
        chooseImage()
        nextPage()
        chooseImage()
        nextPage()
        chooseImage()

        // verify that the error message is shown
        onView(withText("This form has reached the maximum of 5 attachments. To add a new one, remove an existing attachment first."))
            .check(matches(isDisplayed()))

        // dismiss the error message
        onView(withText(R.string.ok))
            .perform(click())

        // go back and remove an image
        prevPage()
        onView(withText(R.string.discard_image))
            .perform(click())

        // try again to add an image and verify that it is added successfully without error message
        nextPage()
        chooseImage()
        onView(withTagValue(equalTo(IMAGE_VIEW_TAG)))
            .check(matches(hasDrawable()))
    }

    @After
    fun teardown() {
        FormEntryActivity.restoreMaxFormAttachmentsToDefault()

        Espresso.pressBack()
        onView(withText(R.string.do_not_save))
            .perform(click())

        InstrumentationUtility.logout()
    }
}
