package org.commcare.navdrawer

import android.os.Bundle
import android.view.MenuItem
import android.view.View
import org.commcare.activities.CommCareActivity
import org.commcare.connect.ConnectActivityCompleteListener
import org.commcare.connect.ConnectNavHelper.unlockAndGoToConnectJobsList
import org.commcare.connect.ConnectNavHelper.unlockAndGoToMessaging
import org.commcare.connect.ConnectNavHelper.unlockAndGoToWorkHistory
import org.commcare.connect.PersonalIdManager
import org.commcare.dalvik.R
import org.commcare.navdrawer.BaseDrawerController.NavItemType
import org.commcare.navdrawer.NavDrawerHelper.drawerShownBefore
import org.commcare.navdrawer.NavDrawerHelper.setDrawerShown
import org.commcare.personalId.photo.PersonalIdPhotoUpdater
import org.commcare.pn.helper.NotificationBroadcastHelper
import org.commcare.utils.AppLogoutHelper
import org.javarosa.core.services.Logger

abstract class BaseDrawerActivity<T> : CommCareActivity<T>() {
    private var drawerController: BaseDrawerController? = null
    private val photoUpdater =
        PersonalIdPhotoUpdater(
            this,
            this,
            onSuccess = { photoBase64 -> drawerController!!.onPhotoUpdateSuccess(photoBase64) },
            onFailure = { _, _ -> drawerController!!.onPhotoUpdateFailure() },
        )

    override fun onContentChanged() {
        super.onContentChanged()
        checkForDrawerSetUp()
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        if (drawerController != null) {
            NotificationBroadcastHelper.registerForNotifications(this, this) {
                refreshDrawer()
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

    protected open val currentDrawerSection: NavItemType? = null

    protected open val isSidebarSection: Boolean = false

    private fun checkForDrawerSetUp() {
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
                photoUpdater,
            ) { navItemType: NavItemType ->
                handleDrawerItemClick(navItemType)
            }
        drawerController!!.setupDrawer()
    }

    protected open fun handleDrawerItemClick(itemType: NavItemType) {
        if (itemType == currentDrawerSection) {
            closeDrawer()
            return
        }

        when (itemType) {
            NavItemType.OPPORTUNITIES -> {
                navigateToConnectMenu()
            }

            NavItemType.COMMCARE_APPS -> {
                closeDrawer()
                AppLogoutHelper.promptToReturnToLogin(
                    this,
                    R.string.nav_drawer_switch_app_from_section_dialog_title,
                    R.string.nav_drawer_switch_app_from_section_dialog_message,
                )
            }

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
        unlockAndGoToConnectJobsList(this, listener = getDrawerNavigationListener())
    }

    protected fun navigateToMessaging() {
        unlockAndGoToMessaging(this, listener = getDrawerNavigationListener())
    }

    protected fun navigateToWorkHistory() {
        unlockAndGoToWorkHistory(this, listener = getDrawerNavigationListener())
    }

    private fun getDrawerNavigationListener() =
        object : ConnectActivityCompleteListener {
            override fun connectActivityComplete(
                success: Boolean,
                error: String?,
            ) {
                if (success) {
                    closeDrawer()
                    // A section is replaced by the one just opened rather than stacked beneath it.
                    if (isSidebarSection) {
                        finish()
                    }
                }
            }
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

    protected fun shouldShowDrawerAfterCheck(requirePersonalIDLogin: Boolean): Boolean {
        if (drawerShownBefore()) {
            return true
        }

        val personalIdManager = PersonalIdManager.getInstance()
        personalIdManager.init(this)
        val showDrawer =
            (!requirePersonalIDLogin || personalIdManager.isloggedIn()) &&
                personalIdManager.checkDeviceCompability()

        if (showDrawer) {
            setDrawerShown()
        }

        return showDrawer
    }

    protected fun setDrawerTopLevel(isTopLevel: Boolean) {
        drawerController?.setTopLevel(isTopLevel)
    }

    fun isShowingGlobalError(): Boolean = drawerController?.isShowingError() ?: false
}
