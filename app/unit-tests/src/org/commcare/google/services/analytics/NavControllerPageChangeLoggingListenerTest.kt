package org.commcare.google.services.analytics

import android.os.Build
import androidx.navigation.NavController
import androidx.navigation.fragment.FragmentNavigator
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.unmockkAll
import io.mockk.verify
import org.commcare.CommCareTestApplication
import org.commcare.connect.opportunity.OpportunityHomeFragment
import org.commcare.fragments.connect.ConnectDeliveryVisitsDetailFragment
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@Config(application = CommCareTestApplication::class, sdk = [Build.VERSION_CODES.Q])
@RunWith(AndroidJUnit4::class)
class NavControllerPageChangeLoggingListenerTest {
    private lateinit var listener: NavController.OnDestinationChangedListener

    @Before
    fun setUp() {
        listener = FirebaseAnalyticsUtil.getNavControllerPageChangeLoggingListener()
        mockkStatic(FirebaseAnalyticsUtil::class)
        every { FirebaseAnalyticsUtil.reportScreenView(any(), any()) } just runs
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `a destination is reported by its label and class`() {
        navigateTo(ConnectDeliveryVisitsDetailFragment::class.java.name, "fragment_connect_delivery_visits_detail")

        verify(exactly = 1) {
            FirebaseAnalyticsUtil.reportScreenView(
                "fragment_connect_delivery_visits_detail",
                ConnectDeliveryVisitsDetailFragment::class.java.name,
            )
        }
    }

    @Test
    fun `opportunity home is left to report its own surfaces`() {
        navigateTo(OpportunityHomeFragment::class.java.name, "fragment_opportunity_home")

        verify(exactly = 0) { FirebaseAnalyticsUtil.reportScreenView(any(), any()) }
    }

    private fun navigateTo(
        className: String,
        label: String,
    ) {
        val destination = mockk<FragmentNavigator.Destination>()
        every { destination.className } returns className
        every { destination.label } returns label
        val navController = mockk<NavController>()
        every { navController.currentDestination } returns destination

        listener.onDestinationChanged(navController, destination, null)
    }
}
