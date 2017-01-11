package org.commcare.adapters;

import android.content.Context;

import org.commcare.views.widgets.SpinnerWidget;

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

    public static StandardComboboxAdapter AdapterWithBlankFirstChoice(Context context,
                                                                      int textViewResourceId,
                                                                      String[] objects) {
        objects = SpinnerWidget.getChoicesWithEmptyFirstSlot(objects);
        return new StandardComboboxAdapter(context, textViewResourceId, objects);
    }

    @Override
    public boolean isValidUserEntry(String enteredText) {
        return isPrefixOfSomeChoiceValue(enteredText);
    }

    /**
     * This is the logic that the default Filter for an AutoCompleteTextView employs, so this
     * is what the standard combobox adapter uses to determine if entered text is valid
     */
    private boolean isPrefixOfSomeChoiceValue(String text) {
        for (String choice : choicesAllLowerCase) {
            if (choice.startsWith(text.toLowerCase())) {
                return true;
            }
        }
        return false;
    }
}