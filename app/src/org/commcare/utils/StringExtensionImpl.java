package org.commcare.utils;

import org.commcare.core.graph.util.AbsStringExtension;

/**
 * @author $|-|!˅@M
 */
public class StringExtensionImpl implements AbsStringExtension {
    @Override
    public int getWidth(String text) {
        // Tried https://c3js.org/samples/axes_x_tick_rotate.html.
        // A string of length 1, needs 45 height to not overlap
        // And for every additional character, height needs an increase in +5 to not overlap.

        // We have put a cap at 15 characters. See graph.max.js line - 95
        if (text.length() > 15) {
            return 115;
        } else {
            return 40 + 5 * text.length();
        }
    }
}
