package org.commcare.activities;

import android.os.Bundle;
import android.view.MenuItem;

import org.commcare.activities.components.MenuList;
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

    @Override
    protected void onCreateSessionSafe(Bundle savedInstanceState) {
        super.onCreateSessionSafe(savedInstanceState);
        String menuId = getIntent().getStringExtra(SessionFrame.STATE_COMMAND_ID);
        if (menuId == null) {
            menuId = Menu.ROOT_MENU_ID;
        }
        MenuList.setupMenusViewInActivity(this, menuId, true, true);
        navDrawerController = new HomeNavDrawerController(this);
        navDrawerController.setupNavDrawer();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
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
    public boolean shouldShowSyncItemInActionBar() {
        return true;
    }

    @Override
    public void refreshUI() {
        rebuildOptionsMenu();
        navDrawerController.refreshItems();
    }

    @Override
    public boolean isBackEnabled() {
        return false;
    }
}
