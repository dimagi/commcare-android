package org.commcare.adapters;

import android.content.Context;
import android.widget.Filter;

import org.commcare.views.widgets.SpinnerWidget;

import java.util.ArrayList;

/**
 * An implementation of ComboboxAdapter that permits a wider range of strings to be entered
 * into the combobox's edittext field. SEE implementation of  isValidUserEntry() for precise
 * definition of accepted strings.
 *
 * @author Aliza Stone
 */
public class PermissiveComboboxAdapter extends ComboboxAdapter {

    private final String[] allChoices;
    private String[] currentChoices;

    public PermissiveComboboxAdapter(final Context context, final int textViewResourceId,
                                     final String[] objects) {
        super(context, textViewResourceId, objects);
        allChoices = currentChoices = objects;
    }

    public static PermissiveComboboxAdapter AdapterWithBlankFirstChoice(Context context,
                                                                        int textViewResourceId,
                                                                        String[] objects) {
        objects = SpinnerWidget.getChoicesWithEmptyFirstSlot(objects);
        return new PermissiveComboboxAdapter(context, textViewResourceId, objects);
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

    /**
     *
     * @param choiceLowerCase - the answer choice to be considered, in all lower case
     * @param textEntered - the text entered by the user
     * @return true if choiceLowerCase contains any word within textEntered (the "words" of
     * textEntered are obtained by splitting textEntered on " ")
     */
    private boolean choiceShouldBeShown(String choiceLowerCase, CharSequence textEntered) {
        if ("".equals(textEntered) || textEntered == null) {
            return true;
        }
        String[] enteredTextIndividualWords = textEntered.toString().split(" ");
        for (String word : enteredTextIndividualWords) {
            if (!choiceLowerCase.contains(word.toLowerCase())) {
                return false;
            }
        }
        return true;
    }

}
