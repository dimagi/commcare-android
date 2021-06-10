package org.commcare.utils;

import android.graphics.Paint;
import android.graphics.Rect;

import org.commcare.core.graph.util.AbsStringExtension;

/**
 * @author $|-|!˅@M
 */
public class StringExtensionImpl implements AbsStringExtension {
    @Override
    public int getWidth(String text) {
        Paint paint = new Paint();
        paint.setTextSize(16);
        Rect rect = new Rect();
        paint.getTextBounds(text, 0, text.length(), rect);
        return rect.width();
    }
}
