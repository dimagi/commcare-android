package org.commcare.android.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;

import org.commcare.android.view.SquareButtonWithNotification;
import org.commcare.dalvik.R;

/**
 * Created by dancluna on 3/19/15.
 */
public class HomeScreenAdapter extends BaseAdapter {
    int[] buttonsResources = new int[]{
            R.layout.home_start_button,
            R.layout.home_savedforms_button,
            R.layout.home_sync_button,
            R.layout.home_disconnect_button,
            R.layout.home_incompleteforms_button,
    };
    private Context context;

    public HomeScreenAdapter(Context c){
        this.context = c;
    }

    @Override
    public int getCount() {
        return buttonsResources.length;
    }

    @Override
    public Object getItem(int position) {
        return null;
    }

    @Override
    public long getItemId(int position) {
        return 0;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if(position < 0 || position >= getCount()) return null;
        if(convertView != null){
            return convertView;
        } else {
            SquareButtonWithNotification view = (SquareButtonWithNotification) LayoutInflater.from(context)
                    .inflate(buttonsResources[position], parent, false);
            return view;
        }
    }
}
