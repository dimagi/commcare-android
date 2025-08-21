package org.commcare.navdrawer

import android.os.Bundle
import android.view.MenuItem
import android.view.View
import org.commcare.activities.CommCareActivity
import org.commcare.connect.PersonalIdManager
import org.commcare.navdrawer.BaseDrawerController.NavItemType

abstract class BaseDrawerActivity<T> : CommCareActivity<T>() {

    protected var drawerController: BaseDrawerController? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        checkForDrawerSetUp()
    }

    private fun isPersonalIdLoggedIn(): Boolean {
        val personalIdManager = PersonalIdManager.getInstance()
        personalIdManager.init(this)
        return personalIdManager.isloggedIn();
    }

    protected open fun shouldShowDrawer(): Boolean {
        return false;
    }

    fun checkForDrawerSetUp(){
        if (shouldShowDrawer() && isPersonalIdLoggedIn()) {
            setupDrawerController()
        }
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
            NavItemType.OPPORTUNITIES -> {}
            NavItemType.COMMCARE_APPS -> {}
            NavItemType.PAYMENTS -> {}
            NavItemType.MESSAGING -> {}
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
}

