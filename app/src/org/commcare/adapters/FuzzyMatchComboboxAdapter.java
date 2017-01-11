package org.commcare.adapters;

import android.content.Context;

import org.commcare.utils.StringUtils;

/**
 * An implementation of ComboboxAdapter whose filter accepts answer choice strings based on either
 * direct or fuzzy matches to the entered text
 *
 * @author Aliza Stone
 */

public class FuzzyMatchComboboxAdapter extends CustomFilterComboboxAdapter {

    public FuzzyMatchComboboxAdapter(final Context context, final int textViewResourceId,
                                     final String[] objects) {
        super(context, textViewResourceId, objects);
    }

    @Override
    public boolean shouldRestrictTyping() {
        // Since fuzzy match only works once the number of typed letters reaches a certain
        // threshold and is close to the number of letters in the comparison string, it doesn't
        // make any sense to restrict typing here
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
        if (isSubstringOrFuzzyMatch(choiceLowerCase, textEnteredLowerCase)) {
            return true;
        }

        return allEnteredWordsHaveMatchOrFuzzyMatch(choiceLowerCase, textEnteredLowerCase);
    }

    private static boolean allEnteredWordsHaveMatchOrFuzzyMatch(String choiceLowerCase,
                                                                String textEnteredLowerCase) {
        String[] enteredWords = textEnteredLowerCase.split(" ");
        String[] wordsFromChoice = choiceLowerCase.split(" ");
        for (String enteredWord : enteredWords) {
            boolean foundMatchForWord = false;
            for (String wordFromChoice : wordsFromChoice) {
                if (isSubstringOrFuzzyMatch(wordFromChoice, enteredWord)) {
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

    private static boolean isSubstringOrFuzzyMatch(String choiceLowerCase,
                                                   String textEnteredLowerCase) {
        return choiceLowerCase.contains(textEnteredLowerCase) ||
                StringUtils.fuzzyMatch(textEnteredLowerCase, choiceLowerCase).first;
    }

}
