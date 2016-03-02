package org.commcare.views;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;

import org.commcare.dalvik.BuildConfig;
import org.commcare.suite.model.DisplayData;
import org.commcare.utils.MediaUtil;
import org.javarosa.core.services.locale.Localizer;

import java.util.LinkedList;

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
    @SuppressLint("NewApi")
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

    /**
     * Debug method to toast a view's ID whenever it is clicked.
     */
    public static void setClickListenersForEverything(Activity act) {
        if (BuildConfig.DEBUG) {
            final ViewGroup layout = (ViewGroup)act.findViewById(android.R.id.content);
            final LinkedList<View> views = new LinkedList<>();
            views.add(layout);
            for (int i = 0; !views.isEmpty(); i++) {
                final View child = views.getFirst();
                views.removeFirst();
                Log.i("GetID", "Adding onClickListener to view " + child);
                child.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(final View v) {
                        String vid;
                        try {
                            vid = "View id is: " + v.getResources().getResourceName(v.getId()) + " ( " + v.getId() + " )";
                        } catch (final Resources.NotFoundException excp) {
                            vid = "View id is: " + v.getId();
                        }
                        Log.i("CLK", vid);
                    }
                });
                if (child instanceof ViewGroup) {
                    final ViewGroup vg = (ViewGroup)child;
                    for (int j = 0; j < vg.getChildCount(); j++) {
                        final View gchild = vg.getChildAt(j);
                        if (!views.contains(gchild)) views.add(gchild);
                    }
                }
            }
        }
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
}
