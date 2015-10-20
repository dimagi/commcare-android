package org.commcare.android.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.WindowManager;

import org.commcare.dalvik.preferences.DeveloperPreferences;
import org.javarosa.core.model.data.GeoPointData;
import org.javarosa.core.model.data.UncastData;
import org.javarosa.core.reference.InvalidReferenceException;
import org.javarosa.core.reference.ReferenceManager;

import java.io.File;

/**
 * @author ctsims
 */
public class MediaUtil {

    public static final String FORM_VIDEO = "video";
    public static final String FORM_AUDIO = "audio";
    public static final String FORM_IMAGE = "image";

    public static Bitmap getBitmapScaledToContainer(File f, int containerHeight, int containerWidth) {
        Log.i("10/15", "scaling down to height " + containerHeight + " and width " + containerWidth);
        // Determine dimensions of original image
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(f.getAbsolutePath(), o);
        int imageHeight = o.outHeight;
        int imageWidth = o.outWidth;

        // Get a scale-down factor -- Powers of 2 work faster according to the docs, but we're
        // just doing closest size that still fills the screen
        int heightScale = Math.round((float) imageHeight / containerHeight);
        int widthScale = Math.round((float) imageWidth / containerWidth);
        int scale = Math.max(widthScale, heightScale);
        if (scale == 0) {
            // Rounding could possibly have resulted in a scale factor of 0, which is invalid
            scale = 1;
        }

        return performSafeScaleDown(f, scale, 0);
    }

    /**
     * @return A scaled-down bitmap for the given image file, progressively increasing the
     * scale-down factor by 1 until allocating memory for the bitmap does not cause an OOM error
     */
    private static Bitmap performSafeScaleDown(File f, int scale, int depth) {
        if (depth == 5) {
            // Limit the number of recursive calls
            return null;
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = scale;
        try {
            return BitmapFactory.decodeFile(f.getAbsolutePath(), options);
        } catch (OutOfMemoryError e) {
            return performSafeScaleDown(f, scale + 1, depth + 1);
        }
    }

    /**
     * @return A bitmap representation of the given image file, scaled up as close as possible to
     * desiredWidth and desiredHeight, without exceeding either boundingHeight or boundingWidth
     */
    private static Bitmap attemptBoundedScaleUp(File imageFile, int desiredHeight, int desiredWidth,
                                         int boundingHeight, int boundingWidth) {
        if (boundingHeight < desiredHeight || boundingWidth < desiredWidth) {
            float heightScale = ((float)boundingHeight) / desiredHeight;
            float widthScale = ((float)boundingWidth) / desiredWidth;
            float boundingScaleDownFactor = Math.min(heightScale, widthScale);
            desiredHeight = Math.round(desiredHeight * boundingScaleDownFactor);
            desiredWidth = Math.round(desiredWidth * boundingScaleDownFactor);
        }
        Log.i("10/15", "scaling up to height " + desiredHeight + " and width " + desiredWidth);
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inScaled = false;
            Bitmap originalBitmap = BitmapFactory.decodeFile(imageFile.getAbsolutePath(), o);
            try {
                return Bitmap.createScaledBitmap(originalBitmap, desiredWidth, desiredHeight, false);
            } catch (OutOfMemoryError e) {
                return originalBitmap;
            }
        }
        catch (OutOfMemoryError e) {
            // Just inflating the image at its original size caused an OOM error, don't have a
            // choice but to scale down
            return performSafeScaleDown(imageFile, 2, 1);
        }
    }

