package org.commcare.android.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.Pair;
import android.view.Display;
import android.view.WindowManager;

import org.commcare.dalvik.preferences.DeveloperPreferences;
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

    /**
     * Attempts to inflate an image from a CommCare UI definition source.
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
                return getBitmapScaledForNativeDensity(
                        context.getResources().getDisplayMetrics(), imageFile.getAbsolutePath(),
                        boundingHeight, boundingWidth,
                        DeveloperPreferences.getTargetInflationDensity());
            } else {
                // just scaling down if the original image is too big for its container
                return getBitmapScaledToContainer(imageFile.getAbsolutePath(), boundingHeight, boundingWidth);
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

    /**
     * Attempt to inflate an image source into a bitmap whose final dimensions are based upon
     * 2 factors:
     *
     * 1) The application of a scaling factor, which is derived from the relative values of the
     * target density declared by the app and the current device's actual density
     * 2) The absolute dimensions of the bounding container into which this image is being inflated
     * (may just be the screen dimens)
     *
     * @return the bitmap, or null if none could be created from the source
     */
    public static Bitmap getBitmapScaledForNativeDensity(DisplayMetrics metrics, String imageFilepath,
                                                         int containerHeight, int containerWidth,
                                                         int targetDensity) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        o.inScaled = false;
        BitmapFactory.decodeFile(imageFilepath, o);
        int imageHeight = o.outHeight;
        int imageWidth = o.outWidth;
        Log.i("10/15", "original height: " + imageHeight + ", original width: " + imageWidth);

        double scaleFactor = computeInflationScaleFactor(metrics, targetDensity);
        int calculatedHeight = Math.round((float)(imageHeight * scaleFactor));
        int calculatedWidth = Math.round((float)(imageWidth * scaleFactor));
        Log.i("10/15", "calculated height: " + calculatedHeight + ", calculated width: " + calculatedWidth);

        if (containerHeight < imageHeight || containerWidth < imageWidth || calculatedHeight < imageHeight) {
            // If either the container dimens or calculated dimens impose a smaller dimension,
            // scale down
            return getBitmapScaledDownExact(imageFilepath, imageHeight, imageWidth,
                    calculatedHeight, calculatedWidth, containerHeight, containerWidth);
        } else {
            return attemptBoundedScaleUp(imageFilepath, calculatedHeight, calculatedWidth,
                    containerHeight, containerWidth);
        }
    }

    /**
     * @return A bitmap representation of the given image file, scaled down such that the new
     * dimensions of the image is the SMALLER of the following 2 options:
     * 1) newCalcHeight and newCalcWidth
     * 2) the largest dimensions for which the original aspect ratio is maintained, without
     * exceeding either boundingWidth or boundingHeight
     *
     * If the aspect ratio given by newHeight and newWidth does not match the current aspect ratio
     * of the image, return null
     */
    private static Bitmap getBitmapScaledDownExact(String imageFilepath,
                                                   int originalHeight, int originalWidth,
                                                   int calcHeight, int calcWidth,
                                                   int boundingHeight, int boundingWidth) {
        int currentAspectRatio = originalWidth / originalHeight;
        int newAspectRatio = calcWidth / calcHeight;
        if (currentAspectRatio != newAspectRatio) {
            return null;
        }

        Pair<Integer, Integer> dimensImposedByContainer = getProportionalDimensForContainer(
                originalHeight, originalWidth, boundingHeight, boundingWidth);
        int newWidth = Math.min(dimensImposedByContainer.first, calcWidth);
        int newHeight = Math.min(dimensImposedByContainer.second, calcHeight);

        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inScaled = false;
            Bitmap originalBitmap = BitmapFactory.decodeFile(imageFilepath, o);
            return Bitmap.createScaledBitmap(originalBitmap, newWidth, newHeight, false);
        }
        catch (OutOfMemoryError e) {
            // OOM encountered trying to decode the bitmap at its current size, so we know we
            // need to scale down by some factor greater than 1
            int scaleFactor = originalHeight / newHeight;
            if (scaleFactor < 2) {
                scaleFactor = 2;
            }
            return performSafeScaleDown(imageFilepath, scaleFactor, 0);
        }
    }

    /**
     * @return A (width, height) pair representing the largest dimensions for which the aspect
     * ratio given by originalHeight and originalWidth is maintained, without exceeding
     * boundingHeight or boundingWidth
     */
    private static Pair<Integer, Integer> getProportionalDimensForContainer(int originalHeight,
                                                                            int originalWidth,
                                                                            int boundingHeight,
                                                                            int boundingWidth) {
        double heightScaleFactor = (double)boundingHeight / originalHeight;
        double widthScaleFactor =  (double)boundingWidth / originalWidth;
        double dominantScaleFactor = Math.min(heightScaleFactor, widthScaleFactor);

        int widthImposedByContainer = (int)Math.round(originalWidth * dominantScaleFactor);
        int heightImposedByContainer = (int)Math.round(originalHeight * dominantScaleFactor);
        return new Pair<>(widthImposedByContainer, heightImposedByContainer);
    }

    /**
     * @return A bitmap representation of the given image file, scaled up as close as possible to
     * desiredWidth and desiredHeight, without exceeding either boundingHeight or boundingWidth
     */
    private static Bitmap attemptBoundedScaleUp(String imageFilepath, int desiredHeight, int desiredWidth,
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
            Bitmap originalBitmap = BitmapFactory.decodeFile(imageFilepath, o);
            try {
                return Bitmap.createScaledBitmap(originalBitmap, desiredWidth, desiredHeight, false);
            } catch (OutOfMemoryError e) {
                return originalBitmap;
            }
        }
        catch (OutOfMemoryError e) {
            // Just inflating the image at its original size caused an OOM error, don't have a
            // choice but to scale down
            return performSafeScaleDown(imageFilepath, 2, 1);
        }
    }

    public static double computeInflationScaleFactor(DisplayMetrics metrics, int targetDensity) {
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
     * @return A bitmap representation of the given image file, potentially adjusted from the
     * image's original size such that its width is no larger than containerWidth, and its height
     * is no larger than containerHeight
     */
    private static Bitmap getBitmapScaledToContainer(String imageFilepath, int containerHeight,
                                                     int containerWidth) {
        Log.i("10/15", "scaling down to height " + containerHeight + " and width " + containerWidth);
        // Determine dimensions of original image
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(imageFilepath, o);
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

        return performSafeScaleDown(imageFilepath, scale, 0);
    }

    public static Bitmap getBitmapScaledToContainer(File imageFile, int containerHeight,
                                                    int containerWidth) {
        return getBitmapScaledToContainer(imageFile.getAbsolutePath(), containerHeight,
                containerWidth);
    }

    /**
     * @return A scaled-down bitmap for the given image file, progressively increasing the
     * scale-down factor by 1 until allocating memory for the bitmap does not cause an OOM error
     */
    private static Bitmap performSafeScaleDown(String imageFilepath, int scale, int depth) {
        if (depth == 5) {
            // Limit the number of recursive calls
            return null;
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = scale;
        try {
            return BitmapFactory.decodeFile(imageFilepath, options);
        } catch (OutOfMemoryError e) {
            return performSafeScaleDown(imageFilepath, scale + 1, depth + 1);
        }
    }

}
