package org.commcare.navdrawer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.view.MenuItem
import android.view.View
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import org.commcare.activities.CommCareActivity
import org.commcare.connect.ConnectActivityCompleteListener
import org.commcare.connect.ConnectNavHelper.unlockAndGoToConnectJobsList
import org.commcare.connect.ConnectNavHelper.unlockAndGoToMessaging
import org.commcare.navdrawer.BaseDrawerController.NavItemType
import org.commcare.utils.FirebaseMessagingUtil
import android.os.Bundle

abstract class BaseDrawerActivity<T> : CommCareActivity<T>() {

    private var drawerController: BaseDrawerController? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        checkForDrawerSetUp()
    }
    override fun onResume() {
        super.onResume()
        LocalBroadcastManager.getInstance(this).registerReceiver(
            messagingUpdateReceiver,
            IntentFilter(FirebaseMessagingUtil.MESSAGING_UPDATE_BROADCAST)
        )
    }

    override fun onPause() {
        super.onPause()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(messagingUpdateReceiver)
    }

    fun refreshDrawer() {
        drawerController?.refreshDrawerContent()
    }

    private val messagingUpdateReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            drawerController?.refreshDrawerContent()
        }
    }

    protected open fun shouldShowDrawer(): Boolean {
        return false
    }

    protected open fun shouldHighlightSeatedApp(): Boolean {
        return false
    }

    fun checkForDrawerSetUp(){
        if (shouldShowDrawer()) {
            setupDrawerController()
        }
    }

    private fun setupDrawerController() {
        val rootView = findViewById<View>(android.R.id.content)
        val drawerRefs = DrawerViewRefs(rootView)
        drawerController = BaseDrawerController(
            this,
            drawerRefs,
            shouldHighlightSeatedApp()
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

