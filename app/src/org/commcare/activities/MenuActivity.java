package org.commcare.activities;

import android.os.Bundle;

import org.commcare.activities.components.MenuBase;
import org.commcare.preferences.DeveloperPreferences;
import org.commcare.session.SessionFrame;
import org.commcare.suite.model.Menu;

/**
 * Created by amstone326 on 11/14/16.
 */

public class MenuActivity extends SyncCapableCommCareActivity<MenuActivity> {

    public static final String KEY_USE_GRID_MENU = "should-use-grid-menu";

    @Override
    protected void onCreateSessionSafe(Bundle savedInstanceState) {
        super.onCreateSessionSafe(savedInstanceState);
        String menuId = getIntent().getStringExtra(SessionFrame.STATE_COMMAND_ID);
        if (menuId == null) {
            menuId = Menu.ROOT_MENU_ID;
        }
        if (menuId == Menu.ROOT_MENU_ID && DeveloperPreferences.useRootModuleMenuAsHomeScreen()) {
            // Pressing back from any screen immediately after the RootMenuHomeActivity will take
            // us here, so we want to redirect
            finish();
            return;
        }
        boolean useGridMenu = getIntent().getBooleanExtra(KEY_USE_GRID_MENU, false);
        MenuBase.setupMenuInActivity(this, menuId, useGridMenu, false);
    }

    @Override
    public boolean shouldShowSyncItemInActionBar() {
        return DeveloperPreferences.syncFromAllContextsEnabled();
    }

    @Override
    public String getActivityTitle() {
        //return adapter.getMenuTitle();
        return null;
    }

    @Override
    protected boolean isTopNavEnabled() {
        return true;
    }

    @Override
    protected boolean onBackwardSwipe() {
        onBackPressed();
        return true;
    }

}
