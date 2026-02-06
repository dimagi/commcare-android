package org.commcare.navdrawer

import android.graphics.Color
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import org.commcare.CommCareApplication
import org.commcare.activities.CommCareActivity
import org.commcare.connect.ConnectConstants
import org.commcare.connect.ConnectNavHelper
import org.commcare.connect.PersonalIdManager
import org.commcare.connect.database.ConnectMessagingDatabaseHelper
import org.commcare.connect.database.ConnectUserDatabaseUtil
import org.commcare.dalvik.BuildConfig
import org.commcare.dalvik.R
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil
import org.commcare.personalId.PersonalIdFeatureFlagChecker.Companion.isFeatureEnabled
import org.commcare.personalId.PersonalIdFeatureFlagChecker.FeatureFlag.Companion.NOTIFICATIONS
import org.commcare.personalId.PersonalIdFeatureFlagChecker.FeatureFlag.Companion.WORK_HISTORY
import org.commcare.utils.GlobalErrorUtil
import org.commcare.utils.MultipleAppsUtil
import org.commcare.utils.NotificationUtil.getNotificationIcon
import org.commcare.views.ViewUtil
import org.commcare.views.dialogs.DialogCreationHelpers

class BaseDrawerController(
    private val activity: CommCareActivity<*>,
    private val binding: DrawerViewRefs,
    private val highlightSeatedApp: Boolean,
    private val onItemClicked: (NavItemType, String?) -> Unit,
) {
    private lateinit var drawerToggle: ActionBarDrawerToggle
    private lateinit var navDrawerAdapter: NavDrawerAdapter
    private var hasRefreshed = false

    /** Enum to represent navigation drawer menu items */
    enum class NavItemType {
        OPPORTUNITIES,
        COMMCARE_APPS,
        WORK_HISTORY,
        MESSAGING,
        PAYMENTS,
    }

    fun setupDrawer() {
        setupActionBarDrawerToggle()
        initializeAdapter()
        setupListeners()
        setupViews()
        if (refreshDrawerContent()) {
            openDrawer()
            binding.drawerLayout.postDelayed({
                ViewUtil.hideVirtualKeyboard(activity)
            }, 250)
        }
    }

    private fun setupActionBarDrawerToggle() {
        drawerToggle =
            object : ActionBarDrawerToggle(
                activity,
                binding.drawerLayout,
                binding.toolbar,
                R.string.nav_drawer_open,
                R.string.nav_drawer_close,
            ) {
                override fun onDrawerOpened(drawerView: View) {
                    super.onDrawerOpened(drawerView)
                    ViewUtil.hideVirtualKeyboard(activity)
                    FirebaseAnalyticsUtil.reportNavDrawerOpen()
                }

                override fun onDrawerSlide(
                    drawerView: View,
                    slideOffset: Float,
                ) {
                    super.onDrawerSlide(drawerView, slideOffset)
                    if (slideOffset > 0 && !hasRefreshed) {
                        refreshDrawerContent()
                        hasRefreshed = true
                    }
                    if (slideOffset == 0f) hasRefreshed = false
                }
            }
        binding.drawerLayout.addDrawerListener(drawerToggle)
        (activity as? AppCompatActivity)?.apply {
            drawerToggle.drawerArrowDrawable.color = Color.WHITE
            drawerToggle.syncState()
        }
    }

    private fun setupViews() {
        binding.versionText.text = "v ${BuildConfig.VERSION_NAME}"
    }

    private fun initializeAdapter() {
        navDrawerAdapter =
            NavDrawerAdapter(
                activity,
                emptyList(),
                onParentClick = {
                    FirebaseAnalyticsUtil.reportNavDrawerItemSelected(it.title)
                    onItemClicked(it.type, null)
                },
                onChildClick = { parentType, childItem ->
                    FirebaseAnalyticsUtil.reportNavDrawerItemSelected(childItem.childTitle)
                    onItemClicked(parentType, childItem.recordId)
                },
            )
        binding.navDrawerRecycler.layoutManager = LinearLayoutManager(activity)
        binding.navDrawerRecycler.adapter = navDrawerAdapter
    }

    private fun setupListeners() {
        binding.signInButton.setOnClickListener {
            PersonalIdManager
                .getInstance()
                .launchPersonalId(
                    activity,
                    ConnectConstants.PERSONAL_ID_SIGN_UP_LAUNCH,
                )
            closeDrawer()
        }
        binding.aboutView.setOnClickListener {
            DialogCreationHelpers.showAboutCommCareDialog(
                activity,
            )
        }
        binding.notificationView.setOnClickListener {
            ConnectNavHelper.goToNotification(activity)
            closeDrawer()
        }
        binding.helpView.setOnClickListener { /* Future Help Action */ }
    }

    fun refreshDrawerContent(): Boolean {
        if (PersonalIdManager.getInstance().isloggedIn()) {
            setSignedInState(true)
            binding.ivNotification.setImageResource(getNotificationIcon(activity))

            val user = ConnectUserDatabaseUtil.getUser(activity)
            binding.userName.text = user.name
            Glide
                .with(binding.imageUserProfile)
                .load(user.photo)
                .apply(
                    RequestOptions
                        .circleCropTransform()
                        .placeholder(R.drawable.nav_drawer_person_avatar)
                        .error(R.drawable.nav_drawer_person_avatar),
                ).into(binding.imageUserProfile)

            val appRecords = MultipleAppsUtil.getUsableAppRecords()

            val seatedApp =
                if (highlightSeatedApp && appRecords.count() > 1) {
                    CommCareApplication.instance().currentApp.uniqueId
                } else {
                    null
                }

            val commcareApps =
                appRecords.map {
                    NavDrawerItem.ChildItem(
                        it.displayName,
                        it.uniqueId,
                        NavItemType.COMMCARE_APPS,
                        it.uniqueId == seatedApp,
                    )
                }

            val hasConnectAccess = ConnectUserDatabaseUtil.hasConnectAccess(activity)

            val items = ArrayList<NavDrawerItem.ParentItem>()
            if (hasConnectAccess) {
                items.add(
                    NavDrawerItem.ParentItem(
                        activity.getString(R.string.nav_drawer_opportunities),
                        R.drawable.connect_logo,
                        NavItemType.OPPORTUNITIES,
                    ),
                )
            }

            items.add(
                NavDrawerItem.ParentItem(
                    activity.getString(R.string.nav_drawer_commcare_apps),
                    R.drawable.commcare_actionbar_logo,
                    NavItemType.COMMCARE_APPS,
                    isEnabled = commcareApps.isNotEmpty(),
                    isExpanded = commcareApps.size < 2,
                    children = commcareApps,
                ),
            )

            val unreadCount =
                if (ConnectMessagingDatabaseHelper.getMessagingChannels(activity).isNotEmpty()) {
                    ConnectMessagingDatabaseHelper.getUnviewedMessages(activity).size
                } else {
                    0
                }
            val messageCount = if (unreadCount > 0) unreadCount else null

            items.add(
                NavDrawerItem.ParentItem(
                    activity.getString(R.string.connect_messaging_title),
                    R.drawable.nav_drawer_message_icon,
                    NavItemType.MESSAGING,
                    badgeCount = messageCount,
                ),
            )

            if (shouldShowWorkHistory()) {
                items.add(
                    NavDrawerItem.ParentItem(
                        activity.getString(R.string.personalid_work_history),
                        R.drawable.ic_work_history,
                        NavItemType.WORK_HISTORY,
                    ),
                )
            }

//            if (hasConnectAccess) {
//                items.add(
//                    NavDrawerItem.ParentItem(
//                        activity.getString(R.string.nav_drawer_payments),
//                        R.drawable.nav_drawer_payments_icon,
//                        NavItemType.PAYMENTS,
//                    )
//                )
//            }

            navDrawerAdapter.refreshList(items)

            return false
        } else {
            setSignedInState(false)

            val globalError = GlobalErrorUtil.checkGlobalErrors()
            val hasError = globalError != null

            binding.errorContainer.visibility = if (hasError) View.VISIBLE else View.GONE
            binding.continueLink.visibility = if (hasError) View.VISIBLE else View.GONE
            val textId = if (hasError) R.string.nav_drawer_fix_now else R.string.nav_drawer_signin_register
            binding.signInButton.text = activity.getString(textId)

            if (hasError) {
                binding.errorText.text = globalError

                binding.continueLink.setOnClickListener {
                    GlobalErrorUtil.dismissGlobalErrors()
                    refreshDrawerContent()
                }
            }

            binding.signInButton.setOnClickListener {
                if (hasError) {
                    GlobalErrorUtil.dismissGlobalErrors()
                }
                PersonalIdManager
                    .getInstance()
                    .launchPersonalId(
                        activity,
                        ConnectConstants.PERSONAL_ID_SIGN_UP_LAUNCH,
                    )
                closeDrawer()
            }

            return hasError
        }
    }

    private fun setSignedInState(isSignedIn: Boolean) {
        binding.signoutView.visibility = if (isSignedIn) View.GONE else View.VISIBLE
        binding.navDrawerRecycler.visibility = if (isSignedIn) View.VISIBLE else View.GONE
        binding.profileCard.visibility = if (isSignedIn) View.VISIBLE else View.GONE
        binding.notificationView.visibility =
            if (shouldShowNotiifcations()) View.VISIBLE else View.GONE
    }

    private fun shouldShowWorkHistory(): Boolean {
        // we are keeping this off for now until we have go ahead to release this feature
        return PersonalIdManager.getInstance().isloggedIn() && isFeatureEnabled(WORK_HISTORY)
    }

    private fun shouldShowNotiifcations(): Boolean = PersonalIdManager.getInstance().isloggedIn() && isFeatureEnabled(NOTIFICATIONS)

    fun closeDrawer() {
        binding.drawerLayout.closeDrawer(GravityCompat.START)
    }

    fun openDrawer() {
        binding.drawerLayout.openDrawer(GravityCompat.START)
    }

    fun handleOptionsItem(item: MenuItem): Boolean = drawerToggle.onOptionsItemSelected(item)
}
