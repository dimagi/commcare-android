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

    private HomeNavDrawerController navDrawerController;
    private MenuList menuView;

    @Override
    public void onCreateSessionSafe(Bundle savedInstanceState) {
        super.onCreateSessionSafe(savedInstanceState);
        String menuId = getIntent().getStringExtra(SessionFrame.STATE_COMMAND_ID);
        if (menuId == null) {
            menuId = Menu.ROOT_MENU_ID;
        }
        menuView = MenuList.setupMenuViewInActivity(this, menuId, true, true);
        navDrawerController = new HomeNavDrawerController(this);
        if (usingNavDrawer()) {
            navDrawerController.setupNavDrawer(savedInstanceState);
        }
    }

    @Override
    public void onResumeSessionSafe() {
        super.onResumeSessionSafe();
        navDrawerController.reopenDrawerIfNeeded();
        menuView.refreshItems(); // Otherwise adapter will show obsolete data
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (usingNavDrawer() && item.getItemId() == android.R.id.home) {
            navDrawerController.toggleDrawer();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(HomeNavDrawerController.KEY_DRAWER_WAS_OPEN,
                navDrawerController.isDrawerOpen());
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
    protected void updateUiAfterDataPullOrSend(String message, boolean success) {
        displayToast(message);
        if (usingNavDrawer()) {
            navDrawerController.refreshItems();
        }
        menuView.refreshItems();
    }

    @Override
    public void refreshUI() {
        // empty intentionally
    }

    @Override
    public boolean usesSubmissionProgressBar() {
        return true;
    }
}
