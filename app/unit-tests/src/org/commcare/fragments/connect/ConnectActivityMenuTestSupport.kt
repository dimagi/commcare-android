package org.commcare.fragments.connect

import android.view.Window
import androidx.appcompat.view.menu.MenuBuilder
import org.commcare.activities.connect.ConnectActivity
import org.commcare.dalvik.R

fun ConnectActivity.isSyncMenuItemVisible(): Boolean {
    val menu = MenuBuilder(this)
    onCreatePanelMenu(Window.FEATURE_OPTIONS_PANEL, menu)
    onPreparePanel(Window.FEATURE_OPTIONS_PANEL, null, menu)
    return menu.findItem(R.id.action_sync).isVisible
}
