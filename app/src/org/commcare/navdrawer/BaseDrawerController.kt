package org.commcare.navdrawer

import android.app.Activity.RESULT_OK
import android.content.Intent
import android.graphics.Color
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import org.commcare.CommCareApplication
import org.commcare.activities.CommCareActivity
import org.commcare.android.database.connect.models.ConnectUserRecord
import org.commcare.connect.ConnectConstants
import org.commcare.connect.ConnectNavHelper
import org.commcare.connect.PersonalIdManager
import org.commcare.connect.database.ConnectMessagingDatabaseHelper
import org.commcare.connect.database.ConnectUserDatabaseUtil
import org.commcare.connect.network.PersonalIdOrConnectApiErrorHandler
import org.commcare.connect.network.connectId.PersonalIdApiHandler
import org.commcare.dalvik.BuildConfig
import org.commcare.dalvik.R
import org.commcare.fragments.MicroImageActivity
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil
import org.commcare.personalId.PersonalIdFeatureFlagChecker.Companion.isFeatureEnabled
import org.commcare.personalId.PersonalIdFeatureFlagChecker.FeatureFlag.Companion.NOTIFICATIONS
import org.commcare.personalId.PersonalIdFeatureFlagChecker.FeatureFlag.Companion.WORK_HISTORY
import org.commcare.utils.GlobalErrorUtil
import org.commcare.utils.KeyboardHelper.hideVirtualKeyboard
import org.commcare.utils.MultipleAppsUtil
import org.commcare.utils.NotificationUtil.getNotificationIcon
import org.commcare.views.dialogs.DialogCreationHelpers

