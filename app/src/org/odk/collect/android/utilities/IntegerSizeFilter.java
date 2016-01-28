package org.odk.collect.android.utilities;

import android.text.InputFilter;
import android.text.Spanned;

/**
 * @author amstone326
 *
 *         An InputFilter that limits the characters a user can enter,
 *         based on whether or not they represent a valid integer in Java.
 *         Used by IntegerWidget.java.
 */

public class IntegerSizeFilter implements InputFilter {

    @Override
    public CharSequence filter(CharSequence source, int start, int end,
                               Spanned dest, int dstart, int dend) {
        String destString = dest.toString();
        if (source.equals("") || destString.equals("")) {
            return null; //If the source or destination strings are empty, can leave as is
        }
        String part1 = destString.substring(0, dstart);
        String part2 = destString.substring(dend);
        String newString = part1 + source.subSequence(start, end).toString() + part2;

        try {
            Integer x = Integer.parseInt(newString);
            return null; //keep original
        } catch (NumberFormatException e) {
            return ""; //don't allow edit that was just made
        }
    }

}
