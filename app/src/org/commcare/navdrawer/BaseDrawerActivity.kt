package org.commcare.navdrawer

import android.os.Bundle
import android.text.SpannableString
import android.text.style.UnderlineSpan
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.widget.FrameLayout
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.core.view.GravityCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import org.commcare.AppUtils
import org.commcare.activities.CommCareActivity
import org.commcare.android.database.global.models.ApplicationRecord
import org.commcare.connect.ConnectConstants
import org.commcare.connect.PersonalIdManager
import org.commcare.connect.database.ConnectUserDatabaseUtil
import org.commcare.dalvik.BuildConfig
import org.commcare.dalvik.R
import org.commcare.dalvik.databinding.NavDrawerBaseBinding
import org.commcare.dalvik.databinding.NavDrawerFooterBinding
import org.commcare.dalvik.databinding.NavDrawerHeaderBinding
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil
import org.commcare.utils.MultipleAppsUtil
import org.commcare.views.ViewUtil
import org.commcare.views.dialogs.DialogCreationHelpers

/**
 * Abstract activity that sets up and manages a shared navigation drawer layout.
 * Subclasses must provide the main screen UI via [injectScreenLayout].
 */
abstract class BaseDrawerActivity<T> : CommCareActivity<T>() {

    private lateinit var baseDrawerBinding: NavDrawerBaseBinding
    private lateinit var headerBinding: NavDrawerHeaderBinding
    private lateinit var footerBinding: NavDrawerFooterBinding

    private lateinit var drawerToggle: ActionBarDrawerToggle
    private var hasRefreshed = false
    private lateinit var navDrawerAdapter: NavDrawerAdapter


    /** Enum to represent navigation drawer menu items */
    enum class NavItemType {
        OPPORTUNITIES,
        COMMCARE_APPS,
        WORK_HISTORY,
        MESSAGING,
        PAYMENTS
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        baseDrawerBinding = NavDrawerBaseBinding.inflate(layoutInflater)
        headerBinding = baseDrawerBinding.navDrawerHeader
        footerBinding = baseDrawerBinding.navDrawerFooter
        setContentView(baseDrawerBinding.root)
        initializeViews()
        setupActionBarDrawerToggle()
        initializeAdapter()
        setUpViewListener()
        injectScreenLayout(layoutInflater, baseDrawerBinding.navDrawerFrame)
        setupDrawer()
    }

    private fun initializeAdapter() {
        navDrawerAdapter = NavDrawerAdapter(
            this,
            emptyList(),
            onParentClick = {
                onDrawerItemClicked(it.type, null)
            },
            onChildClick = { parentType, childItem ->
                onDrawerItemClicked(parentType, childItem.recordId)
            }
        )
        baseDrawerBinding.navDrawerRecycler.layoutManager = LinearLayoutManager(this)
        baseDrawerBinding.navDrawerRecycler.adapter = navDrawerAdapter
    }

    /** Subclass must inject the actual screen layout into [contentFrame] */
    abstract fun injectScreenLayout(inflater: LayoutInflater, contentFrame: FrameLayout)

    private fun initializeViews() {
        val content = SpannableString(getString(R.string.nav_drawer_signin_register))
        content.setSpan(UnderlineSpan(), 0, content.length, 0);
        baseDrawerBinding.navDrawerSignInText.text = content
        baseDrawerBinding.navDrawerFooter.appVersion.text = "v ${BuildConfig.VERSION_NAME}"

    }

    private fun setupActionBarDrawerToggle() {
        drawerToggle = object : ActionBarDrawerToggle(
            this,
            baseDrawerBinding.drawerLayout,
            R.string.nav_drawer_open,
            R.string.nav_drawer_close
        ) {
            override fun onDrawerOpened(drawerView: View) {
                super.onDrawerOpened(drawerView)
                // Hide keyboard
                ViewUtil.hideVirtualKeyboard(this@BaseDrawerActivity)
                FirebaseAnalyticsUtil.reportNavDrawerOpen()
            }
            override fun onDrawerSlide(drawerView: View, slideOffset: Float) {
                super.onDrawerSlide(drawerView, slideOffset)

                // Refresh once just as the drawer starts sliding open
                if (slideOffset > 0 && !hasRefreshed) {
                    setupDrawer()
                    hasRefreshed = true
                }

                // Reset flag when fully closed
                if (slideOffset == 0f) {
                    hasRefreshed = false
                }
            }
        }
        baseDrawerBinding.drawerLayout.addDrawerListener(drawerToggle)
        supportActionBar!!.setDisplayHomeAsUpEnabled(true)
        supportActionBar!!.setHomeButtonEnabled(true)
        drawerToggle.syncState()
    }

