package org.commcare.activities;

import android.os.Bundle;
import android.widget.ListView;
import android.widget.TextView;

import org.commcare.CommCareApplication;
import org.commcare.adapters.MenuAdapter;
import org.commcare.dalvik.R;
import org.commcare.fragments.BreadcrumbBarFragment;
import org.commcare.views.ManagedUi;
import org.commcare.views.UiElement;

@ManagedUi(R.layout.screen_suite_menu)
public class MenuList extends MenuBase {

    private MenuAdapter adapter;

    @UiElement(R.id.screen_suite_menu_list)
    private ListView list;

    // removed the UiElement annotation here because it was causing a crash @ loadFields() in CommCareActivity
    private TextView header;

    @Override
    protected void onCreateSessionSafe(Bundle savedInstanceState) {
        super.onCreateSessionSafe(savedInstanceState);

        if (header == null) {
            header = (TextView)getLayoutInflater().inflate(R.layout.menu_list_header, null);
        }
        String subHeaderTitle = BreadcrumbBarFragment.getBestSubHeaderTitle();
        if (subHeaderTitle != null) {
            header.setText(subHeaderTitle);
            // header must not be clickable
            list.addHeaderView(header, null, false);
        }

        adapter = new MenuAdapter(this, CommCareApplication._().getCommCarePlatform(), menuId);
        adapter.showAnyLoadErrors(this);
        refreshView();

        list.setOnItemClickListener(this);
    }

    /**
     * Get form list from database and insert into view.
     */
    private void refreshView() {
        list.setAdapter(adapter);
    }
}
