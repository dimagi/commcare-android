package org.commcare.activities;

import android.annotation.TargetApi;
import android.app.ActionBar;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.widget.DrawerLayout;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;

import org.commcare.activities.components.NavDrawerItem;
import org.commcare.adapters.NavDrawerAdapter;
import org.commcare.dalvik.R;
import org.commcare.preferences.HiddenCommCarePreferences;
import org.commcare.utils.ChangeLocaleUtil;
import org.commcare.utils.SyncDetailCalculations;
import org.javarosa.core.services.locale.Localization;

import java.util.HashMap;
import java.util.Map;

/**
 * Manages the UI of the nav drawer in RootMenuHomeActivity
 *
 * @author Aliza Stone
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

    protected static final String KEY_DRAWER_WAS_OPEN = "drawer-open-before-rotation";

    private RootMenuHomeActivity activity;

    private DrawerLayout drawerLayout;
    private ListView navDrawerList;
    private Map<String, NavDrawerItem> allDrawerItems;
    private NavDrawerItem[] drawerItemsShowing;
    private boolean reopenDrawerWhenResumed;

    public HomeNavDrawerController(RootMenuHomeActivity activity) {
        this.activity = activity;
        drawerLayout = (DrawerLayout)activity.findViewById(R.id.menu_activity_drawer_layout);
        navDrawerList = (ListView)activity.findViewById(R.id.nav_drawer);
        // Disable opening of the nav drawer via swiping
        drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED);
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    protected void setupNavDrawer(Bundle savedInstanceState) {
        initDrawerItemsMap();
        determineDrawerItemsToInclude();
        navDrawerList.setOnItemClickListener(getNavDrawerClickListener());
        refreshItems();

        ActionBar actionBar = activity.getActionBar();
        actionBar.setHomeButtonEnabled(true);
        actionBar.setDisplayHomeAsUpEnabled(false);
        actionBar.setDisplayUseLogoEnabled(false);
        actionBar.setIcon(R.drawable.ic_menu_bar);

        if (savedInstanceState != null &&
                savedInstanceState.getBoolean(KEY_DRAWER_WAS_OPEN)) {
            reopenDrawerWhenResumed = true;
        }
    }

    protected void reopenDrawerIfNeeded() {
        if (reopenDrawerWhenResumed) {
            openDrawer();
            reopenDrawerWhenResumed = false;
        }
    }

    protected void refreshItems() {
        updateItemSubtexts();
        determineDrawerItemsToInclude();
        navDrawerList.setAdapter(new NavDrawerAdapter(activity, drawerItemsShowing));
    }

    private void updateItemSubtexts() {
        NavDrawerItem syncItem = allDrawerItems.get(SYNC_DRAWER_ITEM_ID);
        syncItem.subtext = SyncDetailCalculations.getLastSyncTimeAndMessage().second;
    }

    private void initDrawerItemsMap() {
        allDrawerItems = new HashMap<>();
        for (String itemId : getAllItemIdsInOrder()) {
            NavDrawerItem item = new NavDrawerItem(itemId, getItemTitle(itemId),
                    getItemIcon(itemId), getItemSubtext(itemId));
            allDrawerItems.put(itemId, item);
        }
    }

    private void determineDrawerItemsToInclude() {
        boolean hideSavedFormsItem = !HiddenCommCarePreferences.isSavedFormsEnabled();
        boolean hideChangeLanguageItem = ChangeLocaleUtil.getLocaleNames().length <= 1;
        int numItemsToInclude = allDrawerItems.size()
                - (hideChangeLanguageItem ? 1 : 0)
                - (hideSavedFormsItem ? 1 : 0);

        drawerItemsShowing = new NavDrawerItem[numItemsToInclude];
        int index = 0;
        for (String id : getAllItemIdsInOrder()) {
            NavDrawerItem item = allDrawerItems.get(id);
            if (!excludeItem(id, hideChangeLanguageItem, hideSavedFormsItem)) {
                drawerItemsShowing[index] = item;
                index++;
            }
        }
    }

    private boolean excludeItem(String itemId,
                                boolean hideChangeLanguageItem,
                                boolean hideSavedFormsItem) {
        return (itemId.equals(CHANGE_LANGUAGE_DRAWER_ITEM_ID) && hideChangeLanguageItem) ||
                (itemId.equals(SAVED_FORMS_ITEM_ID) && hideSavedFormsItem);
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
                        HomeScreenBaseActivity.createPreferencesMenu(activity);
                        break;
                    case ADVANCED_DRAWER_ITEM_ID:
                        activity.showAdvancedActionsPreferences();
                        break;
                    case CHANGE_LANGUAGE_DRAWER_ITEM_ID:
                        activity.showLocaleChangeMenu(null);
                        break;
                    case LOGOUT_DRAWER_ITEM_ID:
                        activity.userTriggeredLogout();
                        break;
                }
            }
        };
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

    private static String getItemSubtext(String id) {
        if (SYNC_DRAWER_ITEM_ID.equals(id)) {
            return SyncDetailCalculations.getLastSyncTimeAndMessage().second;
        } else {
            return null;
        }
    }

    protected boolean isDrawerOpen() {
        return drawerLayout.isDrawerOpen(navDrawerList);
    }

    protected void toggleDrawer() {
        if (isDrawerOpen()) {
            closeDrawer();
        } else {
            openDrawer();
        }
    }

    private void openDrawer() {
        drawerLayout.openDrawer(navDrawerList);
    }

    private void closeDrawer() {
        drawerLayout.closeDrawer(navDrawerList);
    }

}