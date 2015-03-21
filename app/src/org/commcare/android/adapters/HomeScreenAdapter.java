package org.commcare.android.adapters;

import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;

import org.commcare.android.view.SquareButtonWithNotification;
import org.commcare.dalvik.R;

import java.util.HashMap;

/**
 * Created by dancluna on 3/19/15.
 */
public class HomeScreenAdapter extends BaseAdapter {
    //region Buttons

    static final int[] buttonsResources = new int[]{
            R.layout.home_start_button,
            R.layout.home_savedforms_button,
            R.layout.home_sync_button,
            R.layout.home_disconnect_button,
            R.layout.home_incompleteforms_button,
    };

    static final HashMap<Integer, Integer> buttonsIDsToResources = new HashMap<Integer, Integer>() {{
                put(R.id.home_start_sqbn,buttonsResources[0]);
                put(R.id.home_savedforms_sqbn,buttonsResources[1]);
                put(R.id.home_sync_sqbn,buttonsResources[2]);
                put(R.id.home_disconnect_sqbn,buttonsResources[3]);
                put(R.id.home_incompleteforms_sqbn,buttonsResources[4]);
    }};

    //endregion

    //region Private variables

    final View.OnClickListener[] buttonListeners = new View.OnClickListener[buttonsResources.length];

    final SquareButtonWithNotification[] buttons = new SquareButtonWithNotification[buttonsResources.length];

    private Context context;

    //endregion

    //region Constructors

    public HomeScreenAdapter(Context c) { this.context = c; }

    //endregion

    //region Public API

    public void setOnClickListenerForButton(int androidCode, boolean lookupID, View.OnClickListener listener){
        int code = androidCode;
        // if lookupID is set, we are mapping from an int in R.id to one in R.layout
        if(lookupID){
            Integer layoutCode = buttonsIDsToResources.get(androidCode);
            if(layoutCode == null) throw new IllegalArgumentException("ID code not found: " + androidCode);
            code = layoutCode;
        }
        Integer buttonIndex = null;
        for (int i = 0; i < buttonsResources.length; i++) {
            if(code == buttonsResources[i]){
                buttonIndex = i;
            }
        }
        if(buttonIndex == null) throw new IllegalArgumentException("Layout code not found: " + code);
        setOnClickListenerForButton(buttonIndex, listener);
    }

    @Override
    public int getCount() {
        return buttonsResources.length;
    }

    @Override
    public Object getItem(int position) {
        return buttons[position];
    }

    @Override
    public long getItemId(int position) {
        return buttonsResources[position];
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if(position < 0 || position >= getCount()) return null;
        if(convertView != null){
            return convertView;
        } else {
            SquareButtonWithNotification view = (SquareButtonWithNotification) LayoutInflater.from(context)
                    .inflate(buttonsResources[position], parent, false);
            buttons[position] = view;
            Log.i("DEBUG-i","Added button " + view + "to position " + position);

            View.OnClickListener listener = buttonListeners[position];
            // creating now, but set a clickListener before, so we'll add it to this button...
            if(listener != null){
                view.setOnClickListener(listener);
                Log.i("DEBUG-i","Added onClickListener " + listener + " to button in position " + position);
            }
            return view;
        }
    }

    //endregion

    //region Private methods

    private void setOnClickListenerForButton(int buttonIndex, View.OnClickListener listener) {
        buttonListeners[buttonIndex] = listener;
        SquareButtonWithNotification button = (SquareButtonWithNotification) getItem(buttonIndex);
        if(button != null){
            button.setOnClickListener(listener);
        }
    }

    //endregion
}
