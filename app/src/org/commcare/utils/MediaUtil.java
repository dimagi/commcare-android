package org.commcare.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.Pair;
import android.view.WindowManager;

import org.commcare.engine.references.JavaFileReference;
import org.commcare.logging.AndroidLogger;
import org.commcare.preferences.CommCarePreferences;
import org.javarosa.core.reference.InvalidReferenceException;
import org.javarosa.core.reference.Reference;
import org.javarosa.core.reference.ReferenceManager;
import org.javarosa.core.services.Logger;

import java.io.File;
import java.io.IOException;

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
     * @param jrUri          The image to inflate
     * @param boundingWidth  the width of the container this image is being inflated into, to serve
     *                       as a max width. If passed in as -1, gets set to screen width
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
            Reference ref = ReferenceManager._().DeriveReference(jrUri);
            try {
                if (!ref.doesBinaryExist()) {
                    return null;
                }
                if (!(ref instanceof JavaFileReference)) {
                    return BitmapFactory.decodeStream(ref.getStream());
                }
            } catch (IOException e) {
                Logger.log(AndroidLogger.TYPE_ERROR_ASSERTION, "IO Exception loading reference: " + jrUri);
                return null;
            }

            String imageFilename = ref.getLocalURI();
            final File imageFile = new File(imageFilename);
            if (!imageFile.exists()) {
                return null;
            }

            DisplayMetrics displayMetrics = new DisplayMetrics();
            ((WindowManager)context.getSystemService(Context.WINDOW_SERVICE))
                    .getDefaultDisplay()
                    .getMetrics(displayMetrics);

            if (boundingHeight == -1) {
                boundingHeight = displayMetrics.heightPixels;
            }
            if (boundingWidth == -1) {
                boundingWidth = displayMetrics.widthPixels;
            }

            if (CommCarePreferences.isSmartInflationEnabled()) {
                // scale based on native density AND bounding dimens
                return getBitmapScaledForNativeDensity(
                        context.getResources().getDisplayMetrics(), imageFile.getAbsolutePath(),
                        boundingHeight, boundingWidth,
                        CommCarePreferences.getTargetInflationDensity());
            } else {
                // just scaling down if the original image is too big for its container
                return getBitmapScaledToContainer(imageFile.getAbsolutePath(), boundingHeight,
                        boundingWidth);
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

        double scaleFactor = computeInflationScaleFactor(metrics, targetDensity);
        int calculatedHeight = Math.round((float)(imageHeight * scaleFactor));
        int calculatedWidth = Math.round((float)(imageWidth * scaleFactor));

        if (containerHeight < imageHeight || containerWidth < imageWidth || calculatedHeight < imageHeight) {
            // If either the container dimens or calculated dimens impose a smaller dimension,
            // scale down
            return getBitmapScaledByTargetOrContainer(imageFilepath, imageHeight, imageWidth,
                    calculatedHeight, calculatedWidth, containerHeight, containerWidth, false);
        } else {
            return attemptBoundedScaleUp(imageFilepath, calculatedHeight, calculatedWidth,
                    containerHeight, containerWidth);
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

        // Get our custom scale factor, based on this device's density and what the image's target
        // density was
        double customDpiScaleFactor = (double)SCREEN_DENSITY / targetDensity;

        return customDpiScaleFactor * proportionalAdjustmentFactor;
    }

    /**
     * @return A bitmap representation of the given image file, scaled down to the smallest
     * size that still fills the container
     *
     * More precisely, preserves the following 2 conditions:
     * 1. The larger of the 2 sides takes on the size of the corresponding container dimension
     * (e.g. if its width is larger than its height, then the new width should = containerWidth)
     * 2. The aspect ratio of the original image is maintained (so the height would get scaled
     * down proportionally with the width)
     */
    public static Bitmap getBitmapScaledToContainer(String imageFilepath, int containerHeight,
                                                     int containerWidth) {
        // Determine dimensions of original image
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(imageFilepath, o);
        int imageHeight = o.outHeight;
        int imageWidth = o.outWidth;

        return getBitmapScaledByTargetOrContainer(imageFilepath, imageHeight, imageWidth, -1, -1,
                containerHeight, containerWidth, true);
    }

    public static Bitmap getBitmapScaledToContainer(File imageFile, int containerHeight,
                                                    int containerWidth) {
        return getBitmapScaledToContainer(imageFile.getAbsolutePath(), containerHeight,
                containerWidth);
    }

    /**
     * @param scaleByContainerOnly If true, means that we're just trying to ensure that our bitmap
     *                             isn't way bigger than necessary, rather than creating a bitmap
     *                             of an exact size based on a target width and height. In this
     *                             case, targetWidth and targetHeight are ignored and the 2nd case
     *                             above is used.
     * @return A bitmap representation of the given image file, scaled down such that the new
     * dimensions of the image are the SMALLER of the following 2 options:
     * 1) targetHeight and targetWidth
     * 2) the largest dimensions for which the original aspect ratio is maintained, without
     * exceeding either boundingWidth or boundingHeight
     */
    private static Bitmap getBitmapScaledByTargetOrContainer(String imageFilepath,
                                                             int originalHeight, int originalWidth,
                                                             int targetHeight, int targetWidth,
                                                             int boundingHeight, int boundingWidth,
                                                             boolean scaleByContainerOnly) {
        Pair<Integer, Integer> dimensImposedByContainer = getDimensImposedByContainer(
                originalHeight, originalWidth, boundingHeight, boundingWidth);

        int newWidth, newHeight;
        if (scaleByContainerOnly) {
            newWidth = dimensImposedByContainer.first;
            newHeight = dimensImposedByContainer.second;
        } else {
            newWidth = Math.min(dimensImposedByContainer.first, targetWidth);
            newHeight = Math.min(dimensImposedByContainer.second, targetHeight);
        }

        int approximateScaleFactor = getApproxScaleFactor(newWidth, originalWidth);

        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inSampleSize = approximateScaleFactor;
            // Decode the bitmap with the largest integer scale down factor that will not make it
            // smaller than the final desired size
            Bitmap originalBitmap = BitmapFactory.decodeFile(imageFilepath, o);
            if (scaleByContainerOnly) {
                // Not worth performance loss of creating an exact scaled bitmap in this case
                return originalBitmap;
            } else {
                // Here we want to be more precise because we had a target width and height
                return Bitmap.createScaledBitmap(originalBitmap, newWidth, newHeight, false);
            }
        } catch (OutOfMemoryError e) {
            // OOM encountered trying to decode the bitmap, so we know we need to scale down by
            // a larger factor
            return performSafeScaleDown(imageFilepath, approximateScaleFactor + 1, 0);
        }
    }

    private static int getApproxScaleFactor(int newWidth, int originalWidth) {
        if (newWidth == 0) {
            return 1;
        } else {
            int scale = originalWidth / newWidth;
            if (scale == 0) {
                return 1;
            }
            return scale;
        }
    }

    /**
     * @return The original dimens if they both fit within the bounds. Otherwise, return the
     * smallest dimensions that both preserve the original aspect ratio, and still fill the
     * container
     */
    private static Pair<Integer, Integer> getDimensImposedByContainer(int originalHeight,
                                                                      int originalWidth,
                                                                      int boundingHeight,
                                                                      int boundingWidth) {
        if (originalHeight < boundingHeight && originalWidth < boundingWidth) {
            return new Pair<>(originalWidth, originalHeight);
        }

        double heightScaleDownFactor = (double)boundingHeight / originalHeight;
        double widthScaleDownFactor =  (double)boundingWidth / originalWidth;
        // Choosing the larger of the scale down factors, so that the image still fills the entire
        // container
        double dominantScaleDownFactor = Math.max(heightScaleDownFactor, heightScaleDownFactor);

        int widthImposedByContainer = (int)Math.round(originalWidth * dominantScaleDownFactor);
        int heightImposedByContainer = (int)Math.round(originalHeight * dominantScaleDownFactor);
        return new Pair<>(widthImposedByContainer, heightImposedByContainer);

    }

    /**
     * @return A bitmap representation of the given image file, scaled up as close as possible to
     * desiredWidth and desiredHeight, without exceeding either boundingHeight or boundingWidth
     */
    private static Bitmap attemptBoundedScaleUp(String imageFilepath,
                                                int desiredHeight, int desiredWidth,
                                                int boundingHeight, int boundingWidth) {
        if (boundingHeight < desiredHeight || boundingWidth < desiredWidth) {
            Pair<Integer, Integer> dimensForScaleUp = boundedScaleUpHelper(
                    desiredHeight, desiredWidth, boundingHeight, boundingWidth);
            desiredWidth = dimensForScaleUp.first;
            desiredHeight = dimensForScaleUp.second;
        }
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inScaled = false;
            Bitmap originalBitmap = BitmapFactory.decodeFile(imageFilepath, o);
            try {
                return Bitmap.createScaledBitmap(originalBitmap, desiredWidth, desiredHeight, false);
            } catch (OutOfMemoryError e) {
                return originalBitmap;
            }
        } catch (OutOfMemoryError e) {
            // Just inflating the image at its original size caused an OOM error, don't have a
            // choice but to scale down
            return performSafeScaleDown(imageFilepath, 2, 1);
        }
    }

    /**
     * @return A (width, height) pair representing the largest possible dimensions that both:
     * a) do not exceed boundingHeight or boundingWidth, and
     * b) maintain the aspect ratio given by originalHeight and originalWidth
     */
    private static Pair<Integer, Integer> boundedScaleUpHelper(int originalHeight,
                                                               int originalWidth,
                                                               int boundingHeight,
                                                               int boundingWidth) {
        double heightScaleFactor = (double)boundingHeight / originalHeight;
        double widthScaleFactor =  (double)boundingWidth / originalWidth;
        double dominantScaleFactor = Math.min(heightScaleFactor, widthScaleFactor);

        int scaledUpWidthImposedByContainer = (int)Math.round(originalWidth * dominantScaleFactor);
        int scaledUpHeightImposedByContainer = (int)Math.round(originalHeight * dominantScaleFactor);
        return new Pair<>(scaledUpWidthImposedByContainer, scaledUpHeightImposedByContainer);
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
