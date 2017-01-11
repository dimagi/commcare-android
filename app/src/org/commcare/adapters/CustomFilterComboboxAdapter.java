package org.commcare.adapters;

import android.content.Context;
import android.widget.Filter;

import java.util.ArrayList;

/**
 * Base class for any combobox adapter that implements a custom filter
 *
 * @author Aliza Stone
 */
public abstract class CustomFilterComboboxAdapter extends ComboboxAdapter {

    protected String[] currentChoices;

    public CustomFilterComboboxAdapter(final Context context, final int textViewResourceId,
                           final String[] objects) {
        super(context, textViewResourceId, objects);
        currentChoices = allChoices;
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

}
