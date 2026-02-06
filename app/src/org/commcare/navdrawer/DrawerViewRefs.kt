package org.commcare.navdrawer

import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import androidx.cardview.widget.CardView
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.RecyclerView
import org.commcare.dalvik.R

class DrawerViewRefs(rootView: View) {
    val drawerLayout: DrawerLayout = rootView.findViewById(R.id.drawer_layout)
    val navDrawerRecycler: RecyclerView = rootView.findViewById(R.id.nav_drawer_recycler)
    val drawerFrame: FrameLayout = rootView.findViewById(R.id.nav_drawer_frame)
    val signInButton: Button = rootView.findViewById(R.id.nav_drawer_sign_in_button)
    val versionText: TextView = rootView.findViewById(R.id.app_version)
    val signoutView: LinearLayout = rootView.findViewById(R.id.signout_view)
    val profileCard: CardView = rootView.findViewById(R.id.profile_card)
    val imageUserProfile: ImageView = rootView.findViewById(R.id.image_user_profile)
    val userName: TextView = rootView.findViewById(R.id.header_user_name)
    val notificationView: LinearLayout = rootView.findViewById(R.id.notification_view)
    val ivNotification: ImageView = rootView.findViewById(R.id.ivNotification)
    val aboutView: LinearLayout = rootView.findViewById(R.id.about_view)
    val helpView: LinearLayout = rootView.findViewById(R.id.help_view)
    val toolbar: Toolbar = rootView.findViewById(R.id.toolbar)
    val tvPersonalIDTestToggleActive: TextView = rootView.findViewById(R.id.tv_personalid_test_toggle_active)
    val tvConnectTestToggleActive: TextView = rootView.findViewById(R.id.tv_connect_test_toggle_active)
}