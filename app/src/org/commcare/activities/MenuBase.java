package org.commcare.activities;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.widget.DrawerLayout;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;

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

    protected DrawerLayout drawerLayout;
    private DrawerLayout.DrawerListener drawerListener;
    protected ListView navDrawerList;

    // NOTE: Menu.FIRST is reserved for MENU_SYNC in SyncCapableCommCareActivity
    private static final int MENU_LOGOUT = android.view.Menu.FIRST + 1;

    private static final String[] navDrawerItems = {"Sync", "Logout"};

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
        setupNavDrawer();
    }

    private void setupNavDrawer() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2 && menuIsBeingUsedAsHomeScreen()) {
            navDrawerList = (ListView)findViewById(R.id.nav_drawer);
            navDrawerList.setAdapter(
                    new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, navDrawerItems));
            navDrawerList.setOnItemClickListener(getNavDrawerClickListener());

            drawerLayout = (DrawerLayout)findViewById(R.id.menu_activity_drawer_layout);
            initDrawerListener();
            drawerLayout.addDrawerListener(drawerListener);
            getActionBar().setHomeButtonEnabled(true);
        }
    }

    private void initDrawerListener() {
        drawerListener = new DrawerLayout.DrawerListener() {
            @Override
            public void onDrawerSlide(View drawerView, float slideOffset) {

            }

            @Override
            public void onDrawerOpened(View drawerView) {

            }

            @Override
            public void onDrawerClosed(View drawerView) {

            }

            @Override
            public void onDrawerStateChanged(int newState) {

            }
        };
    }

    private ListView.OnItemClickListener getNavDrawerClickListener() {
        return new ListView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {

            }
        };
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
    public boolean onCreateOptionsMenu(android.view.Menu menu) {
        super.onCreateOptionsMenu(menu);
        // TODO: TEMPORARY, MOVE THIS TO THE DRAWER MENU LATER
        if (menuIsBeingUsedAsHomeScreen()) {
            ViewUtil.addItemToActionBar(menu, MENU_LOGOUT, MENU_LOGOUT, "Logout",
                    R.drawable.ic_logout_action_bar);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home && menuIsBeingUsedAsHomeScreen()) {
            if (drawerLayout.isDrawerOpen(navDrawerList)) {
                drawerLayout.closeDrawer(navDrawerList);
            } else {
                drawerLayout.openDrawer(navDrawerList);
            }
            return true;
        } else if (item.getItemId() == MENU_LOGOUT) {
            CommCareApplication._().closeUserSession();
            Intent i = new Intent(getIntent());
            setResult(RESULT_CANCELED, i);
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean isBackEnabled() {
        return !isRootModuleMenu ||
                (!menuIsBeingUsedAsHomeScreen() && !CommCareApplication._().isConsumerApp());
    }

    private boolean menuIsBeingUsedAsHomeScreen() {
        return isRootModuleMenu && DeveloperPreferences.useRootModuleMenuAsHomeScreen();
    }

    @Override
    public boolean shouldShowSyncItemInActionBar() {
        return menuIsBeingUsedAsHomeScreen() || DeveloperPreferences.syncFromAllContextsEnabled();
    }

}
