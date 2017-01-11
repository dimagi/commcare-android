package org.commcare.adapters;

import android.content.Context;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import org.commcare.dalvik.R;
import org.commcare.views.Combobox;
import org.commcare.views.widgets.SpinnerWidget;

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

    public abstract boolean isValidUserEntry(String enteredText);

    /**
     * @return Whether the text that a user can type into the corresponding combobox's edittext
     * field should be restricted in accordance with its adapter's filtering rules
     */
    public abstract boolean shouldRestrictTyping();

    public static ComboboxAdapter getAdapterForWidget(Context context, String[] choices,
                                                      Combobox.FilterType type, int fontSize) {
        choices = SpinnerWidget.getChoicesWithEmptyFirstSlot(choices);

        ComboboxAdapter adapter;
        switch(type) {
            case MULTI_WORD:
                adapter = new MultiWordComboboxAdapter(context, R.layout.custom_spinner_item, choices);
                break;
            case FUZZY:
                adapter = new FuzzyMatchComboboxAdapter(context, R.layout.custom_spinner_item, choices);
                break;
            case STANDARD:
            default:
                adapter = new StandardComboboxAdapter(context, R.layout.custom_spinner_item, choices);
        }

        adapter.setCustomTextSize(fontSize);
        return adapter;
    }

}