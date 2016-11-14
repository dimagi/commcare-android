package org.commcare.activities.components;

import android.app.Activity;
import android.content.Intent;
import android.view.View;
import android.widget.AdapterView;

import org.commcare.activities.CommCareActivity;
import org.commcare.activities.HomeScreenCapableActivity;
import org.commcare.adapters.MenuAdapter;
import org.commcare.session.SessionFrame;
import org.commcare.suite.model.Entry;
import org.commcare.suite.model.Menu;

public abstract class MenuBase implements AdapterView.OnItemClickListener {

    protected CommCareActivity activity;
    protected AdapterView adapterView;
    protected MenuAdapter adapter;
    private boolean beingUsedAsHomeScreen;

    public static void setupMenusViewInActivity(CommCareActivity activity, String menuId,
                                                boolean useGridMenu, boolean beingUsedAsHomeScreen) {
        MenuBase menuBase;
        if (useGridMenu) {
            activity.setContentView(MenuGrid.LAYOUT_FILE);
            menuBase = new MenuGrid();
        } else {
            activity.setContentView(MenuList.LAYOUT_FILE);
            menuBase = new MenuList();
        }
        menuBase.activity = activity;
        menuBase.beingUsedAsHomeScreen = beingUsedAsHomeScreen;
        menuBase.setupMenu(menuId);
    }

    public MenuBase() {

    }

    protected void setupMenu(String menuId) {
        initViewAndAdapter(menuId);
        setupAdapter();
    }

    protected abstract void initViewAndAdapter(String menuId);

    protected void setupAdapter() {
        adapter.showAnyLoadErrors(activity);
        adapterView.setOnItemClickListener(this);
        adapterView.setAdapter(adapter);
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

        if (beingUsedAsHomeScreen) {
            ((HomeScreenCapableActivity)activity).setCommandAndProceed(commandId);
        } else {
            Intent i = new Intent(activity.getIntent());
            i.putExtra(SessionFrame.STATE_COMMAND_ID, commandId);
            activity.setResult(Activity.RESULT_OK, i);
            activity.finish();
        }
    }

}
