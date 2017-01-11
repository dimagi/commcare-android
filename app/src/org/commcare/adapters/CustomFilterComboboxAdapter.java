package org.commcare.adapters;

import android.content.Context;
import android.widget.Filter;

import java.util.ArrayList;

/**
 * Created by amstone326 on 1/11/17.
 */

public abstract class CustomFilterComboboxAdapter extends ComboboxAdapter {

    protected final String[] allChoices;
    protected String[] currentChoices;

    public CustomFilterComboboxAdapter(final Context context, final int textViewResourceId,
                           final String[] objects) {
        super(context, textViewResourceId, objects);
        allChoices = currentChoices = objects;
    }

    @Override
    public int getCount() {
        return currentChoices.length;
    }

    @Override
    public String getItem(int position) {
        return currentChoices[position];
    }

    @Override
    public Filter getFilter() {
        return new Filter() {
            @Override
            protected FilterResults performFiltering(CharSequence constraint) {
                ArrayList<String> matched = new ArrayList<>();
                for (String choice : allChoices) {
                    if (choiceShouldBeShown(choice, constraint)) {
                        matched.add(choice);
                    }
                }

                FilterResults results = new FilterResults();
                results.values = matched.toArray(new String[]{});
                results.count = matched.size();
                return results;
            }

            @Override
            protected void publishResults(CharSequence constraint, FilterResults results) {
                currentChoices = (String[])results.values;
                if (results.count > 0) {
                    notifyDataSetChanged();
                } else {
                    notifyDataSetInvalidated();
                }
            }
        };
    }

    /**
     * @param enteredText - the text entered by the user in the combobox's edittext field
     * @return Whether enteredText should be considered a viable entry, which is defined as
     * there being at least 1 answer option in the dropdown list when this string is entered.
     */
    @Override
    public boolean isValidUserEntry(String enteredText) {
        for (String choice : allChoices) {
            if (choiceShouldBeShown(choice, enteredText)) {
                return true;
            }
        }
        return false;
    }

    public abstract boolean choiceShouldBeShown(String choice, CharSequence textEntered);
}
