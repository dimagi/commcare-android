package org.commcare.activities

import android.os.Bundle
import androidx.navigation.FloatingWindow
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil
import org.commcare.navdrawer.BaseDrawerActivity

abstract class NavigationHostCommCareActivity<T> : BaseDrawerActivity<T>() {
    private var destinationListener: NavController.OnDestinationChangedListener? = null
    private val topLevelListener =
        NavController.OnDestinationChangedListener { _, _, _ -> refreshDrawerTopLevel() }

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
        navController.addOnDestinationChangedListener(topLevelListener)
    }

    override fun onPause() {
        super.onPause()
        destinationListener?.let { navController.removeOnDestinationChangedListener(it) }
        navController.removeOnDestinationChangedListener(topLevelListener)
    }

    override fun onDestroy() {
        super.onDestroy()
        destinationListener = null
    }

    /** Whether the current destination shows the sidebar rather than back; by default only the graph's start destination does. */
    protected open fun isCurrentDestinationTopLevel(): Boolean =
        navController.currentDestination?.id == navController.graph.startDestinationId

    /** Whether the sidebar is kept closed even where the sidebar icon shows; never by default. */
    protected open fun isDrawerLocked(): Boolean = false

    /** Re-applies [isCurrentDestinationTopLevel] and [isDrawerLocked] after a change they depend on. */
    fun refreshDrawerTopLevel() {
        val destination = navController.currentDestination ?: return
        if (destination !is FloatingWindow) {
            setDrawerTopLevel(isCurrentDestinationTopLevel(), isDrawerLocked())
        }
    }

    protected abstract fun getLayoutResource(): Int

    protected abstract fun getHostFragment(): NavHostFragment
}
