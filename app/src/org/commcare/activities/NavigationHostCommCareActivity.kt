package org.commcare.activities

import android.os.Bundle
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil
import org.commcare.navdrawer.BaseDrawerActivity

abstract class NavigationHostCommCareActivity<T> : BaseDrawerActivity<T>() {
    private var destinationListener: NavController.OnDestinationChangedListener? = null

    protected lateinit var navController: NavController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(getLayoutResource())
        destinationListener = FirebaseAnalyticsUtil.getNavControllerPageChangeLoggingListener()
        navController = getHostFragment().navController
    }

    override fun onResume() {
        super.onResume()
        destinationListener?.let { navController.addOnDestinationChangedListener(it) }
    }

    override fun onPause() {
        super.onPause()
        destinationListener?.let { navController.removeOnDestinationChangedListener(it) }
    }

    override fun onDestroy() {
        super.onDestroy()
        destinationListener = null
    }

    protected abstract fun getLayoutResource(): Int

    protected abstract fun getHostFragment(): NavHostFragment
}
