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
            R.layout.home_incompleteforms_button,
            R.layout.home_sync_button,
            R.layout.home_disconnect_button,
    };

    static final HashMap<Integer, Integer> buttonsIDsToResources = new HashMap<Integer, Integer>() {{
                put(R.id.home_start_sqbn,R.layout.home_start_button);
                put(R.id.home_savedforms_sqbn,R.layout.home_savedforms_button);
                put(R.id.home_sync_sqbn,R.layout.home_sync_button);
                put(R.id.home_disconnect_sqbn,R.layout.home_disconnect_button);
                put(R.id.home_incompleteforms_sqbn,R.layout.home_incompleteforms_button);
    }};

    //endregion

    //region Private variables

    final View.OnClickListener[] buttonListeners = new View.OnClickListener[buttonsResources.length];

    final SquareButtonWithNotification[] buttons = new SquareButtonWithNotification[buttonsResources.length];

    private Context context;

    private boolean[] hiddenButtons = new boolean[buttonsResources.length];

    //endregion

    //region Constructors

    public HomeScreenAdapter(Context c) { this.context = c; }

    //endregion

    //region Public API

    public void setOnClickListenerForButton(int androidCode, boolean lookupID, View.OnClickListener listener){
        int buttonIndex = getButtonIndex(androidCode, lookupID);
        setOnClickListenerForButton(buttonIndex, listener);
    }

    public SquareButtonWithNotification getButton(int androidCode, boolean lookupID){
        return buttons[getButtonIndex(androidCode, lookupID)];
    }

    public void setNotificationTextForButton(int androidCode, boolean lookupID, String notificationText) {
        getButton(androidCode, lookupID).setNotificationText(notificationText);
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
        for (int i = 0; i < hiddenButtons.length; i++) {
            if(hiddenButtons[i]) return null;
        }
        if(convertView != null){
            return convertView;
        } else {
            SquareButtonWithNotification view = (SquareButtonWithNotification) LayoutInflater.from(context)
                    .inflate(buttonsResources[position], parent, false);
            buttons[position] = view;
            Log.i("HomeScrnAdpt","Added button " + view + "to position " + position);

            View.OnClickListener listener = buttonListeners[position];
            // creating now, but set a clickListener before, so we'll add it to this button...
            if(listener != null){
                view.setOnClickListener(listener);
                Log.i("HomeScrnAdpt","Added onClickListener " + listener + " to button in position " + position);
            }
            return view;
        }
    }

    public void setButtonVisibility(int androidCode, boolean lookupID, boolean isButtonHidden){
        hiddenButtons[getButtonIndex(androidCode, lookupID)] = isButtonHidden;
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

    /**
     * Returns the index of the button with the given resource code. If lookupID is set, will search for the button with the given R.id; if not, will search for the button with the given R.layout code.
     * @param androidCode
     * @param lookupID
     * @return
     * @throws java.lang.IllegalArgumentException If the given androidCode is not found
     */
    private int getButtonIndex(int androidCode, boolean lookupID){
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
        return buttonIndex;
    }

    //endregion
}
