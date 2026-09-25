package org.commcare.navdrawer

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import org.commcare.CommCareApplication
import org.commcare.activities.CommCareActivity
import org.commcare.activities.DispatchActivity
import org.commcare.activities.LoginActivity
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
import org.commcare.services.CommCareSessionService
import org.commcare.views.dialogs.StandardAlertDialog
import org.javarosa.core.services.Logger
import org.javarosa.core.services.locale.Localization

abstract class BaseDrawerActivity<T> : CommCareActivity<T>() {
    private var drawerController: BaseDrawerController? = null
    private lateinit var photoUpdater: PersonalIdPhotoUpdater

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        photoUpdater =
            PersonalIdPhotoUpdater(
                this,
                this,
                onSuccess = { photoBase64 -> drawerController!!.onPhotoUpdateSuccess(photoBase64) },
                onFailure = { _, _ -> drawerController!!.onPhotoUpdateFailure() },
            )
        checkForDrawerSetUp()
        NotificationBroadcastHelper.registerForNotifications(this, this) {
            drawerController?.refreshDrawerContent()
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

    protected open val drawerSection: NavItemType? = null

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
                photoUpdater,
            ) { navItemType: NavItemType ->
                handleDrawerItemClick(navItemType)
            }
        drawerController!!.setupDrawer()
    }

    protected open fun handleDrawerItemClick(itemType: NavItemType) {
        if (itemType == drawerSection) {
            closeDrawer()
            return
        }

        when (itemType) {
            NavItemType.OPPORTUNITIES -> {
                navigateToConnectMenu()
            }

            NavItemType.COMMCARE_APPS -> {
                closeDrawer()
                // Screens that aren't sections (Login, Setup) already are the CommCare Apps landing.
                if (drawerSection != null) {
                    promptToReturnToLogin()
                }
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
                    if (drawerSection != null) {
                        finish()
                    }
                }
            }
        }

    private fun promptToReturnToLogin() {
        if (!CommCareApplication.isSessionActive()) {
            returnToLogin()
            return
        }

        val dialog =
            StandardAlertDialog(
                getString(R.string.nav_drawer_switch_app_from_section_dialog_title),
                getString(R.string.nav_drawer_switch_app_from_section_dialog_message),
            )
        dialog.setPositiveButton(getString(R.string.nav_drawer_switch_app_dialog_confirm)) { _, _ ->
            dismissAlertDialog()
            if (!isBlockedByActiveSync()) {
                CommCareApplication.instance().closeUserSession()
                returnToLogin()
            }
        }
        dialog.setNegativeButton(getString(R.string.nav_drawer_switch_app_dialog_cancel)) { _, _ ->
            dismissAlertDialog()
        }
        showAlertDialog(dialog)
    }

    private fun returnToLogin() {
        val intent =
            Intent(this, DispatchActivity::class.java)
                .putExtra(LoginActivity.USER_TRIGGERED_LOGOUT, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        startActivity(intent)
        finish()
    }

    protected fun isBlockedByActiveSync(): Boolean {
        if (CommCareSessionService.sessionAliveLock.isLocked) {
            Toast
                .makeText(
                    this,
                    Localization.get("background.sync.logout.attempt.during.sync"),
                    Toast.LENGTH_LONG,
                ).show()
            return true
        }
        return false
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
