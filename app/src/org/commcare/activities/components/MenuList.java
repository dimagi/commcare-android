package org.commcare.activities.components;

import android.app.Activity;
import android.content.Intent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.TextView;

import org.commcare.CommCareApplication;
import org.commcare.activities.CommCareActivity;
import org.commcare.activities.HomeScreenBaseActivity;
import org.commcare.adapters.MenuAdapter;
import org.commcare.dalvik.R;
import org.commcare.fragments.BreadcrumbBarFragment;
import org.commcare.session.SessionFrame;
import org.commcare.suite.model.Entry;
import org.commcare.suite.model.Menu;

public class MenuList implements AdapterView.OnItemClickListener {

    protected CommCareActivity activity;
    protected AdapterView adapterView;
    protected MenuAdapter adapter;
    private boolean beingUsedAsHomeScreen;
    private TextView header;

    /**
     * Injects a list (or grid) of CommCare modules/forms for the given menu id into the UI of
     * the given activity
     */
    public static void setupMenuViewInActivity(CommCareActivity activity, String menuId,
                                               boolean useGridMenu, boolean beingUsedAsHomeScreen) {
        MenuList menuView;
        if (useGridMenu) {
            menuView = new MenuGrid();
        } else {
            menuView = new MenuList();
        }
        menuView.setupMenuInActivity(activity, menuId);
        menuView.beingUsedAsHomeScreen = beingUsedAsHomeScreen;
    }

    public int getLayoutFileResource() {
        return R.layout.screen_suite_menu;
    }

    protected void setupMenuInActivity(CommCareActivity activity, String menuId) {
        this.activity = activity;
        activity.setContentView(getLayoutFileResource());
        initViewAndAdapter(menuId);
        setupAdapter();
    }

    protected void initViewAndAdapter(String menuId) {
        adapterView = (ListView)activity.findViewById(R.id.screen_suite_menu_list);
        adapter = new MenuAdapter(activity, CommCareApplication.instance().getCommCarePlatform(),
                menuId);

        // in menu list only, we add a header
        if (header == null) {
            header = (TextView)activity.getLayoutInflater().inflate(R.layout.menu_list_header, null);
        }
        String subHeaderTitle = BreadcrumbBarFragment.getBestSubHeaderTitle();
        if (subHeaderTitle != null) {
            header.setText(subHeaderTitle);
            // header must not be clickable
            ((ListView)adapterView).addHeaderView(header, null, false);
        }
    }

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

        if (beingUsedAsHomeScreen) {
            // If we are using a MenuList as our home screen, we don't want to finish() here
            // because there is nowhere to go back to. Instead, just set the selected command
            // and trigger the next session step
            ((HomeScreenBaseActivity)activity).setCommandAndProceed(commandId);
        } else {
            Intent i = new Intent(activity.getIntent());
            i.putExtra(SessionFrame.STATE_COMMAND_ID, commandId);
            activity.setResult(Activity.RESULT_OK, i);
            activity.finish();
        }
    }

}
