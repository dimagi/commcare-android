package org.commcare.utils;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.AudioManager;
import androidx.exifinterface.media.ExifInterface;
import android.os.Build;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.Pair;
import android.view.WindowManager;

import org.commcare.CommCareApplication;
import org.commcare.engine.references.JavaFileReference;
import org.commcare.preferences.HiddenPreferences;
import org.commcare.util.LogTypes;
import org.javarosa.core.reference.InvalidReferenceException;
import org.javarosa.core.reference.Reference;
import org.javarosa.core.reference.ReferenceManager;
import org.javarosa.core.services.Logger;

import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

/**
 * @author ctsims
 */
public class MediaUtil {

    private static final String TAG = MediaUtil.class.toString();

    public static final String FORM_VIDEO = "video";
    public static final String FORM_AUDIO = "audio";
    public static final String FORM_IMAGE = "image";


    /**
     * Attempts to inflate an image from a CommCare UI definition source.
     *
     * @param jrUri          The image to inflate
     * @param boundingWidth  the width of the container this image is being inflated into, to serve
     *                       as a max width. If passed in as -1, gets set to screen width
     * @param boundingHeight the height of the container this image is being inflated into, to
     *                       serve as a max height. If passed in as -1, gets set to screen height
     * @return A bitmap if one could be created. Null if error occurs or the image is unavailable.
     */
    @Nullable
    public static Bitmap inflateDisplayImage(Context context, String jrUri,
                                             int boundingWidth, int boundingHeight,
                                             boolean respectBoundsExactly) {
        if (jrUri == null || jrUri.equals("")) {
            return null;
        }
        try {
            Reference ref = ReferenceManager.instance().DeriveReference(jrUri);
            try {
                if (!ref.doesBinaryExist()) {
                    return null;
                }
                if (!(ref instanceof JavaFileReference)) {
                    return BitmapFactory.decodeStream(ref.getStream());
                }
            } catch (IOException e) {
                Logger.log(LogTypes.TYPE_ERROR_ASSERTION, "IO Exception loading reference: " + jrUri);
                return null;
            }

            String imageFilename = ref.getLocalURI();
            final File imageFile = new File(imageFilename);
            if (!imageFile.exists()) {
                return null;
            }

            DisplayMetrics displayMetrics = new DisplayMetrics();
            ((WindowManager) context.getSystemService(Context.WINDOW_SERVICE))
                    .getDefaultDisplay()
                    .getMetrics(displayMetrics);

            if (boundingHeight == -1) {
                boundingHeight = displayMetrics.heightPixels;
            }
            if (boundingWidth == -1) {
                boundingWidth = displayMetrics.widthPixels;
            }

            if (HiddenPreferences.isSmartInflationEnabled()) {
                // scale based on bounding dimens AND native density
                return getBitmapScaledForNativeDensity(
                        context.getResources().getDisplayMetrics(), imageFile.getAbsolutePath(),
                        boundingHeight, boundingWidth, HiddenPreferences.getTargetInflationDensity());
            } else {
                // just scale down if the original image is way too big for its container
                return getBitmapScaledToContainer(imageFile.getAbsolutePath(), boundingHeight,
                        boundingWidth, respectBoundsExactly);
            }
        } catch (InvalidReferenceException e) {
            Log.e("ImageInflater", "image invalid reference exception for " + e.getReferenceString());
            e.printStackTrace();
        }
        return null;
    }

    public static int getActionBarHeightInPixels(Context context) {
        final TypedArray styledAttributes = context.getTheme().obtainStyledAttributes(
                new int[]{android.R.attr.actionBarSize});
        int actionBarSize = (int) styledAttributes.getDimension(0, 0);
        styledAttributes.recycle();

        return actionBarSize;
    }

    public static Bitmap inflateDisplayImage(Context context, String jrUri,
                                             int boundingWidth, int boundingHeight) {
        return inflateDisplayImage(context, jrUri, boundingWidth, boundingHeight, false);
    }

    public static Bitmap inflateDisplayImage(Context context, String jrUri) {
        return inflateDisplayImage(context, jrUri, -1, -1, false);
    }

