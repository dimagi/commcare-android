package org.commcare.views;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.support.v4.util.Pair;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;

import org.commcare.suite.model.DisplayData;
import org.commcare.utils.MediaUtil;
import org.javarosa.core.services.locale.Localizer;

import java.util.ArrayList;

/**
 * Utilities for converting CommCare UI diplsay details into Android objects
 *
 * @author ctsims
 */
public final class ViewUtil {

    // This is silly and isn't really what we want here, but it's a start.
    // (We'd like to be able to add a displayunit to a menu in a super
    // easy/straightforward way.
    public static void addDisplayToMenu(Context context, Menu menu,
                                        int menuId, int menuGroupId, DisplayData display) {
        MenuItem item = menu.add(menuGroupId, menuId, menuId,
                Localizer.clearArguments(display.getName()).trim());
        if (display.getImageURI() != null) {
            Bitmap b = MediaUtil.inflateDisplayImage(context, display.getImageURI());
            if (b != null) {
                item.setIcon(new BitmapDrawable(context.getResources(), b));
            }
        }
    }

    public static void hideVirtualKeyboard(Activity activity) {
        InputMethodManager inputManager = (InputMethodManager)activity.getSystemService(Context.INPUT_METHOD_SERVICE);

        View focus = activity.getCurrentFocus();
        if (focus != null) {
            inputManager.hideSoftInputFromWindow(focus.getWindowToken(),
                    InputMethodManager.HIDE_NOT_ALWAYS);
        }
    }

    /**
     * Sets the background on a view to the provided drawable while retaining the padding
     * of the original view (regardless of whether the provided drawable has its own padding)
     *
     * @param v          The view whose background will be updated
     * @param background A background drawable (can be null to clear the background)
     */
    public static void setBackgroundRetainPadding(View v, Drawable background) {
        //Need to transplant the padding due to background affecting it
        int[] padding = {v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(), v.getPaddingBottom()};

        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.JELLY_BEAN) {
            v.setBackground(background);
        } else {
            v.setBackgroundDrawable(background);
        }
        v.setPadding(padding[0], padding[1], padding[2], padding[3]);
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public static int getColorDrawableColor(ColorDrawable drawable) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB) {
            Bitmap bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_4444);
            Canvas canvas = new Canvas(bitmap);
            drawable.draw(canvas);
            int pix = bitmap.getPixel(0, 0);
            bitmap.recycle();
            return pix;
        } else {
            return drawable.getColor();
        }
    }


    /**
     * Determine width of each child view, based on mHints, the suite's size hints.
     * mHints contains a width hint for each child view, each one of
     * - A string like "50%", requesting the field take up 50% of the row
     * - A string like "200", requesting the field take up 200 pixels
     * - Null, not specifying a width for the field
     * This function will parcel out requested widths and divide remaining space among unspecified columns.
     *
     * @param fullSize Width, in pixels, of the containing row.
     * @return Array of integers, each corresponding to a child view,
     * representing the desired width, in pixels, of that view.
     */
    public static int[] calculateColumnWidths(ArrayList<String> hints, int fullSize) {
        // Convert any percentages to pixels. Percentage columns are treated
        // as percentage of the entire screen width.
        int[] widths = new int[hints.size()];
        parseWidths(hints, widths, fullSize);

        Pair<Integer, Integer> constraints = buildConstraints(widths);
        int claimedSpace = constraints.first;
        int indeterminateColumns = constraints.second;

        if (widthReadjustmentNeeded(fullSize, claimedSpace, indeterminateColumns)) {
            readjustWidths(widths, fullSize, claimedSpace, indeterminateColumns);
        } else if (indeterminateColumns > 0) {
            divideIndeterminateSpace(widths, fullSize, claimedSpace, indeterminateColumns);
        }

        return widths;
    }

    private static void parseWidths(ArrayList<String> hints, int[] widths, int fullSize) {
        int hintIndex = 0;
        for (String hint : hints) {
            if (hint == null) {
                widths[hintIndex] = -1;
            } else if (hint.contains("%")) {
                String percentString = hint.substring(0, hint.indexOf("%"));
                widths[hintIndex] = fullSize * Integer.parseInt(percentString) / 100;
            } else {
                widths[hintIndex] = Integer.parseInt(hint);
            }
            hintIndex++;
        }
    }

    private static Pair<Integer, Integer> buildConstraints(int[] widths) {
        int claimedSpace = 0;
        int indeterminateColumns = 0;
        for (int width : widths) {
            if (width != -1) {
                claimedSpace += width;
            } else {
                indeterminateColumns++;
            }
        }
        return new Pair<>(claimedSpace, indeterminateColumns);
    }

    /**
     * Either more space has been claimed than the screen has room for, or the
     * full width isn't spoken for and there are no indeterminate columns.
     */
    private static boolean widthReadjustmentNeeded(int fullSize, int claimedSpace,
                                                   int indeterminateColumns) {
        return (fullSize < claimedSpace + indeterminateColumns)
                || (fullSize > claimedSpace && indeterminateColumns == 0);
    }

    private static void readjustWidths(int[] widths, int fullSize,
                                       int claimedSpace,
                                       int indeterminateColumns) {
        claimedSpace += indeterminateColumns;
        for (int i = 0; i < widths.length; i++) {
            if (widths[i] == -1) {
                // Assign indeterminate columns a real width.
                // It's arbitrary and tiny, but this is going to look terrible regardless.
                widths[i] = 1;
            } else {
                // Shrink or expand columns proportionally
                widths[i] = fullSize * widths[i] / claimedSpace;
            }
        }
    }

    private static void divideIndeterminateSpace(int[] widths, int fullSize,
                                                 int claimedSpace,
                                                 int indeterminateColumns) {
        int defaultWidth = (fullSize - claimedSpace) / indeterminateColumns;
        for (int i = 0; i < widths.length; i++) {
            if (widths[i] == -1) {
                widths[i] = defaultWidth;
            }
        }
    }
}
