package org.commcare.adapters;

import android.content.Context;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import org.commcare.dalvik.R;

/**
 * A custom adapter for use by a Combobox view. Implementations of ComboboxAdapter require a
 * custom definition for choiceShouldBeShown(), which defines whether a given answer choice
 * should be considered a match for the text entered by the user.
 *
 * @author Aliza Stone
 */
public abstract class ComboboxAdapter extends ArrayAdapter<String> {

    private float customTextSize;
    protected final String[] allChoices;

    public static ComboboxAdapter getAdapterForFilterType(Context context,
                                                          String[] choices,
                                                          FilterType type) {
        switch(type) {
            case MULTI_WORD:
                return new MultiWordComboboxAdapter(context, R.layout.custom_spinner_item, choices);
            case FUZZY:
                return new FuzzyMatchComboboxAdapter(context, R.layout.custom_spinner_item, choices);
            case STANDARD:
            default:
                return new StandardComboboxAdapter(context, R.layout.custom_spinner_item, choices);
        }
    }

    public ComboboxAdapter(final Context context, final int textViewResourceId,
                           final String[] objects) {
        super(context, textViewResourceId, objects);
        allChoices = objects;
        this.customTextSize = -1;
    }

    /**
     * @param enteredText - the text entered by the user in the combobox's edittext field
     * @return Whether enteredText should be considered a viable entry, which is defined as
     * there being at least 1 answer option in the dropdown list when this string is entered.
     */
    public boolean isValidUserEntry(String enteredText) {
        for (String choice : allChoices) {
            if (choiceShouldBeShown(choice, enteredText)) {
                return true;
            }
        }
        return false;
    }

    /**
     *
     * @param choice - an answer choice available in this adapter
     * @param textEntered - the text entered by the user in the combobox's edittext field
     * @return If the given choice should be displayed in combobox's dropdown menu, based upon
     * the text that the user currently has entered
     */
    public abstract boolean choiceShouldBeShown(String choice, CharSequence textEntered);

    /**
     * @return Whether the text that a user can type into the corresponding combobox's edittext
     * field should be restricted in accordance with its adapter's filtering rules
     */
    public abstract boolean shouldRestrictTyping();

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

    public enum FilterType {
        STANDARD, MULTI_WORD, FUZZY
    }

}