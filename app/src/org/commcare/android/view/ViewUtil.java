/**
 * 
 */
package org.commcare.android.view;

import java.io.File;
import org.commcare.dalvik.R;
import org.commcare.suite.model.DisplayUnit;
import org.javarosa.core.reference.InvalidReferenceException;
import org.javarosa.core.reference.ReferenceManager;
import org.javarosa.core.services.locale.Localizer;
import org.odk.collect.android.utilities.FileUtils;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.Log;
import android.view.Display;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;

/**
 * Utilities for converting CommCare UI diplsay details into Android objects 
 * 
 * @author ctsims
 *
 */
public class ViewUtil {

    //This is silly and isn't really what we want here, but it's a start. (We'd like to be able to add
    //a displayunit to a menu in a super easy/straightforward way.
    public static void addDisplayToMenu(Context context, Menu menu, int menuId, DisplayUnit display) {
        Bitmap b = ViewUtil.inflateDisplayImage(context, display.getImageURI().evaluate());
        MenuItem item = menu.add(0, menuId, menuId, Localizer.clearArguments(display.getText().evaluate()).trim());
        if(b != null) {
            item.setIcon(new BitmapDrawable(context.getResources(),b));
        }
    }

    //ctsims 5/23/2014
    //NOTE: I pretty much extracted the below straight from the TextImageAudioView. It's
    //not great and doesn't scale resources well. Feel free to split back up. 
    
    /**
     * Attempts to inflate an image from a <display> or other CommCare UI definition source.
     *  
     * @param context 
     * @param jrUri The image to inflate
     * @return A bitmap if one could be created. Null if there is an error or if the image is unavailable.
     */
    public static Bitmap inflateDisplayImage(Context context, String jrUri) {
        //TODO: Cache?
        
        // Now set up the image view
        if (jrUri != null && !jrUri.equals("")) {
            try {
                //TODO: Fallback for non-local refs? Write to a file first or something...
                String imageFilename = ReferenceManager._().DeriveReference(jrUri).getLocalURI();
                final File imageFile = new File(imageFilename);
                if (imageFile.exists()) {
                    Bitmap b = null;
                    try {
                        Display display = ((WindowManager)context.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
                        int screenWidth = display.getWidth();
                        int screenHeight = display.getHeight();
                        b = FileUtils.getBitmapScaledToDisplay(imageFile, screenHeight, screenWidth);
                    } catch (OutOfMemoryError e) {
                        Log.w("ImageInflater", "File too large to function on local device");
                    }

                    if (b != null) {
                        return b;
                    }
                }

            } catch (InvalidReferenceException e) {
                Log.e("ImageInflater", "image invalid reference exception for " + e.getReferenceString());
                e.printStackTrace();
            }
        }
        return null;
    }

    public static void hideVirtualKeyboard(Activity activity){
        InputMethodManager inputManager = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);

        View focus = activity.getCurrentFocus();
        if(focus != null) {
            inputManager.hideSoftInputFromWindow(focus.getWindowToken(),
                    InputMethodManager.HIDE_NOT_ALWAYS);
        }
    }
    
    /**
     * Sets the background on a view to the provided drawable while retaining the padding
     * of the original view (regardless of whether the provided drawable has its own padding)
     * 
     * @param v The view whose background will be updated
     * @param background A background drawable (can be null to clear the background)
     */
    @SuppressLint("NewApi")
    public static void setBackgroundRetainPadding(View v, Drawable background) {
        //Need to transplant the padding due to background affecting it
        int[] padding = {v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(),v.getPaddingBottom() };
        
        if(Build.VERSION.SDK_INT > Build.VERSION_CODES.JELLY_BEAN) {
            v.setBackground(background);
        } else {
            v.setBackgroundDrawable(background);
        }
        v.setPadding(padding[0],padding[1], padding[2], padding[3]);
    }
}
