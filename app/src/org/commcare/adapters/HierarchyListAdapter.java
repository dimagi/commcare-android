package org.commcare.adapters;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;

import org.commcare.views.HierarchyElementView;
import org.odk.collect.android.logic.HierarchyElement;

import java.util.ArrayList;
import java.util.List;

public class HierarchyListAdapter extends BaseAdapter {
    private final Context mContext;
    private List<HierarchyElement> mItems = new ArrayList<>();

    public HierarchyListAdapter(Context context) {
        mContext = context;
    }

    @Override
    public int getCount() {
        return mItems.size();
    }

    @Override
    public Object getItem(int position) {
        return mItems.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        HierarchyElementView hev;
        if (convertView == null) {
            hev = new HierarchyElementView(mContext, mItems.get(position));
        } else {
            hev = (HierarchyElementView)convertView;
            HierarchyElement entry = mItems.get(position);
            hev.setFromHierarchyElement(entry);
        }

        if (mItems.get(position).getSecondaryText() == null
                || mItems.get(position).getSecondaryText().equals("")) {
            hev.showSecondary(false);
        } else {
            hev.showSecondary(true);
        }
        return hev;
    }

    /**
     * Sets the list of items for this adapter to use.
     */
    public void setListItems(List<HierarchyElement> it) {
        mItems = it;
    }
}
