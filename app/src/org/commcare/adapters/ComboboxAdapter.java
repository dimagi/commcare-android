package org.commcare.adapters;

import android.content.Context;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import org.commcare.dalvik.R;

import java.util.Vector;

/**
 * A custom adapter for use by a Combobox view. Implementations of ComboboxAdapter require a
 * custom definition for isValidUserEntry(), which defines what strings can be entered into the
 * associated combobox's edittext field.
 *
 * @author Aliza Stone
 */
public abstract class ComboboxAdapter extends ArrayAdapter<String> {

    private float customTextSize;

    public ComboboxAdapter(final Context context, final int textViewResourceId,
                           final String[] objects) {
        super(context, textViewResourceId, objects);
        this.customTextSize = -1;
    }

    public void setCustomTextSize(float customTextSize) {
        this.customTextSize = customTextSize;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View view = super.getView(position, convertView, parent);
        TextView tv = (TextView)view.findViewById(android.R.id.text1);
        if (customTextSize != -1) {
            tv.setTextSize(TypedValue.COMPLEX_UNIT_DIP, customTextSize);
        }
        tv.setPadding(10, 10, 10, 10);
        return view;
    }

    /**
     * @param enteredText - the text entered by the user in the combobox's edittext field
     * @return Whether enteredText should be considered a viable entry, which is defined as
     * there being at least 1 answer option in the dropdown list when this string is entered.
     */
    public abstract boolean isValidUserEntry(String enteredText);

    public static ComboboxAdapter getAdapterForWidget(Context context, String[] choices,
                                                      boolean permissive, int fontSize) {
        ComboboxAdapter adapter;
        if (permissive) {
            adapter = PermissiveComboboxAdapter.AdapterWithBlankFirstChoice(context,
                    R.layout.custom_spinner_item, choices);
        } else {
            adapter = StandardComboboxAdapter.AdapterWithBlankFirstChoice(context,
                    R.layout.custom_spinner_item, choices);
        }
        adapter.setCustomTextSize(fontSize);
        return adapter;
    }

}