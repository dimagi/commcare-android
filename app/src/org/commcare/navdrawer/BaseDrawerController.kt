package org.commcare.navdrawer
import android.text.SpannableString
import android.text.style.UnderlineSpan
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
import org.commcare.connect.PersonalIdManager
import org.commcare.connect.database.ConnectMessagingDatabaseHelper
import org.commcare.connect.database.ConnectUserDatabaseUtil
import org.commcare.dalvik.BuildConfig
import org.commcare.dalvik.R
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil
import org.commcare.utils.MultipleAppsUtil
import org.commcare.views.ViewUtil
import org.commcare.views.dialogs.DialogCreationHelpers

class BaseDrawerController(
    private val activity: CommCareActivity<*>,
    private val binding: DrawerViewRefs,
    private val highlightSeatedApp: Boolean,
    private val onItemClicked: (NavItemType, String?) -> Unit
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
        PAYMENTS
    }

    fun setupDrawer() {
        setupActionBarDrawerToggle()
        initializeAdapter()
        setupListeners()
        setupViews()
        refreshDrawerContent()
    }

    private fun setupActionBarDrawerToggle() {
        drawerToggle = object : ActionBarDrawerToggle(
            activity,
            binding.drawerLayout,
            R.string.nav_drawer_open,
            R.string.nav_drawer_close
        ) {
            override fun onDrawerOpened(drawerView: View) {
                super.onDrawerOpened(drawerView)
                ViewUtil.hideVirtualKeyboard(activity)
                FirebaseAnalyticsUtil.reportNavDrawerOpen()
            }

            override fun onDrawerSlide(drawerView: View, slideOffset: Float) {
                super.onDrawerSlide(drawerView, slideOffset)
                (activity as? AppCompatActivity)?.supportActionBar?.setHomeAsUpIndicator(R.drawable.ic_connect_close)
                if (slideOffset > 0 && !hasRefreshed) {
                    refreshDrawerContent()
                    hasRefreshed = true
                }
                if (slideOffset == 0f) hasRefreshed = false
            }

            override fun onDrawerClosed(drawerView: View) {
                (activity as? AppCompatActivity)?.supportActionBar?.setHomeAsUpIndicator(R.drawable.ic_menu_bar)
            }
        }
        binding.drawerLayout.addDrawerListener(drawerToggle)
        (activity as? AppCompatActivity)?.apply {
            supportActionBar?.setDisplayHomeAsUpEnabled(true)
            supportActionBar?.setHomeButtonEnabled(true)
            drawerToggle.syncState()
        }
    }

    private fun setupViews() {
        val content = SpannableString(activity.getString(R.string.nav_drawer_signin_register))
        content.setSpan(UnderlineSpan(), 0, content.length, 0)
        binding.signInText.text = content
        binding.versionText.text = "v ${BuildConfig.VERSION_NAME}"
    }

    private fun initializeAdapter() {
        navDrawerAdapter = NavDrawerAdapter(
            activity,
            emptyList(),
            onParentClick = {
                onItemClicked(it.type, null)
            },
            onChildClick = { parentType, childItem ->
                onItemClicked(parentType, childItem.recordId)
            }
        )
        binding.navDrawerRecycler.layoutManager = LinearLayoutManager(activity)
        binding.navDrawerRecycler.adapter = navDrawerAdapter
    }

    private fun setupListeners() {
        binding.signInText.setOnClickListener {
            PersonalIdManager.getInstance()
                .launchPersonalId(activity, ConnectConstants.LOGIN_CONNECT_LAUNCH_REQUEST_CODE)
            closeDrawer()
        }
        binding.aboutView.setOnClickListener { DialogCreationHelpers.showAboutCommCareDialog(activity) }
        binding.helpView.setOnClickListener { /* Future Help Action */ }
    }

    fun refreshDrawerContent() {
        if (PersonalIdManager.getInstance().isloggedIn()) {
            setSignedInState(true)
            val user = ConnectUserDatabaseUtil.getUser(activity)
            binding.userName.text = user.name
            Glide.with(binding.imageUserProfile)
                .load(user.photo)
                .apply(
                    RequestOptions.circleCropTransform()
                        .placeholder(R.drawable.nav_drawer_person_avatar)
                        .error(R.drawable.nav_drawer_person_avatar)
                ).into(binding.imageUserProfile)

            val seatedApp = if(highlightSeatedApp)
                CommCareApplication.instance().currentApp.uniqueId else null

            val commcareApps = MultipleAppsUtil.getUsableAppRecords().map {
                NavDrawerItem.ChildItem(it.displayName, it.uniqueId, NavItemType.COMMCARE_APPS,
                    it.uniqueId == seatedApp)
            }

            val hasConnectAccess = ConnectUserDatabaseUtil.hasConnectAccess(activity)

            val items = ArrayList<NavDrawerItem.ParentItem>()
            if(hasConnectAccess) {
                items.add(NavDrawerItem.ParentItem(
                    activity.getString(R.string.nav_drawer_opportunities),
                    R.drawable.nav_drawer_opportunity_icon,
                    NavItemType.OPPORTUNITIES,
                ))
            }

            items.add(NavDrawerItem.ParentItem(
                activity.getString(R.string.nav_drawer_commcare_apps),
                R.drawable.commcare_actionbar_logo,
                NavItemType.COMMCARE_APPS,
                isEnabled = commcareApps.isNotEmpty(),
                isExpanded = commcareApps.size < 2,
                children = commcareApps
            ))

//            items.add(NavDrawerItem.ParentItem(
//                activity.getString(R.string.nav_drawer_work_history),
//                R.drawable.nav_drawer_worker_history_icon,
//                NavItemType.WORK_HISTORY,
//            ))

            val channels = ConnectMessagingDatabaseHelper.getMessagingChannels(activity)
            if(channels.isNotEmpty()) {
                items.add(NavDrawerItem.ParentItem(
                    activity.getString(R.string.connect_messaging_title),
                    R.drawable.nav_drawer_message_icon,
                    NavItemType.MESSAGING,
                ))
            }

//            items.add(NavDrawerItem.ParentItem(
//                activity.getString(R.string.nav_drawer_payments),
//                R.drawable.nav_drawer_payments_icon,
//                NavItemType.PAYMENTS,
//            ))

            navDrawerAdapter.refreshList(items)
        } else {
            setSignedInState(false)
        }
    }

    private fun setSignedInState(isSignedIn: Boolean) {
        binding.signoutView.visibility = if (isSignedIn) View.GONE else View.VISIBLE
        binding.navDrawerRecycler.visibility = if (isSignedIn) View.VISIBLE else View.GONE
        binding.profileCard.visibility = if (isSignedIn) View.VISIBLE else View.GONE
    }

    fun closeDrawer() {
        binding.drawerLayout.closeDrawer(GravityCompat.START)
    }

    fun handleOptionsItem(item: MenuItem): Boolean {
        return drawerToggle.onOptionsItemSelected(item)
    }

}
