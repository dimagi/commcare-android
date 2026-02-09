package org.commcare.navdrawer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import org.commcare.activities.CommCareActivity
import org.commcare.connect.ConnectActivityCompleteListener
import org.commcare.connect.ConnectNavHelper.unlockAndGoToConnectJobsList
import org.commcare.connect.ConnectNavHelper.unlockAndGoToMessaging
import org.commcare.connect.ConnectNavHelper.unlockAndGoToWorkHistory
import org.commcare.navdrawer.BaseDrawerController.NavItemType
import org.commcare.pn.helper.NotificationBroadcastHelper
import org.commcare.utils.FirebaseMessagingUtil
import org.javarosa.core.services.Logger

abstract class BaseDrawerActivity<T> : CommCareActivity<T>() {
    private var drawerController: BaseDrawerController? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        checkForDrawerSetUp()
        if (drawerController != null) {
            NotificationBroadcastHelper.registerForNotifications(this, this) {
                drawerController?.refreshDrawerContent()
            }
        }
    }

    override fun onResume() {
        super.onResume()
    }

    override fun onPause() {
        super.onPause()
    }

    fun refreshDrawer() {
        drawerController?.refreshDrawerContent()
    }

    protected open fun shouldShowDrawer(): Boolean = false

    protected open fun shouldHighlightSeatedApp(): Boolean = false

    fun checkForDrawerSetUp() {
        if (shouldShowDrawer()) {
            setupDrawerController()
        }
    }

    private fun setupDrawerController() {
        val rootView = findViewById<View>(android.R.id.content)
        val drawerRefs = DrawerViewRefs(rootView)
        drawerController =
            BaseDrawerController(
                this,
                drawerRefs,
                shouldHighlightSeatedApp(),
            ) { navItemType: NavItemType, recordId: String? ->
                handleDrawerItemClick(navItemType, recordId)
            }
        drawerController!!.setupDrawer()
    }

    protected open fun handleDrawerItemClick(
        itemType: NavItemType,
        recordId: String?,
    ) {
        when (itemType) {
            NavItemType.OPPORTUNITIES -> {
                navigateToConnectMenu()
            }
            NavItemType.COMMCARE_APPS -> { /* No nav, expands/collapses menu */ }
            NavItemType.PAYMENTS -> {}
            NavItemType.MESSAGING -> {
                navigateToMessaging()
            }
            NavItemType.WORK_HISTORY -> {
                navigateToWorkHistory()
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (drawerController != null && drawerController!!.handleOptionsItem(item)) {
            return true
        } else {
            return super.onOptionsItemSelected(item)
        }
    }

    protected fun navigateToConnectMenu() {
        unlockAndGoToConnectJobsList(
            this,
            object : ConnectActivityCompleteListener {
                override fun connectActivityComplete(
                    success: Boolean,
                    error: String?,
                ) {
                    if (success) {
                        closeDrawer()
                    }
                }
            },
        )
    }

    protected fun navigateToMessaging() {
        unlockAndGoToMessaging(
            this,
            object : ConnectActivityCompleteListener {
                override fun connectActivityComplete(
                    success: Boolean,
                    error: String?,
                ) {
                    if (success) {
                        closeDrawer()
                    }
                }
            },
        )
    }

    protected fun navigateToWorkHistory() {
        unlockAndGoToWorkHistory(
            this,
            object : ConnectActivityCompleteListener {
                override fun connectActivityComplete(
                    success: Boolean,
                    error: String?,
                ) {
                    if (success) {
                        closeDrawer()
                    }
                }
            },
        )
    }

    protected fun closeDrawer() {
        if (drawerController == null) {
            Logger.exception(
                "There was an error closing the app's sidebar.",
                NullPointerException("The BaseDrawerController is null!"),
            )
        }

        drawerController?.closeDrawer()
    }

    fun openDrawer() {
        if (drawerController == null) {
            Logger.exception(
                "There was an error opening the app's sidebar.",
                NullPointerException("The BaseDrawerController is null!"),
            )
        }

        drawerController?.openDrawer()
    }
}
