package org.commcare.navdrawer

import android.view.MenuItem
import android.view.View
import org.commcare.activities.CommCareActivity
import org.commcare.connect.ConnectActivityCompleteListener
import org.commcare.connect.ConnectNavHelper.unlockAndGoToConnectJobsList
import org.commcare.connect.ConnectNavHelper.unlockAndGoToMessaging
import org.commcare.navdrawer.BaseDrawerController.NavItemType

abstract class BaseDrawerActivity<T> : CommCareActivity<T>() {

    private var drawerController: BaseDrawerController? = null

    override fun onResume() {
        super.onResume()
        if (shouldShowDrawer()) {
            setupDrawerController()
        }
    }

    protected open fun shouldShowDrawer(): Boolean {
        return false
    }

    private fun setupDrawerController() {
        val rootView = findViewById<View>(android.R.id.content)
        val drawerRefs = DrawerViewRefs(rootView)
        drawerController = BaseDrawerController(
            this,
            drawerRefs
        ) { navItemType: NavItemType, recordId: String? ->
            handleDrawerItemClick(navItemType, recordId)
        }
        drawerController!!.setupDrawer()
    }

    protected open fun handleDrawerItemClick(itemType: NavItemType, recordId: String?) {
        when (itemType) {
            NavItemType.OPPORTUNITIES -> { navigateToConnectMenu() }
            NavItemType.COMMCARE_APPS -> { /* No nav, expands/collapses menu */}
            NavItemType.PAYMENTS -> {}
            NavItemType.MESSAGING -> { navigateToMessaging() }
            NavItemType.WORK_HISTORY -> {}
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
        unlockAndGoToConnectJobsList(this, object : ConnectActivityCompleteListener {
            override fun connectActivityComplete(success: Boolean) {
                if (success) {
                    closeDrawer()
                }
            }
        })
    }

    protected fun navigateToMessaging() {
        unlockAndGoToMessaging(this, object : ConnectActivityCompleteListener {
            override fun connectActivityComplete(success: Boolean) {
                if (success) {
                    closeDrawer()
                }
            }
        })
    }

    protected fun closeDrawer() {
        drawerController?.closeDrawer()
    }
}

