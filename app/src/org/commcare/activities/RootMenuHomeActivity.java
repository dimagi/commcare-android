package org.commcare.activities;

import android.os.Bundle;
import android.view.MenuItem;

/**
 * Created by amstone326 on 11/14/16.
 */

public class RootMenuHomeActivity extends MenuGrid {

    private HomeNavDrawerController navDrawerController;

    @Override
    protected void onCreateSessionSafe(Bundle savedInstanceState) {
        super.onCreateSessionSafe(savedInstanceState);
        isRootModuleMenu = true;
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
    public void refreshUI() {
        refreshView();
    }
}
