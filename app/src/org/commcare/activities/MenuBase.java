package org.commcare.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;

import org.commcare.CommCareApplication;
import org.commcare.dalvik.R;
import org.commcare.preferences.DeveloperPreferences;
import org.commcare.session.SessionFrame;
import org.commcare.suite.model.Entry;
import org.commcare.suite.model.Menu;
import org.commcare.views.ViewUtil;

public abstract class MenuBase
        extends SyncCapableCommCareActivity
        implements AdapterView.OnItemClickListener {

    private static final int MENU_GROUP_HOME_SCREEN_ACTIONS = android.view.Menu.FIRST;

    private static final int MENU_LOGOUT = android.view.Menu.FIRST + 1;
    private static final int MENU_SYNC = android.view.Menu.FIRST + 2;

    private boolean isRootModuleMenu;
    protected String menuId;

    @Override
    protected void onCreateSessionSafe(Bundle savedInstanceState) {
        super.onCreateSessionSafe(savedInstanceState);

        menuId = getIntent().getStringExtra(SessionFrame.STATE_COMMAND_ID);

        if (menuId == null) {
            menuId = Menu.ROOT_MENU_ID;
            isRootModuleMenu = true;
        }
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
        onBackPressed();
        return true;
    }

    @Override
    public boolean onCreateOptionsMenu(android.view.Menu menu) {
        super.onCreateOptionsMenu(menu);
        addHomeScreenActionsToTopBar(menu);
        return true;
    }

    private void addHomeScreenActionsToTopBar(android.view.Menu menu) {
        if (menuIsBeingUsedAsHomeScreen()) {
            ViewUtil.addItemToActionBar(menu, MENU_LOGOUT, MENU_GROUP_HOME_SCREEN_ACTIONS, "Logout",
                    R.drawable.ic_logout_action_bar);
            ViewUtil.addItemToActionBar(menu, MENU_SYNC, MENU_GROUP_HOME_SCREEN_ACTIONS, "Sync",
                    R.drawable.ic_sync_action_bar);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == MENU_LOGOUT) {
            CommCareApplication.instance().closeUserSession();
            Intent i = new Intent(getIntent());
            setResult(RESULT_CANCELED, i);
            finish();
            return true;
        } else if (item.getItemId() == MENU_SYNC) {
            sendFormsOrSync(true);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean isBackEnabled() {
        return !isRootModuleMenu ||
                (!menuIsBeingUsedAsHomeScreen() && !CommCareApplication.instance().isConsumerApp());
    }

    private boolean menuIsBeingUsedAsHomeScreen() {
        return isRootModuleMenu && DeveloperPreferences.useRootModuleMenuAsHomeScreen();
    }

    @Override
    public void reportSyncResult(String message, boolean success) {
        // empty intentionally
    }

}
