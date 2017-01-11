package org.commcare.adapters;

import android.content.Context;

import org.commcare.utils.StringUtils;

/**
 * Created by amstone326 on 1/11/17.
 */

public class FuzzyMatchComboboxAdapter extends CustomFilterComboboxAdapter {

    public FuzzyMatchComboboxAdapter(final Context context, final int textViewResourceId,
                                     final String[] objects) {
        super(context, textViewResourceId, objects);
    }

    @Override
    public boolean shouldRestrictTyping() {
        return false;
    }

    @Override
    public boolean choiceShouldBeShown(String choice, CharSequence textEntered) {
        if ("".equals(textEntered) || textEntered == null) {
            return true;
        }

        String textEnteredLowerCase = textEntered.toString().toLowerCase();
        String choiceLowerCase = choice.toLowerCase();

        // Try the easy cases first
        if (choiceLowerCase.startsWith(textEnteredLowerCase) ||
                StringUtils.fuzzyMatch(textEnteredLowerCase, choiceLowerCase).first) {
            return true;
        }

        return allEnteredWordsHaveFuzzyMatch(choiceLowerCase, textEnteredLowerCase);
    }

    private static boolean allEnteredWordsHaveFuzzyMatch(String choiceLowerCase,
                                                  String textEnteredLowerCase) {
        String[] enteredWords = textEnteredLowerCase.split(" ");
        String[] wordsFromChoice = choiceLowerCase.split(" ");
        for (String enteredWord : enteredWords) {
            boolean foundMatchForWord = false;
            for (String wordFromChoice : wordsFromChoice) {
                if (StringUtils.fuzzyMatch(enteredWord, wordFromChoice).first) {
                    foundMatchForWord = true;
                    break;
                }
            }
            if (!foundMatchForWord) {
                return false;
            }
        }

        return true;
    }

}
