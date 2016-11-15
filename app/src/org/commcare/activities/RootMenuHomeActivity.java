package org.commcare.activities;

import android.os.Build;
import android.os.Bundle;
import android.view.MenuItem;

import org.commcare.activities.components.MenuList;
import org.commcare.preferences.DeveloperPreferences;
import org.commcare.session.SessionFrame;
import org.commcare.suite.model.Menu;

/**
 * A version of the CommCare home screen that uses the UI of the root module menu
 * displayed in grid view, and makes all home screen actions available via a
 * navigation drawer (instead of via the usual home screen buttons and options menu)
 *
 * @author Aliza Stone
 */
public class RootMenuHomeActivity extends HomeScreenBaseActivity<RootMenuHomeActivity> {

    private static final String KEY_DRAWER_WAS_OPEN = "drawer-open-before-rotation";

    private HomeNavDrawerController navDrawerController;
    private boolean reopenDrawerInOnResume;

    @Override
    protected void onCreateSessionSafe(Bundle savedInstanceState) {
        super.onCreateSessionSafe(savedInstanceState);
        String menuId = getIntent().getStringExtra(SessionFrame.STATE_COMMAND_ID);
        if (menuId == null) {
            menuId = Menu.ROOT_MENU_ID;
        }
        MenuList.setupMenusViewInActivity(this, menuId, true, true);
        navDrawerController = new HomeNavDrawerController(this);
        if (usingNavDrawer()) {
            navDrawerController.setupNavDrawer();
            if (savedInstanceState != null && savedInstanceState.getBoolean(KEY_DRAWER_WAS_OPEN)) {
                // Necessary because opening the drawer here does not work for some unknown reason
                reopenDrawerInOnResume = true;
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (reopenDrawerInOnResume) {
            navDrawerController.openDrawer();
            reopenDrawerInOnResume = false;
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (usingNavDrawer() && item.getItemId() == android.R.id.home) {
            if (navDrawerController.isDrawerOpen()) {
                navDrawerController.closeDrawer();
            } else {
                navDrawerController.openDrawer();
            }
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(KEY_DRAWER_WAS_OPEN, navDrawerController.isDrawerOpen());
    }

    private boolean usingNavDrawer() {
        // It's possible that this activity is being used as the home screen without having this flag
        // set explicitly (if this is a consumer app), in which case we don't want to show user actions
        return DeveloperPreferences.useRootModuleMenuAsHomeScreen() &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN;
    }

    @Override
    public boolean shouldShowSyncItemInActionBar() {
        // It's possible that this activity is being used as the home screen without having this flag
        // set explicitly (if this is a consumer app), in which case we don't want to show user actions
        return DeveloperPreferences.useRootModuleMenuAsHomeScreen();
    }

    @Override
    public void refreshUI() {
        rebuildOptionsMenu();
        if (usingNavDrawer()) {
            navDrawerController.refreshItems();
        }
    }

}
