package org.commcare.adapters;

import android.content.Context;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Filter;
import android.widget.TextView;

import org.commcare.dalvik.R;
import org.javarosa.core.model.ComboboxFilterRule;
import org.javarosa.core.model.FuzzyMatchFilterRule;
import org.javarosa.core.model.MultiWordFilterRule;
import org.javarosa.core.model.StandardFilterRule;

import java.util.ArrayList;

/**
 * A custom adapter for use by a Combobox view. The filtering behavior of this adapter is determined
 * by the implementation of choiceShouldBeShown() in its ComboboxFilterRule, which defines whether
 * a given answer choice should be considered a match for the text entered by the user.
 *
 * @author Aliza Stone
 */
public class ComboboxAdapter extends ArrayAdapter<String> {

    private float customTextSize;
    protected final String[] allChoices;
    protected String[] currentChoices;
    protected ComboboxFilterRule filterRule;

    public ComboboxAdapter(final Context context, final String[] objects,
                           ComboboxFilterRule filterRule) {
        super(context, R.layout.custom_spinner_item, objects);
        allChoices = currentChoices = objects;
        this.customTextSize = -1;
        this.filterRule = filterRule;
    }

    /**
     * @param enteredText - the text entered by the user in the combobox's edittext field
     * @return Whether enteredText should be considered a viable entry, which is defined as
     * there being at least 1 answer option in the dropdown list when this string is entered.
     */
    public boolean isValidUserEntry(String enteredText) {
        for (String choice : allChoices) {
            if (filterRule.choiceShouldBeShown(choice, enteredText)) {
                return true;
            }
        }
        return false;
    }

    public boolean shouldRestrictTyping() {
        return filterRule.shouldRestrictTyping();
    }

    public void setCustomTextSize(float customTextSize) {
        this.customTextSize = customTextSize;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View view = super.getView(position, convertView, parent);
        TextView tv = view.findViewById(android.R.id.text1);
        if (customTextSize != -1) {
            tv.setTextSize(TypedValue.COMPLEX_UNIT_DIP, customTextSize);
        }
        tv.setPadding(10, 10, 10, 10);
        return view;
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
                    if (constraint == null || filterRule.choiceShouldBeShown(choice, constraint)) {
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
                currentChoices = (String[]) results.values;
                if (results.count > 0) {
                    notifyDataSetChanged();
                } else {
                    notifyDataSetInvalidated();
                }
            }
        };
    }

}