    /**
     * Attempt to inflate an image source into a bitmap whose final dimensions are based upon
     * 2 factors:
     * <p>
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
        Pair<File, Bitmap> cacheKey = getCacheFileLocationAndBitmap(imageFilepath,
                String.format("density_%d_%d_%d", containerHeight, containerWidth, targetDensity));

        if (cacheKey != null && cacheKey.second != null) {
            return cacheKey.second;
        }

        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        o.inScaled = false;
        BitmapFactory.decodeFile(imageFilepath, o);
        int imageHeight = o.outHeight;
        int imageWidth = o.outWidth;

        double scaleFactor = computeInflationScaleFactor(metrics, targetDensity);
        int calculatedHeight = Math.round((float) (imageHeight * scaleFactor));
        int calculatedWidth = Math.round((float) (imageWidth * scaleFactor));

        Bitmap toReturn;

        if (scaleFactor > 1) {
            toReturn = attemptBoundedScaleUp(imageFilepath, calculatedHeight, calculatedWidth,
                    containerHeight, containerWidth);
        } else {
            toReturn = scaleDownToTargetOrContainer(imageFilepath, imageHeight, imageWidth,
                    calculatedHeight, calculatedWidth, containerHeight, containerWidth, false, true);
        }

        if (cacheKey != null) {
            attemptWriteCacheToLocation(toReturn, cacheKey.first);
        }

        return toReturn;
    }

    private static void attemptWriteCacheToLocation(Bitmap toReturn, File cacheLocation) {
        try {
            FileUtil.writeBitmapToDiskAndCleanupHandles(toReturn,
                    ImageType.fromExtension(FileUtil.getExtension(cacheLocation.getPath())),
                    cacheLocation);
        } catch (IOException e) {
            e.printStackTrace();
            Log.d(TAG, "Failed to write bitmap to cache for " + cacheLocation);
        }
    }

    /**
     * Attempts to load a cached filepath from the given location and tag, and returns the
     * location for the cached file either way.
     * <p>
     * If caching is unavailable, null should be returned. If an object is returned, the first
     * argument must be non-null, and must have the same extension as the input filepath.
     * <p>
     * The cache key/object will handle its own file path/modified clearance, the tag provided
     * should differentiate between different ways of inflating the provided image path
     */
    private static Pair<File, Bitmap> getCacheFileLocationAndBitmap(String imageFilepath,
                                                                    String tag) {
        File cacheKey = getCacheFileLocation(imageFilepath, tag);
        if (cacheKey == null) {
            return null;
        }
        Bitmap b = null;
        if (cacheKey.exists()) {
            try {
                b = inflateImageSafe(cacheKey.getPath()).first;
            } catch (RuntimeException e) {
                try {
                    cacheKey.delete();
                    Log.d(TAG, "Removed potentially invalid cache from " + cacheKey.toString());
                } catch (Exception inner) {

                }
            }
        }
        return new Pair<>(cacheKey, b);
    }

    private static File getCacheFileLocation(String imageFilepath, String tag) {
        Context c = CommCareApplication.instance().getApplicationContext();
        File cacheDirectory = c.getCacheDir();

        if (!cacheDirectory.exists()) {
            return null;
        }

        String ext = FileUtil.getExtension(imageFilepath);
        if (ImageType.fromExtension(ext) == null) {
            Log.d(TAG, "Couldn't identify the format of a file for caching: " + imageFilepath);
            return null;
        }

        File fileToTransform = new File(imageFilepath);

        String fileName = String.format("%s_%d_%s.%s",
                getHashedImageFilepath(imageFilepath),
                fileToTransform.lastModified(),
                tag,
                ext);
        return new File(cacheDirectory, fileName);
    }

