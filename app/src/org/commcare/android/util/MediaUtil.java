package org.commcare.android.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.WindowManager;

import org.commcare.dalvik.application.CommCareApplication;
import org.javarosa.core.model.data.GeoPointData;
import org.javarosa.core.model.data.UncastData;
import org.javarosa.core.reference.InvalidReferenceException;
import org.javarosa.core.reference.ReferenceManager;
import org.odk.collect.android.utilities.FileUtils;
import org.odk.collect.android.widgets.ImageWidget;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * @author ctsims
 */
public class MediaUtil {

    private static final String KEY_USE_SMART_SCALING = "cc-use-smart-scaling";
    private static final String KEY_TARGET_DENSITY = "cc-target-density";
    private static final int DEFAULT_TARGET_DENSITY = DisplayMetrics.DENSITY_DEFAULT;

    private static final String TAG = MediaUtil.class.getSimpleName();

    public static final String FORM_VIDEO = "video";
    public static final String FORM_AUDIO = "audio";
    public static final String FORM_IMAGE = "image";

    /**
     * @return whether or not originalImage was scaled down according to maxDimen, and saved to
     * the location given by finalFilePath
     */
    public static boolean scaleAndSaveImage(File originalImage, String finalFilePath, int maxDimen) {
        String extension = FileUtils.getExtension(originalImage.getAbsolutePath());
        ImageWidget.ImageType type = ImageWidget.ImageType.fromExtension(extension);
        if (type == null) {
            // The selected image is not of a type that can be decoded to or from a bitmap
            Log.i(TAG, "Could not scale image " + originalImage.getAbsolutePath() + " due to incompatible extension");
            return false;
        }

        Bitmap bitmap = BitmapFactory.decodeFile(originalImage.getAbsolutePath());
        Bitmap scaledBitmap = getBitmapScaledByMaxDimen(bitmap, maxDimen, false);
        if (scaledBitmap != null) {
            // Write this scaled bitmap to the final file location
            FileOutputStream out = null;
            try {
                out = new FileOutputStream(finalFilePath);
                scaledBitmap.compress(type.getCompressFormat(), 100, out);
                return true;
            } catch (Exception e) {
                e.printStackTrace();
                return false;
            } finally {
                try {
                    if (out != null) {
                        out.close();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return false;
    }

    public static Bitmap getBitmapScaledByMaxDimen(InputStream stream, int maxDimen, boolean mustScaleWidth) {
        return getBitmapScaledByMaxDimen(BitmapFactory.decodeStream(stream), maxDimen, mustScaleWidth);
    }

    /**
     * Attempts to scale down an image file based on the max dimension given.
     *
     * @param mustScaleWidth - if true, the side of the image that we try to scale down is the width
     * @param maxDimen - the largest dimension that we want either side of the image to have
     *                (unless mustScaleWidth is true, and then applies to the width specifically)
     * @return A scaled down bitmap, or null if no scale-down is needed
     *
     * -If mustScaleWidth is false, employs the following logic: If at least one of the dimensions
     * of the original image exceeds the max dimension given, then make the larger side's
     * dimension equal to the max dimension, and scale down the smaller side such that the
     * original aspect ratio is maintained.
     * -If mustScaleWidth is true, employs the following logic: If the width exceeds the max
     * dimension given, set the width equal to that size, and then sale down the height such that
     * the original aspect ratio is maintained.
     *
     */
    private static Bitmap getBitmapScaledByMaxDimen(Bitmap originalBitmap, int maxDimen,
                                          boolean mustScaleWidth) {
        if (originalBitmap == null) {
            return null;
        }
        int height = originalBitmap.getHeight();
        int width = originalBitmap.getWidth();
        int sideToScale, otherSide;
        if (mustScaleWidth) {
            sideToScale = width;
            otherSide = height;
        } else {
            sideToScale = Math.max(height, width);
            otherSide = Math.min(height, width);
        }

        if (sideToScale > maxDimen) {
            // If the side to scale exceeds our max dimension, scale down accordingly
            double aspectRatio = ((double) otherSide) / sideToScale;
            sideToScale = maxDimen;
            otherSide = (int) Math.floor(maxDimen * aspectRatio);
            if (mustScaleWidth) {
                return Bitmap.createScaledBitmap(originalBitmap, sideToScale, otherSide, false);
            }
            if (width > height) {
                return Bitmap.createScaledBitmap(originalBitmap, sideToScale, otherSide, false);
            } else {
                return Bitmap.createScaledBitmap(originalBitmap, otherSide, sideToScale, false);
            }
        } else {
            return null;
        }
    }

    public static Bitmap getBitmapScaledToContainer(File f, int screenHeight, int screenWidth) {
        // Determine dimensions of original image
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(f.getAbsolutePath(), o);
        int imageHeight = o.outHeight;
        int imageWidth = o.outWidth;

        // Get a scale-down factor -- Powers of 2 work faster according to the docs, but we're
        // just doing closest size that still fills the screen
        int heightScale = Math.round((float) imageHeight / screenHeight);
        int widthScale = Math.round((float) imageWidth / screenWidth);
        int scale = Math.max(widthScale, heightScale);
        if (scale == 0) {
            // Rounding could possibly have resulted in a scale factor of 0, which is invalid
            scale = 1;
        }

        return performSafeScaleDown(f, scale, 0);
    }

    /**
     * Returns a scaled-down bitmap for the given image file, progressively increasing the
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

    private static Bitmap attemptScaleUp(File imageFile, int desiredHeight, int desiredWidth) {
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
     * @return A bitmap if one could be created. Null if there is an error or if the image is unavailable.
     */
    public static Bitmap inflateDisplayImage(Context context, String jrUri) {

        SharedPreferences prefs = CommCareApplication._().getCurrentApp().getAppPreferences();
        boolean useSmartImageScaling = prefs.getBoolean(KEY_USE_SMART_SCALING, true);
        if (useSmartImageScaling) {
            return inflateDisplayImage(context, jrUri,
                    prefs.getInt(KEY_TARGET_DENSITY, DEFAULT_TARGET_DENSITY));
        }

        if (jrUri != null && !jrUri.equals("")) {
            try {
                //TODO: Fallback for non-local refs? Write to a file first or something...
                String imageFilename = ReferenceManager._().DeriveReference(jrUri).getLocalURI();
                final File imageFile = new File(imageFilename);
                if (imageFile.exists()) {
                    Bitmap b;
                    Display display = ((WindowManager) context.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
                    int screenWidth = display.getWidth();
                    int screenHeight = display.getHeight();
                    b = getBitmapScaledToContainer(imageFile, screenHeight, screenWidth);
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

    private static Bitmap inflateDisplayImage(Context context, String jrUri, int targetDensity) {
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

                double scaleFactor = chooseScaleFactor(context, targetDensity);
                int newHeight = Math.round((float)(imageHeight * scaleFactor));
                int newWidth = Math.round((float)(imageWidth * scaleFactor));
                Log.i("10/15", "scaled height: " + newHeight + ", scaled width: " + newWidth);

                if (newHeight < imageHeight || newWidth < imageWidth) {
                    // scaling down
                    Display display = ((WindowManager)
                        context.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
                    int screenWidth = display.getWidth();
                    int screenHeight = display.getHeight();
                    if (newHeight > screenHeight || newWidth > screenWidth) {
                        // the screen dimens are even smaller than our new dimens, so scale to those
                        return getBitmapScaledToContainer(imageFile, screenHeight, screenWidth);
                    } else {
                        return getBitmapScaledToContainer(imageFile, newHeight, newWidth);
                    }
                } else {
                    // scaling up
                    return attemptScaleUp(imageFile, newHeight, newWidth);
                }
            }
        } catch (InvalidReferenceException e) {
            Log.e("ImageInflater", "image invalid reference exception for " + e.getReferenceString());
            e.printStackTrace();
        }
        return null;
    }

    private static double chooseScaleFactor(Context context, int targetDensity) {
        // Get native dp scale factor from Android
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        double nativeDpScaleFactor = metrics.density;
        Log.i("10/15", "native dp scale factor: " + nativeDpScaleFactor);

        // Get dpi scale factor
        final int SCREEN_DENSITY = metrics.densityDpi;
        Log.i("10/15", "Target dpi: " + targetDensity);
        Log.i("10/15", "This screen's dpi: " + SCREEN_DENSITY);
        double dpiScaleFactor = (double) SCREEN_DENSITY / targetDensity;
        Log.i("10/15", "dpi scale factor: " + dpiScaleFactor);

        double finalScaleFactor = (nativeDpScaleFactor + dpiScaleFactor) / 2;
        Log.i("10/15", "FINAL scale factor: " + finalScaleFactor);
        return finalScaleFactor;
    }


    /**
     * Pass in a string representing either a GeoPont or an address and get back a valid
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
