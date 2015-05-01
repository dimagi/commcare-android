/**
 * 
 */
package org.commcare.android.view;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.util.Log;
import android.view.Display;
import android.view.Menu;
import android.view.MenuItem;
import android.view.WindowManager;

import org.commcare.suite.model.graph.DisplayData;
import org.javarosa.core.reference.InvalidReferenceException;
import org.javarosa.core.reference.ReferenceManager;
import org.javarosa.core.services.locale.Localizer;
import org.odk.collect.android.utilities.FileUtils;

import java.io.File;

/**
 * Utilities for converting CommCare UI diplsay details into Android objects 
 * 
 * @author ctsims
 *
 */
public class ViewUtil {

    //This is silly and isn't really what we want here, but it's a start. (We'd like to be able to add
    //a displayunit to a menu in a super easy/straightforward way.
    public static void addDisplayToMenu(Context context, Menu menu, int menuId, DisplayData display) {
        MenuItem item = menu.add(0, menuId, menuId, Localizer.clearArguments(display.getName()).trim());
        if(display.getImageURI() != null){
            Bitmap b = ViewUtil.inflateDisplayImage(context, display.getImageURI());
            if(b != null) {
                item.setIcon(new BitmapDrawable(context.getResources(),b));
            }
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
}