    public static String getHashedImageFilepath(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            md.update(input.getBytes());
            byte[] hashInBytes = md.digest();

            BigInteger number = new BigInteger(1, hashInBytes);
            String md5 = number.toString(16);
            while (md5.length() < 32) {
                md5 = "0" + md5;
            }
            return md5;
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("No MD5 platform hashing enabled");
        }
    }

    /**
     * @return Our custom scale factor, based on this device's density and what the image's target
     * density was
     */
    public static double computeInflationScaleFactor(DisplayMetrics metrics, int targetDensity) {
        final int SCREEN_DENSITY = metrics.densityDpi;
        double customDpiScaleFactor = (double) SCREEN_DENSITY / targetDensity;
        double proportionalAdjustmentFactor = getCustomAndroidAdjustmentFactor(metrics);
        return customDpiScaleFactor * proportionalAdjustmentFactor;
    }

    private static double getCustomAndroidAdjustmentFactor(DisplayMetrics metrics) {
        // This is the formula Android *usually* uses to compute the value of metrics.density
        // for a device. If this is in fact the value being used, we are not interested in it.
        // However, if the actual value differs at all from the standard calculation, it means
        // Android is taking other factors into consideration (such as straight up screen size)
        // when it re-sizes an image for this device, so we want to incorporate that proportionally
        // into our own version of the scale factor
        double standardNativeScaleFactor = (double) metrics.densityDpi / DisplayMetrics.DENSITY_DEFAULT;

        double actualNativeScaleFactor = metrics.density;
        if (actualNativeScaleFactor > standardNativeScaleFactor) {
            return 1 +
                    ((actualNativeScaleFactor - standardNativeScaleFactor) / standardNativeScaleFactor);
        } else if (actualNativeScaleFactor < standardNativeScaleFactor) {
            return actualNativeScaleFactor / standardNativeScaleFactor;
        } else {
            return 1;
        }
    }

    /**
     * @return A bitmap representation of the given image file, scaled down to the smallest
     * size that still fills the container
     * <p>
     * More precisely, preserves the following 2 conditions:
     * 1. The larger of the 2 sides takes on the size of the corresponding container dimension
     * (e.g. if its width is larger than its height, then the new width should = containerWidth)
     * 2. The aspect ratio of the original image is maintained (so the height would get scaled
     * down proportionally with the width)
     */
    public static Bitmap getBitmapScaledToContainer(String imageFilepath, int containerHeight,
                                                    int containerWidth,
                                                    boolean respectBoundsExactly) {

        Pair<File, Bitmap> cacheKey = getCacheFileLocationAndBitmap(imageFilepath,
                String.format("container_%d_%d_%b", containerHeight, containerWidth,
                        respectBoundsExactly));

        if (cacheKey != null && cacheKey.second != null) {
            return cacheKey.second;
        }


        // Determine dimensions of original image
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(imageFilepath, o);
        int imageHeight = o.outHeight;
        int imageWidth = o.outWidth;

        Bitmap toReturn = scaleDownToTargetOrContainer(imageFilepath, imageHeight, imageWidth, -1,
                -1, containerHeight, containerWidth, true, respectBoundsExactly);

        if (cacheKey != null) {
            attemptWriteCacheToLocation(toReturn, cacheKey.first);
        }

        return toReturn;
    }

    public static Bitmap getBitmapScaledToContainer(File imageFile, int containerHeight,
                                                    int containerWidth) {
        return getBitmapScaledToContainer(imageFile.getAbsolutePath(), containerHeight,
                containerWidth, false);
    }

    /**
     * @param scaleByContainerOnly If true, means that we're just trying to ensure that our bitmap
     *                             isn't way bigger than necessary, rather than creating a bitmap
     *                             of an exact size based on a target width and height. In this
     *                             case, targetWidth and targetHeight are ignored and the 2nd case
     *                             below is used.
     * @return A bitmap representation of the given image file, scaled down if necessary such that
     * the new dimensions of the image are the SMALLER of the following 2 options:
     * 1) targetHeight and targetWidth
     * 2) the largest dimensions for which the original aspect ratio is maintained, without
     * exceeding either boundingWidth or boundingHeight (or just the original dimensions if the
     * image already roughly fits in the bounds)
     */
    private static Bitmap scaleDownToTargetOrContainer(String imageFilepath,
                                                       int originalHeight, int originalWidth,
                                                       int targetHeight, int targetWidth,
                                                       int boundingHeight, int boundingWidth,
                                                       boolean scaleByContainerOnly,
                                                       boolean respectBoundsExactly) {
        Pair<Integer, Integer> dimensImposedByContainer = getRoughDimensImposedByContainer(
                originalHeight, originalWidth, boundingHeight, boundingWidth);

        int newWidth, newHeight;
        if (scaleByContainerOnly) {
            newWidth = dimensImposedByContainer.first;
            newHeight = dimensImposedByContainer.second;
        } else {
            newWidth = Math.min(dimensImposedByContainer.first, targetWidth);
            newHeight = Math.min(dimensImposedByContainer.second, targetHeight);
        }

        int approximateScaleDownFactor = getApproxScaleDownFactor(newWidth, originalWidth);
        Bitmap b = inflateImageSafe(imageFilepath, approximateScaleDownFactor).first;

        int orientation = ExifInterface.ORIENTATION_NORMAL;
        try {
            ExifInterface exif = new ExifInterface(imageFilepath);
            orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
        } catch (Exception e) {
            Logger.exception("Unable to read exif data from image file: ", e);
        }

        // Rotate the bitmap if needed
        Bitmap rotatedBitmap = b;
        if (orientation != ExifInterface.ORIENTATION_NORMAL) {
            rotatedBitmap = rotateBitmap(b, orientation);
        }

        if (scaleByContainerOnly && !respectBoundsExactly) {
            // Not worth performance loss of creating an exact scaled bitmap in this case
            return rotatedBitmap;
        } else {
            try {
                // Here we want to be more precise because we have a target width and height, or
                // specified that respecting the bounding container precisely is important
                return Bitmap.createScaledBitmap(rotatedBitmap, newWidth, newHeight, false);
            } catch (OutOfMemoryError e) {
                Logger.exception("Ran out of memory attempting to scale image at: " + imageFilepath + " with exception: ", e);
                rotatedBitmap.recycle();
                return null;
            }
        }
    }

    // Helper method to rotate the bitmap based on EXIF orientation that we previously retained
    private static Bitmap rotateBitmap(Bitmap bitmap, int orientation) {
        if (bitmap == null) {
            return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
        }
        if (orientation == ExifInterface.ORIENTATION_NORMAL) {
            return bitmap;
        }

        Matrix matrix = new Matrix();
        switch (orientation) {
            case ExifInterface.ORIENTATION_ROTATE_90:
                matrix.postRotate(90);
                break;
            case ExifInterface.ORIENTATION_ROTATE_180:
                matrix.postRotate(180);
                break;
            case ExifInterface.ORIENTATION_ROTATE_270:
                matrix.postRotate(270);
                break;
            case ExifInterface.ORIENTATION_FLIP_HORIZONTAL:
                matrix.postScale(-1, 1);
                break;
            case ExifInterface.ORIENTATION_FLIP_VERTICAL:
                matrix.postScale(1, -1);
                break;
            case ExifInterface.ORIENTATION_TRANSPOSE:
                matrix.postRotate(90);
                matrix.postScale(-1, 1);
                break;
            case ExifInterface.ORIENTATION_TRANSVERSE:
                matrix.postRotate(270);
                matrix.postScale(-1, 1);
                break;
            default:
                return bitmap;
        }
        try {
            return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
        } catch (OutOfMemoryError e) {
            Logger.exception("Ran out of memory attempting to rotate bitmap", e);
            return bitmap;
        }
    }

    private static int getApproxScaleDownFactor(int newWidth, int originalWidth) {
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
     * @return The smallest dimensions that both preserve the original aspect ratio, and mean
     * that the image still fills the container. It is unimportant to scale down exactly to the
     * container size; we just don't want to be creating a bitmap that is way bigger than necessary.
     */
    private static Pair<Integer, Integer> getRoughDimensImposedByContainer(int originalHeight,
                                                                           int originalWidth,
                                                                           int boundingHeight,
                                                                           int boundingWidth) {
        if (originalHeight < boundingHeight || originalWidth < boundingWidth) {
            // Since this is only meant to be a rough scale-down to keep the image from being way
            // too large, we only want to scale down if both dimensions are currently exceeding
            // their bounds
            return new Pair<>(originalWidth, originalHeight);
        }

        double heightScaleDownFactor = (double) boundingHeight / originalHeight;
        double widthScaleDownFactor = (double) boundingWidth / originalWidth;
        // Choosing the larger of the scale down factors, so that the image still fills the entire
        // container
        double dominantScaleDownFactor = Math.max(widthScaleDownFactor, heightScaleDownFactor);

        int widthImposedByContainer = (int) Math.round(originalWidth * dominantScaleDownFactor);
        int heightImposedByContainer = (int) Math.round(originalHeight * dominantScaleDownFactor);
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
            return performSafeScaleDown(imageFilepath, 2, 1).first;
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
        double heightScaleFactor = (double) boundingHeight / originalHeight;
        double widthScaleFactor = (double) boundingWidth / originalWidth;
        double dominantScaleFactor = Math.min(heightScaleFactor, widthScaleFactor);

        int scaledUpWidthImposedByContainer = (int) Math.round(originalWidth * dominantScaleFactor);
        int scaledUpHeightImposedByContainer = (int) Math.round(originalHeight * dominantScaleFactor);
        return new Pair<>(scaledUpWidthImposedByContainer, scaledUpHeightImposedByContainer);
    }

    /**
     * Inflate an image file into a bitmap, attempting first to inflate it at the given scale-down
     * factor, but progressively scaling down further if an OutOfMemoryError is encountered
     *
     * @return the bitmap, plus a boolean value representing whether the image had to be downsized
     */
    public static Pair<Bitmap, Boolean> inflateImageSafe(String imageFilepath, int scaleDownFactor) {
        return performSafeScaleDown(imageFilepath, scaleDownFactor, 0);
    }

    public static Pair<Bitmap, Boolean> inflateImageSafe(String imageFilepath) {
        return inflateImageSafe(imageFilepath, 1);
    }

    /**
     * @return A scaled-down bitmap for the given image file, progressively increasing the
     * scale-down factor by 1 until allocating memory for the bitmap does not cause an OOM error,
     * and a boolean value representing whether the image had to be downsized
     */
    private static Pair<Bitmap, Boolean> performSafeScaleDown(String imageFilepath,
                                                              int scaleDownFactor, int depth) {
        if (depth == 5) {
            // Limit the number of recursive calls
            return new Pair<>(null, true);
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = scaleDownFactor;
        try {
            return new Pair<>(BitmapFactory.decodeFile(imageFilepath, options), scaleDownFactor > 1);
        } catch (OutOfMemoryError e) {
            return performSafeScaleDown(imageFilepath, scaleDownFactor + 1, depth + 1);
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    public static boolean isRecordingActive(Context context) {
        return ((AudioManager) context.getSystemService(Context.AUDIO_SERVICE))
                .getActiveRecordingConfigurations().size() > 0;
    }

}
