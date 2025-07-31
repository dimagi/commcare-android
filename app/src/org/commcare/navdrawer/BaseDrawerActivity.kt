package org.commcare.navdrawer

import android.graphics.BitmapFactory
import android.os.Bundle
import android.text.SpannableString
import android.text.style.UnderlineSpan
import android.util.Base64
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.commcare.activities.CommCareActivity
import org.commcare.android.database.global.models.ApplicationRecord
import org.commcare.connect.ConnectConstants
import org.commcare.connect.PersonalIdManager
import org.commcare.connect.database.ConnectUserDatabaseUtil
import org.commcare.dalvik.R
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil
import org.commcare.utils.MultipleAppsUtil
import org.commcare.views.dialogs.DialogCreationHelpers

/**
 * Abstract activity that sets up and manages a shared navigation drawer layout.
 * Subclasses must provide the main screen UI via [injectScreenLayout].
 */
abstract class BaseDrawerActivity<T> : CommCareActivity<T>() {

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navDrawerRecycler: RecyclerView
    private lateinit var signOutView: View
    private lateinit var signInView: View
    private lateinit var navDrawerHeader: View
    private lateinit var registerTextView: TextView
    private lateinit var userName: TextView
    private lateinit var userPhoto: ImageView

    private lateinit var drawerToggle: ActionBarDrawerToggle
    private lateinit var closeIcon: ImageView
    private lateinit var aboutCommcare: LinearLayout
    private lateinit var helpButton: LinearLayout
    private var hasRefreshed = false


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
        setContentView(R.layout.nav_drawer_base)

        initializeViews()
        setupActionBarDrawerToggle()

        injectScreenLayout(layoutInflater, findViewById(R.id.nav_drawer_frame))
        setupDrawer()
    }

    /** Subclass must inject the actual screen layout into [contentFrame] */
    abstract fun injectScreenLayout(inflater: LayoutInflater, contentFrame: FrameLayout)

    private fun initializeViews() {
        drawerLayout = findViewById(R.id.drawer_layout)
        navDrawerRecycler = findViewById(R.id.nav_drawer_recycler)
        signOutView = findViewById(R.id.signout_view)
        navDrawerHeader = findViewById(R.id.profile_card)
        registerTextView = findViewById(R.id.nav_drawer_sign_in_text)
        userName = findViewById(R.id.user_name)
        userPhoto = findViewById(R.id.image_user_profile)
        closeIcon = findViewById(R.id.close_button)
        aboutCommcare = findViewById(R.id.about_view)
        helpButton = findViewById(R.id.help_view)
        signInView = navDrawerRecycler
        helpButton.visibility = View.GONE
        val content = SpannableString(getString(R.string.nav_drawer_signin_register))
        content.setSpan(UnderlineSpan(), 0, content.length, 0);
        registerTextView.text = content
    }

    private fun setupActionBarDrawerToggle() {
        drawerToggle = object : ActionBarDrawerToggle(
            this,
            drawerLayout,
            R.string.nav_drawer_open,
            R.string.nav_drawer_close
        ) {
            override fun onDrawerOpened(drawerView: View) {
                super.onDrawerOpened(drawerView)
                // Hide keyboard
                val inputMethodManager = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                currentFocus?.let {
                    inputMethodManager.hideSoftInputFromWindow(it.windowToken, 0)
                    it.clearFocus()
                }
                setupDrawer()
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
        drawerLayout.addDrawerListener(drawerToggle)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setHomeButtonEnabled(true)
        drawerToggle.syncState()
    }

    private fun setupDrawer() {
        setUpListener()
        if (PersonalIdManager.getInstance().isloggedIn()) {
            signOutView.visibility = View.GONE
            signInView.visibility = View.VISIBLE
            navDrawerHeader.visibility = View.VISIBLE
            setUpSignInView()
        } else {
            signOutView.visibility = View.VISIBLE
            signInView.visibility = View.GONE
            navDrawerHeader.visibility = View.GONE
        }
    }

    /** Override this to control which CommCare applications appear */
    private fun loadVisibleCommcareApplications(): List<ApplicationRecord> {
        return MultipleAppsUtil.getUsableAppRecords()
    }

    private fun setUpSignInView() {
        val user = ConnectUserDatabaseUtil.getUser(this)
        userName.text = user.name

        val profilePic = user.photo
        if (!profilePic.isNullOrEmpty()) {
            try {
                val base64Part = profilePic.substringAfter("base64,", profilePic)
                val decodedBytes = Base64.decode(base64Part, Base64.DEFAULT)
                val bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
                if (bitmap != null) {
                    userPhoto.setImageBitmap(bitmap)
                } else {
                    userPhoto.setImageResource(R.drawable.nav_drawer_person_avatar)
                }
            } catch (e: IllegalArgumentException) {
                userPhoto.setImageResource(R.drawable.nav_drawer_person_avatar)
            }
        } else {
            userPhoto.setImageResource(R.drawable.nav_drawer_person_avatar)
        }

        val childItems = loadVisibleCommcareApplications().map {
            NavDrawerItem.ChildItem(it.displayName, it.uniqueId)
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
                isExpanded = false,
                childItems
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

        navDrawerRecycler.layoutManager = LinearLayoutManager(this)
        navDrawerRecycler.adapter = NavDrawerAdapter(
            this,
            parentList,
            onParentClick = {
                onDrawerItemClicked(it.type, null)
            },
            onChildClick = {
                onDrawerItemClicked(NavItemType.COMMCARE_APPS, it.recordId)
                drawerLayout.closeDrawers()
            }
        )
    }

    private fun setUpListener() {
        registerTextView.setOnClickListener {
            registerPersonalIdUser()
            closeDrawer()
        }
        aboutCommcare.setOnClickListener { showAboutCommCareDialog() }
        closeIcon.setOnClickListener { closeDrawer() }
        helpButton.setOnClickListener { /* Future Help Action */ }
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

    fun toggleDrawer() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            drawerLayout.openDrawer(GravityCompat.START)
        }
    }

    private fun closeDrawer() {
        drawerLayout.closeDrawer(GravityCompat.START)
    }

    private fun showAboutCommCareDialog() {
        val dialog = DialogCreationHelpers.buildAboutCommCareDialog(this)
        dialog.makeCancelable()
        showAlertDialog(dialog)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return if (drawerToggle.onOptionsItemSelected(item)) true
        else super.onOptionsItemSelected(item)
    }
}
