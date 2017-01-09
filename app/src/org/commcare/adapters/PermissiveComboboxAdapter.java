package org.commcare.adapters;

import android.content.Context;
import android.widget.Filter;

import java.util.ArrayList;

/**
 * Created by amstone326 on 1/8/17.
 */
public class PermissiveComboboxAdapter extends ComboboxAdapter {

    private final String[] allChoices;
    private String[] currentChoices;

    public PermissiveComboboxAdapter(final Context context, final int textViewResourceId,
                                     final String[] objects, float textSize) {
        super(context, textViewResourceId, objects, textSize);
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
                    if (choiceShouldBeShown(choice.toLowerCase(), constraint)) {
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

    @Override
    public boolean isValidUserEntry(String enteredText) {
        for (String choice : allChoices) {
            if (choiceShouldBeShown(choice.toLowerCase(), enteredText)) {
                return true;
            }
        }
        return false;
    }

    private boolean choiceShouldBeShown(String choiceLowerCase, CharSequence textEntered) {
        String[] enteredTextIndividualWords = textEntered.toString().split(" ");
        for (String word : enteredTextIndividualWords) {
            if (!choiceLowerCase.contains(word.toLowerCase())) {
                return false;
            }
        }
        return true;
    }

}
