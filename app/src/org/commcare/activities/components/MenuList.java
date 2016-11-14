package org.commcare.activities.components;

import android.widget.ListView;
import android.widget.TextView;

import org.commcare.CommCareApplication;
import org.commcare.adapters.MenuAdapter;
import org.commcare.dalvik.R;
import org.commcare.fragments.BreadcrumbBarFragment;

public class MenuList extends MenuBase {

    public static int LAYOUT_FILE = R.layout.screen_suite_menu;

    private TextView header;

    @Override
    protected void initViewAndAdapter(String menuId) {
        adapterView = (ListView)activity.findViewById(R.id.screen_suite_menu_list);
        adapter = new MenuAdapter(activity, CommCareApplication.instance().getCommCarePlatform(),
                menuId);
        addHeaderToView();
    }

    private void addHeaderToView() {
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

}
