package org.commcare.androidTests

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.apache.commons.lang3.RandomStringUtils
import org.commcare.utils.HQApi
import org.commcare.utils.InstrumentationUtility
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
class DeletedUserTest: BaseTest() {

    companion object {
        const val CCZ_NAME = "integration_test_app.ccz";
        const val APP_NAME = "Integration Tests";
    }

    @Before
    fun setup() {
        installApp(APP_NAME, CCZ_NAME);
    }

    @Test
    fun testUserAccountDeletion() {
        // Create a user account
        val userName = RandomStringUtils.random(10, true, true)
        val password = "Pass123!"
        val userId = HQApi.createUser(userName, password)

        // Login to the app using the new user account
        InstrumentationUtility.login(userName, password)

        // Delete the user
        val deleted = HQApi.deleteUser(userId)
        Assert.assertTrue(deleted)

        // Sync with server
        onView(withText("Sync with Server"))
                .perform(click())

        InstrumentationUtility.checkToast("Your account has been deleted, please contact your domain admin to request for restore")
    }

}