    /**
     * Attempts to inflate an image from a <display> or other CommCare UI definition source.
     *
     * @param jrUri The image to inflate
     * @param boundingWidth the width of the container this image is being inflated into, to serve
     *                      as a max width. If passed in as -1, gets set to screen width
     * @param boundingHeight the height fo the container this image is being inflated into, to
     *                       serve as a max height. If passed in as -1, gets set to screen height
     * @return A bitmap if one could be created. Null if error occurs or the image is unavailable.
     */
    public static Bitmap inflateDisplayImage(Context context, String jrUri,
                                             int boundingWidth, int boundingHeight) {
        if (jrUri == null || jrUri.equals("")) {
            return null;
        }
        try {
            String imageFilename = ReferenceManager._().DeriveReference(jrUri).getLocalURI();
            final File imageFile = new File(imageFilename);
            if (!imageFile.exists()) {
                return null;
            }

            Display display = ((WindowManager)
                    context.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
            if (boundingHeight == -1) {
                boundingHeight = display.getHeight();
            }
            if (boundingWidth == -1) {
                boundingWidth = display.getWidth();
            }
            Log.i("10/15", "bounding height: " + boundingHeight + ", bounding width: " + boundingWidth);

            if (DeveloperPreferences.isSmartInflationEnabled()) {
                // scale based on native density AND bounding dimens
                return scaleForNativeDensity(context, jrUri, boundingHeight, boundingWidth,
                        DeveloperPreferences.getTargetInflationDensity());
            } else {
                // just scaling down if the original image is too big for its container
                return getBitmapScaledToContainer(imageFile, boundingHeight, boundingWidth);
            }
        } catch (InvalidReferenceException e) {
            Log.e("ImageInflater", "image invalid reference exception for " + e.getReferenceString());
            e.printStackTrace();
        }
        return null;
    }

    public static Bitmap inflateDisplayImage(Context context, String jrUri) {
        return inflateDisplayImage(context, jrUri, -1, -1);
    }

    private static Bitmap scaleForNativeDensity(Context context, String jrUri, int containerHeight,
                                              int containerWidth, int targetDensity) {
        if (jrUri == null || jrUri.equals("")) {
            return null;
        }
        try {
            String imageFilename = ReferenceManager._().DeriveReference(jrUri).getLocalURI();
            final File imageFile = new File(imageFilename);
            if (imageFile.exists()) {
                Log.i("10/15", "src path: " + imageFilename);

                BitmapFactory.Options o = new BitmapFactory.Options();
                o.inJustDecodeBounds = true;
                o.inScaled = false;
                BitmapFactory.decodeFile(imageFile.getAbsolutePath(), o);
                int imageHeight = o.outHeight;
                int imageWidth = o.outWidth;
                Log.i("10/15", "original height: " + imageHeight + ", original width: " + imageWidth);

                double scaleFactor = computeScaleFactor(context, targetDensity);
                int calculatedHeight = Math.round((float)(imageHeight * scaleFactor));
                int calculatedWidth = Math.round((float)(imageWidth * scaleFactor));
                Log.i("10/15", "calculated height: " + calculatedHeight + ", calculated width: " + calculatedWidth);

                int boundingHeight = Math.min(containerHeight, calculatedHeight);
                int boundingWidth = Math.min(containerWidth, calculatedWidth);

                if (boundingHeight < imageHeight || boundingWidth < imageWidth) {
                    // scaling down
                    return getBitmapScaledToContainer(imageFile, boundingHeight, boundingWidth);
                } else {
                    // scaling up
                    return attemptBoundedScaleUp(imageFile, calculatedHeight, calculatedWidth,
                            containerHeight, containerWidth);
                }
            }
        } catch (InvalidReferenceException e) {
            Log.e("ImageInflater", "image invalid reference exception for " + e.getReferenceString());
            e.printStackTrace();
        }
        return null;
    }

    private static double computeScaleFactor(Context context, int targetDensity) {
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        final int SCREEN_DENSITY = metrics.densityDpi;

        double actualNativeScaleFactor = metrics.density;
        // The formula below is what Android *usually* uses to compute the value of metrics.density
        // for a device. If this is in fact the value being used, we are not interested in it.
        // However, if the actual value differs at all from the standard calculation, it means
        // Android is taking other factors into consideration (such as straight up screen size),
        // and we want to incorporate that proportionally into our own version of the scale factor
        double standardNativeScaleFactor = (double)SCREEN_DENSITY / DisplayMetrics.DENSITY_DEFAULT;
        double proportionalAdjustmentFactor = 1;
        if (actualNativeScaleFactor > standardNativeScaleFactor) {
            proportionalAdjustmentFactor = 1 +
                    ((actualNativeScaleFactor - standardNativeScaleFactor) / standardNativeScaleFactor);
        } else if (actualNativeScaleFactor < standardNativeScaleFactor) {
            proportionalAdjustmentFactor = actualNativeScaleFactor / standardNativeScaleFactor;
        }
        Log.i("10/15", "proportional adjustment factor: " + proportionalAdjustmentFactor);

        // Get our custom scale factor, based on this device's density and what the image's target
        // density was
        Log.i("10/15", "Target dpi: " + targetDensity);
        Log.i("10/15", "This screen's dpi: " + SCREEN_DENSITY);
        double customDpiScaleFactor = (double)SCREEN_DENSITY / targetDensity;
        Log.i("10/15", "dpi scale factor: " + customDpiScaleFactor);

        Log.i("10/15", "FINAL scale factor: " + (customDpiScaleFactor * proportionalAdjustmentFactor));
        return customDpiScaleFactor * proportionalAdjustmentFactor;
    }


    /**
     * Pass in a string representing either a GeoPoint or an address and get back a valid
     * GeoURI that can be passed as an intent argument 
     */
    public static String getGeoIntentURI(String rawInput){
        try {
            GeoPointData mGeoPointData = new GeoPointData().cast(new UncastData(rawInput));
            String latitude = Double.toString(mGeoPointData.getValue()[0]);
            String longitude= Double.toString(mGeoPointData.getValue()[1]);
            return "geo:" + latitude + "," + longitude + "?q=" + latitude + "," + longitude;
            
        } catch(IllegalArgumentException iae){
            return "geo:0,0?q=" + rawInput;
        }
    }
}