    private fun setupDrawer() {
        if (PersonalIdManager.getInstance().isloggedIn()) {
            setUpSignInView()
        } else {
            toggleSignedInState(false)
        }
    }

    private fun toggleSignedInState(isSignedIn: Boolean){
        if (isSignedIn) {
            baseDrawerBinding.signoutView.visibility = View.GONE
            baseDrawerBinding.navDrawerRecycler.visibility = View.VISIBLE
            headerBinding.profileCard.visibility = View.VISIBLE
        } else {
            baseDrawerBinding.signoutView.visibility = View.VISIBLE
            baseDrawerBinding.navDrawerRecycler.visibility = View.GONE
            headerBinding.profileCard.visibility = View.GONE
        }
    }

    protected open fun loadVisibleCommcareApplications(): List<ApplicationRecord> {
        return MultipleAppsUtil.getUsableAppRecords()
    }

    private fun setUpSignInView() {
        toggleSignedInState(true)
        val user = ConnectUserDatabaseUtil.getUser(this)
        headerBinding.userName.text = user.name
        val profilePic = user.photo
        Glide.with(headerBinding.imageUserProfile).load(profilePic).apply(
            RequestOptions.circleCropTransform()
                .placeholder(R.drawable.nav_drawer_person_avatar) // Your default placeholder image
                .error(R.drawable.nav_drawer_person_avatar)
        ).into(headerBinding.imageUserProfile)
        val commacreChildItems = loadVisibleCommcareApplications().map {
            NavDrawerItem.ChildItem(it.displayName, it.uniqueId, NavItemType.COMMCARE_APPS)
        }

        val parentList = listOf(
            NavDrawerItem.ParentItem(
                getString(R.string.nav_drawer_opportunities),
                R.drawable.nav_drawer_opportunity_icon,
                NavItemType.OPPORTUNITIES
            ),
            NavDrawerItem.ParentItem(
                getString(R.string.nav_drawer_commcare_apps),
                R.drawable.commcare_actionbar_logo,
                NavItemType.COMMCARE_APPS,
                isEnabled = true,
                isExpanded = commacreChildItems.size<2,
                commacreChildItems
            ),
            NavDrawerItem.ParentItem(
                getString(R.string.nav_drawer_work_history),
                R.drawable.nav_drawer_worker_history_icon,
                NavItemType.WORK_HISTORY,
                isEnabled = false,
            ),
            NavDrawerItem.ParentItem(
                getString(R.string.connect_messaging_title),
                R.drawable.nav_drawer_message_icon,
                NavItemType.MESSAGING,
                isEnabled = false,
            ),
            NavDrawerItem.ParentItem(
                getString(R.string.nav_drawer_payments),
                R.drawable.nav_drawer_payments_icon,
                NavItemType.PAYMENTS,
                isEnabled = false,
            ),
        )

        navDrawerAdapter.refreshList(parentList)

    }

    private fun setUpViewListener() {
        baseDrawerBinding.navDrawerSignInText.setOnClickListener {
            registerPersonalIdUser()
            closeDrawer()
        }
        footerBinding.aboutView.setOnClickListener { showAboutCommCareDialog() }
        headerBinding.closeButton.setOnClickListener { closeDrawer() }
        footerBinding.helpView.setOnClickListener { /* Future Help Action */ }
    }

    private fun registerPersonalIdUser() {
        PersonalIdManager.getInstance()
            .launchPersonalId(this, ConnectConstants.LOGIN_CONNECT_LAUNCH_REQUEST_CODE)
    }

    /**
     * Override to respond to item click.
     * @param parent Top-level item type
     * @param recordId Optional recordId for child click
     */
    protected open fun onDrawerItemClicked(parent: NavItemType, recordId: String?) {
        FirebaseAnalyticsUtil.reportNavDrawerItemSelected(parent.name)
        when (parent) {
            NavItemType.OPPORTUNITIES -> {}
            NavItemType.COMMCARE_APPS -> {}
            NavItemType.WORK_HISTORY -> {}
            NavItemType.MESSAGING -> {}
            NavItemType.PAYMENTS -> {}
        }
    }

    private fun closeDrawer() {
        baseDrawerBinding.drawerLayout.closeDrawer(GravityCompat.START)
    }

    private fun showAboutCommCareDialog() {
        DialogCreationHelpers.showAboutCommCareDialog(this)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return if (drawerToggle.onOptionsItemSelected(item)) true
        else super.onOptionsItemSelected(item)
    }
}
