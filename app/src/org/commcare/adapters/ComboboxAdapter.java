package org.commcare.adapters;

import android.content.Context;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

/**
 *
 * @author Aliza Stone
 */
public abstract class ComboboxAdapter extends ArrayAdapter<String> {
    final float textSize;

    public ComboboxAdapter(final Context context, final int textViewResourceId,
                           final String[] objects, float textSize) {
        super(context, textViewResourceId, objects);
        this.textSize = textSize;
    }

    @Override
    // Defines the text view parameters for the drop down list entries
    public View getView(int position, View convertView, ViewGroup parent) {
        View view = super.getView(position, convertView, parent);
        TextView tv = (TextView)view.findViewById(android.R.id.text1);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_DIP, textSize);
        tv.setPadding(10, 10, 10, 10);
        return view;
    }

    /**
     *
     * @param enteredText
     * @return Whether the given text entered by the user should be considered a viable entry,
     * which is defined as there being at least 1 answer option in the dropdown list when this
     * string is entered
     */
    public abstract boolean isValidUserEntry(String enteredText);

}