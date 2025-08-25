package org.commcare.navdrawer

import androidx.preference.PreferenceManager
import org.commcare.CommCareApplication
import androidx.core.content.edit

object NavDrawerHelper {
    private const val SIDE_DRAWER_SHOWN: String = "side-drawer-shown"

    fun drawerShownBefore(): Boolean {
        val prefs = PreferenceManager.getDefaultSharedPreferences(CommCareApplication.instance())
        return prefs.getBoolean(SIDE_DRAWER_SHOWN, false)
    }

    fun setDrawerShown() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(CommCareApplication.instance())
        if (!prefs.getBoolean(SIDE_DRAWER_SHOWN, false)) {
            prefs.edit {
                putBoolean(SIDE_DRAWER_SHOWN, true)
            }
        }
    }
}