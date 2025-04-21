package org.commcare.activities;

import android.os.Bundle;
import androidx.annotation.Nullable;

import org.commcare.CommCareApplication;
import org.commcare.activities.components.MenuList;
import org.commcare.preferences.MainConfigurablePreferences;
import org.commcare.session.SessionFrame;
import org.commcare.suite.model.Menu;
import org.commcare.utils.AndroidCommCarePlatform;

public class MenuActivity extends SessionAwareCommCareActivity<MenuActivity> {

    private static final String MENU_STYLE_GRID = "grid";

    @Override
    public void onCreateSessionSafe(Bundle savedInstanceState) {
        super.onCreateSessionSafe(savedInstanceState);
        String menuId = getIntent().getStringExtra(SessionFrame.STATE_COMMAND_ID);
        if (menuId == null) {
            menuId = Menu.ROOT_MENU_ID;
        }
        if (menuId.equals(Menu.ROOT_MENU_ID) && DispatchActivity.useRootMenuHomeActivity()) {
            // Pressing back from any screen immediately after the RootMenuHomeActivity will take
            // us here, so we want to redirect
            finish();
            return;
        }
        MenuList.setupMenuViewInActivity(this, menuId, useGridMenu(menuId), false);
    }

    private static boolean useGridMenu(String currentCommand) {
        // First check if this is enabled in profile
        if (MainConfigurablePreferences.isGridMenuEnabled()) {
            return true;
        }
        // If not, check style attribute for this particular menu block
        AndroidCommCarePlatform platform = CommCareApplication.instance().getCommCarePlatform();
        String commonDisplayStyle = platform.getMenuDisplayStyle(currentCommand);
        return MENU_STYLE_GRID.equals(commonDisplayStyle);
    }

    @Override
    public String getActivityTitle() {
        return null;
    }

    @Override
    protected boolean isTopNavEnabled() {
        return true;
    }

    @Override
    protected boolean onBackwardSwipe() {
        if(!isFinishing()) {
            onBackPressed();
        }
        return true;
    }

    @Override
    public boolean shouldListenToSyncComplete() {
        return true;
    }
}
