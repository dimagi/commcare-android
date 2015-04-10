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
import java.util.LinkedList;

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
    private boolean isInitialized = false;

    private LinkedList<SquareButtonWithNotification> visibleButtons;

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
        SquareButtonWithNotification button = getButton(androidCode, lookupID);
        if (button != null) {
            button.setNotificationText(notificationText);
            notifyDataSetChanged();
        }
    }

    @Override
    public int getCount() {
//        return buttonsResources.length;
        return visibleButtons == null ? buttonsResources.length : visibleButtons.size();
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
        View view;
        if(!isInitialized){
            visibleButtons = new LinkedList<SquareButtonWithNotification>();
            Log.i("HomeScrnAdpt","Creating all buttons because got a null in position " + position);
            for (int i = 0; i < buttons.length; i++) {
                if (buttons[i] != null) continue;
                SquareButtonWithNotification button = (SquareButtonWithNotification) LayoutInflater.from(context)
                        .inflate(buttonsResources[i], parent, false);
                buttons[i] = button;
                Log.i("HomeScrnAdpt","Added button " + button + "to position " + i);

                View.OnClickListener listener = buttonListeners[i];
                // creating now, but set a clickListener before, so we'll add it to this button...
                if(listener != null) {
                    button.setOnClickListener(listener);
                    Log.i("HomeScrnAdpt","Added onClickListener " + listener + " to button in position " + i);
                }
                if( i == position ) view = button;
                if(!hiddenButtons[i]) visibleButtons.add(button);
            }
            isInitialized = true;
        }
        if(position < 0 || position >= getCount()) return null;
        if(convertView != null) {
            return convertView;
        } else {
            SquareButtonWithNotification btn = visibleButtons.get(position);

            if(btn == null) {
                Log.i("HomeScrnAdpt","Unexpected null button");
            }

            return btn;
        }
    }

    public void setButtonVisibility(int androidCode, boolean lookupID, boolean isButtonHidden){
        int index = getButtonIndex(androidCode, lookupID);
        boolean toggled = isButtonHidden ^ hiddenButtons[index];
        hiddenButtons[index] = isButtonHidden;
        if (visibleButtons != null) {
            if(!toggled) return;
            if(isButtonHidden) {
                visibleButtons.remove(buttons[index]);
            } else {
                visibleButtons.add(index, buttons[index]);
            }
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
