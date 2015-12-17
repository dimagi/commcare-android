package org.commcare.android.adapters;


import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;

import org.commcare.android.view.SquareButtonWithNotification;
import org.commcare.dalvik.BuildConfig;
import org.commcare.dalvik.R;

import java.util.ArrayList;

/**
 * Sets up home screen buttons and gives accessors for setting their visibility and listeners
 * Created by dancluna on 3/19/15.
 */
public abstract class SquareButtonAdapter extends BaseAdapter {
    private static final String TAG = SquareButtonAdapter.class.getSimpleName();

    private final SquareButtonWithNotification[] buttons =
            new SquareButtonWithNotification[getButtonResources().length];

    private final boolean[] hiddenButtons = new boolean[getButtonResources().length];

    private final ArrayList<SquareButtonWithNotification> visibleButtons;



    public SquareButtonAdapter(Context c) {
        visibleButtons = new ArrayList<SquareButtonWithNotification>();
        LayoutInflater inflater = LayoutInflater.from(c);
        for (int i = 0; i < buttons.length; i++) {
            if (buttons[i] == null) {
                SquareButtonWithNotification button =
                        (SquareButtonWithNotification) inflater.inflate(getButtonResources()[i].getResource(), null, false);
                buttons[i] = button;
                Log.i(TAG, "Added button " + button + "to position " + i);

                if (!hiddenButtons[i]) {
                    visibleButtons.add(button);
                }
            }
        }
    }

    /**
     * Sets the onClickListener for the given button
     *
     * @param resourceCode Android resource code (R.id.$button or R.layout.$button)
     * @param listener     OnClickListener for the button
     */
    public void setOnClickListenerForButton(int resourceCode, View.OnClickListener listener) {
        int buttonIndex = getButtonIndex(resourceCode);
        SquareButtonWithNotification button = (SquareButtonWithNotification) getItem(buttonIndex);
        if (button != null) {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "Preexisting button when calling setOnClickListenerForButton");
            }
            button.setOnClickListener(listener);
        } else {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "Button did not exist when calling setOnClickListenerForButton!");
            }
        }
    }

    public SquareButtonWithNotification getButton(int resourceCode) {
        return buttons[getButtonIndex(resourceCode)];
    }

    public void setNotificationTextForButton(int resourceCode, String notificationText) {
        SquareButtonWithNotification button = getButton(resourceCode);
        if (button != null) {
            button.setNotificationText(notificationText);
            notifyDataSetChanged();
        }
    }

    @Override
    public int getCount() {
        return visibleButtons.size();
    }

    @Override
    public Object getItem(int position) {
        return buttons[position];
    }

    @Override
    public long getItemId(int position) {
        return getButtonResources()[position].getResource();
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (position < 0 || position >= getCount()) {
            return null;
        }
        SquareButtonWithNotification btn = visibleButtons.get(position);

        if (btn == null) {
            Log.i(TAG, "Unexpected null button");
        }
        return btn;
    }

    /**
     * Sets visibility for the button with the given resource code
     *
     * @param resourceCode   Android resource code (R.id.$button or R.layout.$button)
     * @param isButtonHidden Button visibility state (true for hidden, false for visible)
     */
    public void setButtonVisibility(int resourceCode, boolean isButtonHidden) {
        int index = getButtonIndex(resourceCode);
        boolean hasVisibilityChanged = isButtonHidden ^ hiddenButtons[index];
        hiddenButtons[index] = isButtonHidden;
        if (hasVisibilityChanged) {
            if (isButtonHidden) {
                visibleButtons.remove(buttons[index]);
            } else {
                visibleButtons.add(index, buttons[index]);
            }
        }
    }

    /**
     * Returns the index of the button with the given resource code.
     *
     * @throws IllegalArgumentException If the given resourceCode is not found
     */
    private int getButtonIndex(int resourceCode) {
        for (int i = 0; i < getButtonResources().length; i++) {
            if (resourceCode == getButtonResources()[i].getResource()) {
                return i;
            }
        }
        throw new IllegalArgumentException("Layout code not found: " + resourceCode);
    }

    protected abstract SquareButtonObject[] getButtonResources();

    public void setButtonVisibilities(){
        for(SquareButtonObject obj: getButtonResources()){
        setButtonVisibility(obj.getResource(), obj.isHidden());
        }
    }
}
