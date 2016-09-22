package org.commcare.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;

import org.commcare.CommCareApplication;
import org.commcare.session.SessionFrame;
import org.commcare.suite.model.Entry;
import org.commcare.suite.model.Menu;

public abstract class MenuBase
        extends SaveSessionCommCareActivity
        implements AdapterView.OnItemClickListener {

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
    }

    /**
     * Stores the path of selected form and finishes.
     */
    @Override
    public void onItemClick(AdapterView listView, View view, int position, long id) {
        String commandId;
        Object value = listView.getAdapter().getItem(position);

        // if value is null, probably it means that we clicked on the header view, so we just ignore it
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
        onBackPressed();
        return true;
    }

    @Override
    public boolean isBackEnabled() {
        return !(CommCareApplication._().isConsumerApp() && isRootModuleMenu);
    }
}