class BaseDrawerController(
    private val activity: CommCareActivity<*>,
    private val binding: DrawerViewRefs,
    private val highlightSeatedApp: Boolean,
    private val onItemClicked: (NavItemType, String?) -> Unit,
) {
    private lateinit var drawerToggle: ActionBarDrawerToggle
    private lateinit var navDrawerAdapter: NavDrawerAdapter
    private lateinit var takePhotoLauncher: ActivityResultLauncher<Intent>
    private var hasRefreshed = false
    private var showingError = false
    private var user: ConnectUserRecord? = null
    private var previousUserPhotoBase64: String? = null

    /** Enum to represent navigation drawer menu items */
    enum class NavItemType {
        OPPORTUNITIES,
        COMMCARE_APPS,
        WORK_HISTORY,
        MESSAGING,
        PAYMENTS,
    }

    fun setupDrawer() {
        if (PersonalIdManager.getInstance().isloggedIn()) {
            user = ConnectUserDatabaseUtil.getUser(activity)
        }

        setupActionBarDrawerToggle()
        initializeAdapter()
        initTakePhotoLauncher()
        setupListeners()
        setupViews()
        refreshDrawerContent()

        if (showingError) {
            openDrawer()
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
                    hideVirtualKeyboard(activity)
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
        val loginHandler = {
            if (showingError) {
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

        binding.signInButton.setOnClickListener { loginHandler() }
        binding.reconfigureButton.setOnClickListener { loginHandler() }

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
        binding.imageUserProfile.setOnClickListener {
            launchCameraForPhotoEdit()
        }
    }

    fun refreshDrawerContent() {
        if (PersonalIdManager.getInstance().isloggedIn()) {
            setSignedInState(true)
            binding.ivNotification.setImageResource(getNotificationIcon(activity))
            binding.userName.text = user!!.name
            loadUserPhoto(user!!.photo!!)

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

            navDrawerAdapter.refreshList(items)
        } else {
            setSignedInState(false)
            configureErrorState()
        }
    }

    private fun setSignedInState(isSignedIn: Boolean) {
        binding.signoutView.visibility = if (isSignedIn) View.GONE else View.VISIBLE
        binding.navDrawerRecycler.visibility = if (isSignedIn) View.VISIBLE else View.GONE
        binding.profileCard.visibility = if (isSignedIn) View.VISIBLE else View.GONE
        binding.notificationView.visibility =
            if (shouldShowNotifications()) View.VISIBLE else View.GONE
    }

    private fun configureErrorState() {
        val globalError = GlobalErrorUtil.checkGlobalErrors()
        showingError = globalError != null

        if (showingError) {
            binding.signedOutText.visibility = View.GONE
            binding.signInButton.visibility = View.GONE
            binding.errorContainer.visibility = View.VISIBLE
            binding.errorIcon.visibility = View.VISIBLE

            binding.errorTitle.setText(globalError.titleId)
            binding.errorMessage.setText(globalError.messageId)

            binding.dismissLink.setOnClickListener {
                GlobalErrorUtil.dismissGlobalErrors()
                refreshDrawerContent()
            }
        } else {
            binding.signedOutText.visibility = View.VISIBLE
            binding.signInButton.visibility = View.VISIBLE
            binding.errorContainer.visibility = View.GONE
            binding.errorIcon.visibility = View.GONE
        }
    }

    private fun shouldShowWorkHistory(): Boolean {
        // we are keeping this off for now until we have go ahead to release this feature
        return PersonalIdManager.getInstance().isloggedIn() && isFeatureEnabled(WORK_HISTORY)
    }

    private fun shouldShowNotifications(): Boolean = PersonalIdManager.getInstance().isloggedIn() && isFeatureEnabled(NOTIFICATIONS)

    fun closeDrawer() {
        binding.drawerLayout.closeDrawer(GravityCompat.START)
    }

    fun openDrawer() {
        binding.drawerLayout.openDrawer(GravityCompat.START)
    }

    fun isShowingError(): Boolean = showingError

    fun handleOptionsItem(item: MenuItem): Boolean = drawerToggle.onOptionsItemSelected(item)

    private fun initTakePhotoLauncher() {
        takePhotoLauncher =
            activity.registerForActivityResult(
                ActivityResultContracts.StartActivityForResult(),
            ) { result ->
                if (result.resultCode == RESULT_OK) {
                    result.data
                        ?.getStringExtra(MicroImageActivity.MICRO_IMAGE_BASE_64_RESULT_KEY)
                        ?.let { photoBase64 ->
                            previousUserPhotoBase64 = user!!.photo
                            loadUserPhoto(photoBase64)
                            uploadUserPhoto(photoBase64)
                        }
                }
            }
    }

    private fun launchCameraForPhotoEdit() {
        val intent = Intent(activity, MicroImageActivity::class.java).apply {
            putExtra(MicroImageActivity.MICRO_IMAGE_MAX_DIMENSION_PX_EXTRA, USER_PHOTO_MAX_DIMENSION_PX)
            putExtra(MicroImageActivity.MICRO_IMAGE_MAX_SIZE_BYTES_EXTRA, USER_PHOTO_MAX_SIZE_BYTES)
        }
        takePhotoLauncher.launch(intent)
    }

    private fun uploadUserPhoto(photoBase64: String) {
        object : PersonalIdApiHandler<Boolean>() {
            override fun onSuccess(success: Boolean) {
                user!!.photo = photoBase64
                ConnectUserDatabaseUtil.storeUser(activity, user)
            }

            override fun onFailure(
                errorCode: PersonalIdOrConnectApiErrorCodes,
                t: Throwable?,
            ) {
                revertUserPhoto()
                val errorMessage = PersonalIdOrConnectApiErrorHandler.handle(activity, errorCode, t)
                Toast.makeText(activity, errorMessage, Toast.LENGTH_LONG).show()
            }
            // For now, we only allow users to update their photo, hence the null values for "displayName" and "secondaryPhone".
        }.updateProfile(activity, user!!.userId, user!!.password, null, null, photoBase64)
    }

    private fun loadUserPhoto(photoBase64: String) {
        Glide
            .with(binding.imageUserProfile)
            .load(photoBase64)
            .apply(
                RequestOptions
                    .circleCropTransform()
                    .placeholder(R.drawable.nav_drawer_person_avatar)
                    .error(R.drawable.nav_drawer_person_avatar),
            ).into(binding.imageUserProfile)
    }

    private fun revertUserPhoto() {
        if (!previousUserPhotoBase64.isNullOrEmpty()) {
            loadUserPhoto(previousUserPhotoBase64!!)
        } else {
            binding.imageUserProfile.setImageResource(R.drawable.nav_drawer_person_avatar)
        }
    }

    companion object {
        private const val USER_PHOTO_MAX_DIMENSION_PX = 160
        private const val USER_PHOTO_MAX_SIZE_BYTES = 100 * 1024 // 100 KB
    }
}
