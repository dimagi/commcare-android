package org.commcare.adapters;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import org.commcare.activities.components.NavDrawerItem;
import org.commcare.dalvik.R;

/**
 * @author amstone326
 */
public class NavDrawerAdapter extends ArrayAdapter<NavDrawerItem> {

    Context context;

    public NavDrawerAdapter(Context context, NavDrawerItem[] drawerItems) {
        super(context, android.R.layout.simple_list_item_1, drawerItems);
        this.context = context;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View v = convertView;
        if (v == null) {
            v = View.inflate(context, R.layout.nav_drawer_item_view, null);
        }
        NavDrawerItem item = this.getItem(position);
        ((TextView)v.findViewById(R.id.drawer_text)).setText(item.text);
        ((ImageView)v.findViewById(R.id.drawer_icon)).setImageResource(item.iconResource);
        TextView subtext = ((TextView)v.findViewById(R.id.drawer_subtext));
        if (item.subtext != null && !"".equals(item.subtext)) {
            subtext.setText(item.subtext);
            subtext.setVisibility(View.VISIBLE);
        } else {
            subtext.setVisibility(View.GONE);
        }
        return v;
    }
}
