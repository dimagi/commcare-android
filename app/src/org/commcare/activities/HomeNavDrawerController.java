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
import org.commcare.utils.SyncDetailCalculations;
import org.javarosa.core.services.locale.Localization;

import java.util.HashMap;
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
        for (String itemId : getAllItemIdsInOrder()) {
            NavDrawerItem item = new NavDrawerItem(itemId, getItemTitle(itemId),
                    getItemIcon(itemId), getItemSubtext(itemId));
            allDrawerItems.put(itemId, item);
        }
    }

    private void initDrawerItemsToInclude() {
        boolean shouldShowSavedFormsItem = CommCarePreferences.isSavedFormsEnabled();
        boolean shouldShowChangeLanguageItem = ChangeLocaleUtil.getLocaleNames().length > 1;
        int numItemsToInclude = allDrawerItems.size()
                - (shouldShowChangeLanguageItem ? 0 : 1)
                - (shouldShowSavedFormsItem ? 0 : 1);

        drawerItemsShowing = new NavDrawerItem[numItemsToInclude];
        int index = 0;
        for (String id : getAllItemIdsInOrder()) {
            NavDrawerItem item = allDrawerItems.get(id);
            if ((id.equals(CHANGE_LANGUAGE_DRAWER_ITEM_ID) && !shouldShowChangeLanguageItem) ||
                    (id.equals(SAVED_FORMS_ITEM_ID) && !shouldShowSavedFormsItem)) {
                continue;
            } else {
                drawerItemsShowing[index] = item;
                index++;
            }
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

    private static String[] getAllItemIdsInOrder() {
        return new String[] {
                ABOUT_CC_DRAWER_ITEM_ID, SETTINGS_DRAWER_ITEM_ID, ADVANCED_DRAWER_ITEM_ID,
                CHANGE_LANGUAGE_DRAWER_ITEM_ID, SAVED_FORMS_ITEM_ID, UPDATE_DRAWER_ITEM_ID,
                SYNC_DRAWER_ITEM_ID, LOGOUT_DRAWER_ITEM_ID };
    }

    private static String getItemTitle(String id) {
        switch(id) {
            case ABOUT_CC_DRAWER_ITEM_ID:
                return Localization.get("home.menu.about");
            case SETTINGS_DRAWER_ITEM_ID:
                return Localization.get("home.menu.settings");
            case UPDATE_DRAWER_ITEM_ID:
                return Localization.get("home.menu.update");
            case CHANGE_LANGUAGE_DRAWER_ITEM_ID:
                return Localization.get("home.menu.locale.change");
            case ADVANCED_DRAWER_ITEM_ID:
                return Localization.get("home.menu.advanced");
            case SYNC_DRAWER_ITEM_ID:
                return Localization.get("home.sync");
            case SAVED_FORMS_ITEM_ID:
                return Localization.get("home.menu.saved.forms");
            case LOGOUT_DRAWER_ITEM_ID:
                return Localization.get("home.logout");
        }
        return "";
    }

    private static int getItemIcon(String id) {
        switch(id) {
            case ABOUT_CC_DRAWER_ITEM_ID:
                return R.drawable.ic_about_cc_nav_drawer;
            case SETTINGS_DRAWER_ITEM_ID:
                return R.drawable.ic_settings_nav_drawer;
            case UPDATE_DRAWER_ITEM_ID:
                return R.drawable.ic_update_nav_drawer;
            case CHANGE_LANGUAGE_DRAWER_ITEM_ID:
                return R.drawable.ic_change_lang_nav_drawer;
            case ADVANCED_DRAWER_ITEM_ID:
                return R.drawable.ic_cog_nav_drawer;
            case SYNC_DRAWER_ITEM_ID:
                return R.drawable.ic_sync_nav_drawer;
            case SAVED_FORMS_ITEM_ID:
                return R.drawable.ic_saved_forms_nav_drawer;
            case LOGOUT_DRAWER_ITEM_ID:
                return R.drawable.ic_logout_nav_drawer;
        }
        return -1;
    }

    private String getItemSubtext(String id) {
        if (SYNC_DRAWER_ITEM_ID.equals(id)) {
            return SyncDetailCalculations.getLastSyncTimeAndMessage().second;
        } else {
            return null;
        }
    }

}