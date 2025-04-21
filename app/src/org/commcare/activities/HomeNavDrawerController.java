package org.commcare.activities;

import android.os.Bundle;
import android.widget.ListView;

import org.commcare.CommCareApplication;
import org.commcare.activities.components.NavDrawerItem;
import org.commcare.adapters.NavDrawerAdapter;
import org.commcare.dalvik.R;
import org.commcare.preferences.HiddenPreferences;
import org.commcare.utils.ChangeLocaleUtil;
import org.commcare.utils.SyncDetailCalculations;
import org.javarosa.core.services.locale.Localization;

import java.util.HashMap;
import java.util.Map;

import androidx.appcompat.app.ActionBar;
import androidx.drawerlayout.widget.DrawerLayout;

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
    private static final String TRAINING_DRAWER_ITEM_ID = "training";
    private static final String UPDATE_CC_DRAWER_ITEM_ID = "update-cc";
    private static final String INCOMPLETE_FORMS_ITEM_ID = "incomplete-forms";

    protected static final String KEY_DRAWER_WAS_OPEN = "drawer-open-before-rotation";

    private RootMenuHomeActivity activity;

    private DrawerLayout drawerLayout;
    private ListView navDrawerList;
    private Map<String, NavDrawerItem> allDrawerItems;
    private NavDrawerItem[] drawerItemsShowing;
    private boolean reopenDrawerWhenResumed;

    public HomeNavDrawerController(RootMenuHomeActivity activity) {
        this.activity = activity;
        drawerLayout = activity.findViewById(R.id.menu_activity_drawer_layout);
        navDrawerList = activity.findViewById(R.id.nav_drawer);
        // Disable opening of the nav drawer via swiping
        drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED);
    }

    protected void setupNavDrawer(Bundle savedInstanceState) {
        initDrawerItemsMap();
        determineDrawerItemsToInclude();
        navDrawerList.setOnItemClickListener(getNavDrawerClickListener());
        refreshItems();

        ActionBar actionBar = activity.getSupportActionBar();
        actionBar.setHomeAsUpIndicator(R.drawable.ic_menu_bar);
        actionBar.setHomeButtonEnabled(true);
        actionBar.setDisplayHomeAsUpEnabled(true);
        actionBar.setDisplayUseLogoEnabled(false);


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
        boolean hideSavedFormsItem = !HiddenPreferences.isSavedFormsEnabled();
        boolean hideChangeLanguageItem = ChangeLocaleUtil.getLocaleNames().length <= 1;
        boolean hideTrainingItem = !CommCareApplication.instance().getCurrentApp().hasVisibleTrainingContent();
        boolean hideIncompleteFormsItem = !HiddenPreferences.isIncompleteFormsEnabled();
        int numItemsToInclude = allDrawerItems.size()
                - (hideChangeLanguageItem ? 1 : 0)
                - (hideSavedFormsItem ? 1 : 0)
                - (hideTrainingItem ? 1 : 0)
                - (hideIncompleteFormsItem ? 1 : 0)
                - (activity.showCommCareUpdateMenu ? 0 : 1);

        drawerItemsShowing = new NavDrawerItem[numItemsToInclude];
        int index = 0;
        for (String id : getAllItemIdsInOrder()) {
            NavDrawerItem item = allDrawerItems.get(id);
            if (!excludeItem(id, hideChangeLanguageItem, hideSavedFormsItem, hideTrainingItem,
                    !activity.showCommCareUpdateMenu, hideIncompleteFormsItem)) {
                drawerItemsShowing[index] = item;
                index++;
            }
        }
    }

    private boolean excludeItem(String itemId, boolean hideChangeLanguageItem,
                                boolean hideSavedFormsItem, boolean hideTrainingItem, boolean hideCCUpdateItem,
                                boolean hideIncompleteFormsItem) {
        return (itemId.equals(CHANGE_LANGUAGE_DRAWER_ITEM_ID) && hideChangeLanguageItem) ||
                (itemId.equals(SAVED_FORMS_ITEM_ID) && hideSavedFormsItem) ||
                (itemId.equals(INCOMPLETE_FORMS_ITEM_ID) && hideIncompleteFormsItem) ||
                (itemId.equals(TRAINING_DRAWER_ITEM_ID) && hideTrainingItem) ||
                (itemId.equals(UPDATE_CC_DRAWER_ITEM_ID) && hideCCUpdateItem);
    }

    private ListView.OnItemClickListener getNavDrawerClickListener() {
        return (parent, view, position, id) -> {
            drawerLayout.closeDrawer(navDrawerList);
            switch (drawerItemsShowing[position].id) {
                case SYNC_DRAWER_ITEM_ID:
                    activity.sendFormsOrSync(true);
                    break;
                case SAVED_FORMS_ITEM_ID:
                    activity.goToFormArchive(false);
                    break;
                case UPDATE_DRAWER_ITEM_ID:
                    activity.launchUpdateActivity(false);
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
                case TRAINING_DRAWER_ITEM_ID:
                    activity.enterTrainingModule();
                    break;
                case UPDATE_CC_DRAWER_ITEM_ID:
                    activity.startCommCareUpdate();
                    break;
                case INCOMPLETE_FORMS_ITEM_ID:
                    activity.goToFormArchive(true);
                    break;
            }
        };
    }

    private static String[] getAllItemIdsInOrder() {
        return new String[]{
                ABOUT_CC_DRAWER_ITEM_ID, TRAINING_DRAWER_ITEM_ID, SETTINGS_DRAWER_ITEM_ID,
                ADVANCED_DRAWER_ITEM_ID, CHANGE_LANGUAGE_DRAWER_ITEM_ID, SAVED_FORMS_ITEM_ID,
                INCOMPLETE_FORMS_ITEM_ID, UPDATE_DRAWER_ITEM_ID, SYNC_DRAWER_ITEM_ID, UPDATE_CC_DRAWER_ITEM_ID,
                LOGOUT_DRAWER_ITEM_ID};
    }

    private static String getItemTitle(String id) {
        switch (id) {
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
            case INCOMPLETE_FORMS_ITEM_ID:
                return Localization.get("app.workflow.incomplete.heading");
            case LOGOUT_DRAWER_ITEM_ID:
                return Localization.get("home.logout");
            case TRAINING_DRAWER_ITEM_ID:
                return Localization.get("training.root.title");
            case UPDATE_CC_DRAWER_ITEM_ID:
                return Localization.get("home.menu.update.commcare");
        }
        return "";
    }

    private static int getItemIcon(String id) {
        switch (id) {
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
            case INCOMPLETE_FORMS_ITEM_ID:
                return R.drawable.incomplete_nav_drawer;
            case LOGOUT_DRAWER_ITEM_ID:
                return R.drawable.ic_logout_nav_drawer;
            case TRAINING_DRAWER_ITEM_ID:
                return R.drawable.ic_training_nav_drawer;
            case UPDATE_CC_DRAWER_ITEM_ID:
                return R.drawable.ic_cc_update;
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
