package org.commcare.adapters;

import android.content.Context;

import java.util.Vector;

public class StandardComboboxAdapter extends ComboboxAdapter {

    private Vector<String> choicesAllLowerCase;

    public StandardComboboxAdapter(final Context context, final int textViewResourceId,
                                   final String[] objects) {
        super(context, textViewResourceId, objects);
        this.choicesAllLowerCase = new Vector<>();
        for (String s : objects) {
            choicesAllLowerCase.add(s.toLowerCase());
        }
    }

    @Override
    public boolean shouldRestrictTyping() {
        return true;
    }

    /**
     * This is the logic that the default Filter for an AutoCompleteTextView employs
     */
    @Override
    public boolean choiceShouldBeShown(String choice, CharSequence textEntered) {
        return choice.toLowerCase().startsWith(textEntered.toString().toLowerCase());
    }
}