package org.commcare.activities;


import android.app.ActionBar;
import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.support.v4.widget.DrawerLayout;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;

import org.commcare.CommCareApplication;
import org.commcare.activities.components.NavDrawerItem;
import org.commcare.adapters.NavDrawerAdapter;
import org.commcare.dalvik.R;
import org.commcare.preferences.CommCarePreferences;
import org.commcare.utils.ChangeLocaleUtil;
import org.javarosa.core.services.locale.Localization;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by amstone326 on 11/10/16.
 */

public class HomeNavDrawerController {

    private static final String ABOUT_CC_DRAWER_ITEM_ID = "about-cc";
    private static final String UPDATE_DRAWER_ITEM_ID = "update";
    private static final String CHANGE_LANGUAGE_DRAWER_ITEM_ID = "change-language";
    private static final String SETTINGS_DRAWER_ITEM_ID = "settings";
    private static final String ADVANCED_DRAWER_ITEM_ID = "advanced";
    private static final String SYNC_DRAWER_ITEM_ID = "sync";
    private static final String SAVED_FORMS_ITEM_ID = "saved-forms";
    private static final String LOGOUT_DRAWER_ITEM_ID = "home-logout";

    private MenuBase activity;

    private DrawerLayout drawerLayout;
    private ListView navDrawerList;
    private Map<String, NavDrawerItem> allDrawerItems;
    private NavDrawerItem[] drawerItemsShowing;

    public HomeNavDrawerController(MenuBase activity) {
        this.activity = activity;
    }

    protected void setupNavDrawer() {
        initDrawerItemsMap();
        initDrawerItemsToInclude();
        drawerLayout = (DrawerLayout)activity.findViewById(R.id.menu_activity_drawer_layout);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2
                && activity.menuIsBeingUsedAsHomeScreen()) {
            navDrawerList = (ListView)activity.findViewById(R.id.nav_drawer);
            navDrawerList.setAdapter(new NavDrawerAdapter(activity, drawerItemsShowing));
            navDrawerList.setOnItemClickListener(getNavDrawerClickListener());
            drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED);

            ActionBar actionBar = activity.getActionBar();
            actionBar.setHomeButtonEnabled(true);
            actionBar.setDisplayHomeAsUpEnabled(false);
            actionBar.setDisplayUseLogoEnabled(false);
            actionBar.setIcon(R.drawable.ic_menu_bar);
        } else {
            // Disable opening of the nav drawer if this menu is not being used as the home screen
            drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED);
        }
    }

    private void initDrawerItemsMap() {
        allDrawerItems = new HashMap<>();
        String[] ids = getAllItemIds();
        String[] titles = getAllItemTitles();
        int[] icons = getAllItemIcons();
        for (int i = 0; i < ids.length; i++) {
            NavDrawerItem item = new NavDrawerItem(ids[i], titles[i], icons[i], null);
            allDrawerItems.put(ids[i], item);
        }
    }

    private void initDrawerItemsToInclude() {
        Map<String, NavDrawerItem> itemsToShowMap = new HashMap<>();
        itemsToShowMap.putAll(allDrawerItems);
        if (ChangeLocaleUtil.getLocaleNames().length <= 1) {
            itemsToShowMap.remove(CHANGE_LANGUAGE_DRAWER_ITEM_ID);
        }
        if (!CommCarePreferences.isSavedFormsEnabled()) {
            itemsToShowMap.remove(SAVED_FORMS_ITEM_ID);
        }

        drawerItemsShowing = new NavDrawerItem[itemsToShowMap.size()];
        int index = 0;
        for (NavDrawerItem item : itemsToShowMap.values()) {
            drawerItemsShowing[index] =  item;
            index++;
        }
    }

    private ListView.OnItemClickListener getNavDrawerClickListener() {
        return new ListView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                drawerLayout.closeDrawer(navDrawerList);
                switch(drawerItemsShowing[position].id) {
                    case SYNC_DRAWER_ITEM_ID:
                        activity.sendFormsOrSync(true);
                        break;
                    case SAVED_FORMS_ITEM_ID:
                        activity.goToFormArchive(false);
                        break;
                    case UPDATE_DRAWER_ITEM_ID:
                        activity.launchUpdateActivity();
                        break;
                    case ABOUT_CC_DRAWER_ITEM_ID:
                        activity.showAboutCommCareDialog();
                        break;
                    case SETTINGS_DRAWER_ITEM_ID:
                        activity.createPreferencesMenu(activity);
                        break;
                    case ADVANCED_DRAWER_ITEM_ID:
                        activity.startAdvancedActionsActivity();
                        break;
                    case CHANGE_LANGUAGE_DRAWER_ITEM_ID:
                        activity.showLocaleChangeMenu(null);
                        break;
                    case LOGOUT_DRAWER_ITEM_ID:
                        CommCareApplication.instance().closeUserSession();
                        activity.setResult(Activity.RESULT_CANCELED, new Intent(activity.getIntent()));
                        activity.finish();
                        break;
                }
            }
        };
    }

    protected boolean isDrawerOpen() {
        return drawerLayout.isDrawerOpen(navDrawerList);
    }

    protected void openDrawer() {
        drawerLayout.openDrawer(navDrawerList);
    }

    protected void closeDrawer() {
        drawerLayout.closeDrawer(navDrawerList);
    }

    private static String[] getAllItemIds() {
        return new String[] {
                ABOUT_CC_DRAWER_ITEM_ID, UPDATE_DRAWER_ITEM_ID, CHANGE_LANGUAGE_DRAWER_ITEM_ID,
                SETTINGS_DRAWER_ITEM_ID, ADVANCED_DRAWER_ITEM_ID, SYNC_DRAWER_ITEM_ID,
                SAVED_FORMS_ITEM_ID, LOGOUT_DRAWER_ITEM_ID };
    }

    private static String[] getAllItemTitles() {
        String ABOUT_CC_DRAWER_ITEM_TEXT = Localization.get("home.menu.about");
        String UPDATE_DRAWER_ITEM_TEXT = Localization.get("home.menu.update");
        String CHANGE_LANGUAGE_DRAWER_ITEM_TEXT = Localization.get("home.menu.locale.change");
        String SETTINGS_DRAWER_ITEM_TEXT = Localization.get("home.menu.settings");
        String ADVANCED_DRAWER_ITEM_TEXT = Localization.get("home.menu.advanced");
        String SYNC_DRAWER_ITEM_TEXT = Localization.get("home.sync");
        String SAVED_FORMS_ITEM_TEXT = Localization.get("home.menu.saved.forms");
        String LOGOUT_DRAWER_ITEM_TEXT = Localization.get("home.logout");
        return new String[] {
                ABOUT_CC_DRAWER_ITEM_TEXT, UPDATE_DRAWER_ITEM_TEXT, CHANGE_LANGUAGE_DRAWER_ITEM_TEXT,
                SETTINGS_DRAWER_ITEM_TEXT, ADVANCED_DRAWER_ITEM_TEXT, SYNC_DRAWER_ITEM_TEXT,
                SAVED_FORMS_ITEM_TEXT, LOGOUT_DRAWER_ITEM_TEXT
        };
    }

    private static int[] getAllItemIcons() {
        return new int[] {
                R.drawable.ic_blue_forward, R.drawable.ic_blue_forward, R.drawable.ic_blue_forward,
                R.drawable.ic_settings_nav_drawer, R.drawable.ic_blue_forward,
                R.drawable.ic_sync_nav_drawer, R.drawable.ic_blue_forward,
                R.drawable.ic_logout_nav_drawer
        };
    }


}
