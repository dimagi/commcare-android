package org.commcare.navdrawer

import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.RecyclerView
import org.commcare.dalvik.R

class DrawerViewRefs(rootView: View) {
    val drawerLayout: DrawerLayout = rootView.findViewById(R.id.drawer_layout)
    val navDrawerRecycler: RecyclerView = rootView.findViewById(R.id.nav_drawer_recycler)
    val drawerFrame: FrameLayout = rootView.findViewById(R.id.nav_drawer_frame)
    val signInText: TextView = rootView.findViewById(R.id.nav_drawer_sign_in_text)
    val versionText: TextView = rootView.findViewById(R.id.app_version)
    val signoutView: LinearLayout = rootView.findViewById(R.id.signout_view)
    val profileCard: CardView = rootView.findViewById(R.id.profile_card)
    val imageUserProfile: ImageView = rootView.findViewById(R.id.image_user_profile)
    val userName: TextView = rootView.findViewById(R.id.header_user_name)
    val aboutView: LinearLayout = rootView.findViewById(R.id.about_view)
    val helpView: LinearLayout = rootView.findViewById(R.id.help_view)
}