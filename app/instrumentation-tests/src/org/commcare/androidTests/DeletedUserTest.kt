package org.commcare.androidTests

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.commcare.utils.HQApi
import org.commcare.utils.InstrumentationUtility
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
        val userName = "tempuser"
        val password = "Pass123!"
        HQApi.createUser(userName, password)

        // Login to the app using the new user account
        InstrumentationUtility.login(userName, password)

        // Delete the user
        HQApi.deleteUser(userName);

        // Sync with server
        onView(withText("Sync with Server"))
                .perform(click())

        // see Your account has been deleted
    }

}
