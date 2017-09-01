package org.commcare.activities;

import android.os.Bundle;
import android.support.annotation.Nullable;

import org.commcare.CommCareApplication;
import org.commcare.activities.components.MenuList;
import org.commcare.logic.DetailCalloutListenerDefaultImpl;
import org.commcare.preferences.CommCarePreferences;
import org.commcare.session.SessionFrame;
import org.commcare.suite.model.CalloutData;
import org.commcare.suite.model.Menu;
import org.commcare.utils.AndroidCommCarePlatform;
import org.commcare.utils.DetailCalloutListener;

public class MenuActivity extends SessionAwareCommCareActivity<MenuActivity> implements DetailCalloutListener {

    private static final String MENU_STYLE_GRID = "grid";
    @Nullable
    private MenuList menuView;
    
    @Override
    protected void onCreateSessionSafe(Bundle savedInstanceState) {
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
        menuView = MenuList.setupMenuViewInActivity(this, menuId, useGridMenu(menuId), false);
    }

    private static boolean useGridMenu(String currentCommand) {
        // First check if this is enabled in profile
        if (CommCarePreferences.isGridMenuEnabled()) {
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
        onBackPressed();
        return true;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (menuView != null) {
            menuView.onDestroy();
        }
    }

    @Override
    public void callRequested(String phoneNumber) {
        DetailCalloutListenerDefaultImpl.callRequested(this, phoneNumber);
    }

    @Override
    public void addressRequested(String address) {
        DetailCalloutListenerDefaultImpl.addressRequested(this, address);
    }

    @Override
    public void playVideo(String videoRef) {
        DetailCalloutListenerDefaultImpl.playVideo(this, videoRef);
    }

    @Override
    public void performCallout(CalloutData callout, int id) {
        DetailCalloutListenerDefaultImpl.performCallout(this, callout, id);
    }
}
