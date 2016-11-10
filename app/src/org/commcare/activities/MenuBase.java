package org.commcare.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;

import org.commcare.CommCareApplication;
import org.commcare.preferences.DeveloperPreferences;
import org.commcare.session.SessionFrame;
import org.commcare.suite.model.Entry;
import org.commcare.suite.model.Menu;

public abstract class MenuBase
        extends SyncCapableCommCareActivity
        implements AdapterView.OnItemClickListener {

    private boolean isRootModuleMenu;
    protected String menuId;
    private NavDrawerController navDrawerController;

    @Override
    protected void onCreateSessionSafe(Bundle savedInstanceState) {
        super.onCreateSessionSafe(savedInstanceState);
        menuId = getIntent().getStringExtra(SessionFrame.STATE_COMMAND_ID);
        if (menuId == null) {
            menuId = Menu.ROOT_MENU_ID;
            isRootModuleMenu = true;
        }
        navDrawerController = new NavDrawerController(this);
        navDrawerController.setupNavDrawer();
    }

    /**
     * Stores the path of selected form and finishes.
     */
    @Override
    public void onItemClick(AdapterView listView, View view, int position, long id) {
        String commandId;
        Object value = listView.getAdapter().getItem(position);

        // if value is null, it probably means that we clicked on the header view, so just ignore it
        if (value == null) {
            return;
        }

        if (value instanceof Entry) {
            commandId = ((Entry)value).getCommandId();
        } else {
            commandId = ((Menu)value).getId();
        }

        // create intent for return and store path
        Intent i = new Intent(getIntent());
        i.putExtra(SessionFrame.STATE_COMMAND_ID, commandId);
        setResult(RESULT_OK, i);

        finish();
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
        if (menuIsBeingUsedAsHomeScreen()) {
            return true;
        }
        onBackPressed();
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home && menuIsBeingUsedAsHomeScreen()) {
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
    public boolean isBackEnabled() {
        return !isRootModuleMenu ||
                (!menuIsBeingUsedAsHomeScreen() && !CommCareApplication.instance().isConsumerApp());
    }

    protected boolean menuIsBeingUsedAsHomeScreen() {
        return isRootModuleMenu && DeveloperPreferences.useRootModuleMenuAsHomeScreen();
    }

    @Override
    public boolean shouldShowSyncItemInActionBar() {
        return menuIsBeingUsedAsHomeScreen() || DeveloperPreferences.syncFromAllContextsEnabled();
    }

}
