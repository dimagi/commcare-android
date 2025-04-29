package org.commcare.activities.components;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;

import androidx.annotation.NonNull;

import android.view.View;
import android.widget.AdapterView;
import android.widget.ListAdapter;
import android.widget.ListView;

import org.commcare.CommCareApplication;
import org.commcare.activities.CommCareActivity;
import org.commcare.activities.HomeScreenBaseActivity;
import org.commcare.adapters.MenuAdapter;
import org.commcare.dalvik.R;
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil;
import org.commcare.session.SessionFrame;
import org.commcare.suite.model.Entry;
import org.commcare.suite.model.Menu;

public class MenuList implements AdapterView.OnItemClickListener {

    protected CommCareActivity activity;
    protected AdapterView<ListAdapter> adapterView;
    protected MenuAdapter adapter;
    private boolean beingUsedInHomeScreen;

    /**
     * Injects a list (or grid) of CommCare modules/forms for the given menu id into the UI of
     * the given activity
     */
    @NonNull
    public static MenuList setupMenuViewInActivity(CommCareActivity activity, String menuId,
                                                   boolean useGridMenu, boolean beingUsedInHomeScreen) {
        MenuList menuView;
        if (useGridMenu) {
            menuView = new MenuGrid();
        } else {
            menuView = new MenuList();
        }
        menuView.setupMenuInActivity(activity, menuId);
        menuView.beingUsedInHomeScreen = beingUsedInHomeScreen;
        return menuView;
    }

    public int getLayoutFileResource() {
        return R.layout.screen_suite_menu;
    }

    private void setupMenuInActivity(CommCareActivity activity, String menuId) {
        this.activity = activity;
        activity.setContentView(getLayoutFileResource());
        initViewAndAdapter(menuId);
        setupAdapter();
    }

    protected void initViewAndAdapter(String menuId) {
        adapterView = (ListView)activity.findViewById(R.id.screen_suite_menu_list);
        adapter = new MenuAdapter(activity, CommCareApplication.instance().getCommCarePlatform(),
                menuId);
    }

    protected void setupAdapter() {
        adapter.showAnyLoadErrors(activity);
        adapterView.setOnItemClickListener(this);
        adapterView.setAdapter(adapter);
    }

    public void refreshItems() {
        adapter.notifyDataSetChanged();
        adapterView.setAdapter(adapter);
    }

    /**
     * Stores the path of selected form and finishes.
     */
    @Override
    public void onItemClick(AdapterView listView, View view, int position, long id) {
        Object value = listView.getAdapter().getItem(position);
        if (value == null) {
            // Probably means that we clicked on the header view, so just ignore it
            return;
        }

        String commandId;
        if (value instanceof Entry) {
            commandId = ((Entry)value).getCommandId();
        } else {
            commandId = ((Menu)value).getId();
        }
        FirebaseAnalyticsUtil.reportMenuItemClick(commandId);
        Intent i = new Intent(activity.getIntent());
        i.putExtra(SessionFrame.STATE_COMMAND_ID, commandId);
        if (beingUsedInHomeScreen) {
            // If this MenuList is on our home screen, that means we can't finish() here because we
            // are already in our home activity. Instead, just manually launch the same code path
            // that would have been initiated by onActivityResult of HomeScreenBaseActivity
            HomeScreenBaseActivity homeActivity = (HomeScreenBaseActivity)activity;
            if (homeActivity.processReturnFromGetCommand(AppCompatActivity.RESULT_OK, i)) {
                homeActivity.startNextSessionStepSafe();
            }
        } else {
            activity.setResult(AppCompatActivity.RESULT_OK, i);
            activity.finish();
        }
    }
}
