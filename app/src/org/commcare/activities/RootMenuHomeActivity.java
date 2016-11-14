package org.commcare.activities;

import android.os.Bundle;
import android.view.MenuItem;

import org.commcare.activities.components.MenuBase;
import org.commcare.session.SessionFrame;
import org.commcare.suite.model.Menu;

/**
 * Created by amstone326 on 11/14/16.
 */

public class RootMenuHomeActivity extends HomeScreenCapableActivity<RootMenuHomeActivity> {

    private HomeNavDrawerController navDrawerController;

    @Override
    protected void onCreateSessionSafe(Bundle savedInstanceState) {
        super.onCreateSessionSafe(savedInstanceState);
        String menuId = getIntent().getStringExtra(SessionFrame.STATE_COMMAND_ID);
        if (menuId == null) {
            menuId = Menu.ROOT_MENU_ID;
        }
        MenuBase.setupMenuInActivity(this, menuId, true, true);
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
        //TODO: implement this
    }

    @Override
    public boolean isBackEnabled() {
        return false;
    }
}
