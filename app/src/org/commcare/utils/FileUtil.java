package org.commcare.utils;

import android.annotation.SuppressLint;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;

import androidx.exifinterface.media.ExifInterface;

import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;
import android.webkit.MimeTypeMap;
import android.widget.EditText;
import android.widget.Toast;

import org.commcare.CommCareApplication;
import org.commcare.resources.model.MissingMediaException;
import org.commcare.resources.model.Resource;
import org.commcare.util.LogTypes;
import org.javarosa.core.io.StreamsUtil;
import org.javarosa.core.reference.InvalidReferenceException;
import org.javarosa.core.reference.Reference;
import org.javarosa.core.reference.ReferenceManager;
import org.javarosa.core.services.Logger;
import org.javarosa.core.services.locale.Localization;
import org.javarosa.core.util.PropertyUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.nio.channels.FileChannel;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Properties;
import java.util.Vector;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;

import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;

/**
 * @author ctsims
 */
public class FileUtil {

    private static final int WARNING_SIZE = 3000;

    private static final String LOG_TOKEN = "cc-file-util";

    private static final String[] EXIF_TAGS = {
            ExifInterface.TAG_GPS_LATITUDE,
            ExifInterface.TAG_GPS_LATITUDE_REF,
            ExifInterface.TAG_GPS_LONGITUDE,
            ExifInterface.TAG_GPS_LONGITUDE_REF,
            ExifInterface.TAG_GPS_TIMESTAMP,
            ExifInterface.TAG_GPS_DATESTAMP,
            ExifInterface.TAG_GPS_ALTITUDE,
            ExifInterface.TAG_GPS_ALTITUDE_REF,
            ExifInterface.TAG_GPS_AREA_INFORMATION,
            ExifInterface.TAG_DATETIME,
            ExifInterface.TAG_DATETIME_DIGITIZED,
            ExifInterface.TAG_DATETIME_ORIGINAL,
            ExifInterface.TAG_OFFSET_TIME,
            ExifInterface.TAG_OFFSET_TIME_ORIGINAL,
            ExifInterface.TAG_OFFSET_TIME_DIGITIZED,
            ExifInterface.TAG_COPYRIGHT,
            ExifInterface.TAG_IMAGE_DESCRIPTION,
            ExifInterface.TAG_EXIF_VERSION,
            ExifInterface.TAG_ORIENTATION
    };

    public static boolean deleteFileOrDir(String path) {
        return deleteFileOrDir(new File(path));
    }

    // Returns true if the file and all of its contents were deleted successfully, false otherwise
    public static boolean deleteFileOrDir(File f) {
        if (!f.exists()) {
            return true;
        }
        if (f.isDirectory() && f.listFiles() != null) {
            for (File child : f.listFiles()) {
                if (!deleteFileOrDir(child)) {
                    return false;
                }
            }
        }
        return f.delete();
    }

    public static boolean cleanFilePath(String fullPath, String extendedPath) {
        //There are actually a few things that can go wrong here, should be careful

        //No extended path, life is good.
        if (extendedPath == null) {
            return true;
        }

        //Something's weird, bail!
        if (!fullPath.contains(extendedPath)) {
            return true;
        }

        //Get the root that we should stop at
        File terminal = new File(fullPath.replace(extendedPath, ""));

        File walker = new File(fullPath);

        //technically we shouldn't ever hit the first case here, but also don't wanna get stuck by a weird equality bug.
        while (walker != null && !terminal.equals(walker)) {
            if (walker.isDirectory()) {
                //only wipe out empty directories.
                if (walker.list().length == 0) {
                    if (!walker.delete()) {
                        //I don't think we actually want to fail here, it's not a showstopper.
                        Log.w("cleanup", "couldn't delete directory " + walker.getAbsolutePath() + " while cleaning up file paths");
                        //throw an exception/false here if we care.
                    }
                }
            }
            walker = walker.getParentFile();
        }
        return true;
    }

    private static final String illegalChars = "'*','+'~|<> !?:./\\";

    public static String SanitizeFileName(String input) {
        for (char c : illegalChars.toCharArray()) {
            input = input.replace(c, '_');
        }
        return input;
    }

    /**
     * Copies a source file from a FileProvider Content provider into a file directory local
     * to this application.
     * <p>
     * The app needs to already have permissions granted for the external file content.
     *
     * @param contentUri A valid uri to a contentprovider backed external file
     * @param destDir    The destination directory for the file to be copied into.
     * @return The destination copy of the file
     */
    public static File copyContentFileToLocalDir(Uri contentUri, File destDir, Context context) throws IOException {
        ParcelFileDescriptor inFile = context.getContentResolver().openFileDescriptor(contentUri, "r");

        File newFile = new File(destDir, getFileName(context, contentUri));

        long fileLength = -1;

        //Looks like our source file exists, so let's go grab it
        FileChannel srcFc = null;
        FileChannel dst = null;
        try {
            srcFc = new FileInputStream(inFile.getFileDescriptor()).getChannel();
            fileLength = srcFc.size();
            dst = new FileOutputStream(newFile).getChannel();
            dst.transferFrom(srcFc, 0, fileLength);
        } finally {
            try {
                if (srcFc != null) {
                    srcFc.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            try {
                if (dst != null) {
                    dst.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            try {
                inFile.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        if (!newFile.exists() || newFile.length() != fileLength) {
            throw new IOException("Failed to copy file from content path " + contentUri.toString() + " to dest " + newFile);
        }
        return newFile;
    }

    public static void copyFile(String oldPath, String newPath) throws IOException {
        copyFile(new File(oldPath), new File(newPath));
    }

    public static void copyFile(File oldPath, File newPath) throws IOException {
        if (oldPath.exists()) {
            if (newPath.isDirectory()) {
                newPath = new File(newPath, oldPath.getName());
            }

            FileChannel src;
            src = new FileInputStream(oldPath).getChannel();
            FileChannel dst = new FileOutputStream(newPath).getChannel();
            dst.transferFrom(src, 0, src.size());
            src.close();
            dst.close();
        } else {
            Log.e(LOG_TOKEN, "Source file does not exist: " + oldPath.getAbsolutePath());
        }
    }

    public static void copyFile(File oldPath, File newPath, Cipher oldRead, Cipher newWrite) throws IOException {

        if (!newPath.createNewFile()) {
            throw new IOException("Couldn't create new file @ " + newPath.toString());
        }

        InputStream is = null;
        OutputStream os = null;
        try {

            is = new FileInputStream(oldPath);
            if (oldRead != null) {
                is = new CipherInputStream(is, oldRead);
            }

            os = new FileOutputStream(newPath);
            if (newWrite != null) {
                os = new CipherOutputStream(os, newWrite);
            }

            StreamsUtil.writeFromInputToOutputUnmanaged(is, os);
        } finally {
            try {
                if (is != null) {
                    is.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }

            try {
                if (os != null) {
                    os.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Get a new, clean location to put a file in the same path as the incoming file
     *
     * @param f              The existing file
     * @param slug           A new chunk to append to the file name
     * @param removeExisting Whether to remove any files which already appear in this location.
     *                       If false, the method will continue trying to generate new paths until there is no conflict
     * @return A new file location which does not reference an existing file.
     */
    public static File getNewFileLocation(File f, String slug, boolean removeExisting) {
        if (slug == null) {
            slug = PropertyUtils.genGUID(5);
        }
        String name = f.getName();

        int lastDot = name.lastIndexOf(".");
        if (lastDot != -1) {
            String prefix = name.substring(0, lastDot);
            String postfix = name.substring(lastDot);

            name = prefix + "_" + slug + postfix;
        } else {
            name = name + "_" + slug;
        }

        File newLocation = new File(f.getParent() + File.separator + name);
        if (newLocation.exists()) {
            if (removeExisting) {
                deleteFileOrDir(newLocation);
            } else {
                return getNewFileLocation(newLocation, null, removeExisting);
            }
        }
        return newLocation;
    }

    public static void copyFileDeep(File oldFolder, File newFolder) throws IOException {
        //Create the new folder
        newFolder.mkdir();

        if (oldFolder.listFiles() != null) {
            //Start copying over files
            for (File oldFile : oldFolder.listFiles()) {
                File newFile = new File(newFolder.getPath() + File.separator + oldFile.getName());
                if (oldFile.isDirectory()) {
                    copyFileDeep(oldFile, newFile);
                } else {
                    FileUtil.copyFile(oldFile, newFile);
                }
            }
        }
    }

    /**
     * http://stackoverflow.com/questions/11281010/how-can-i-get-external-sd-card-path-for-android-4-0
     * <p>
     * Used in SD Card functionality to get the location of the SD card for reads and writes
     * Returns a list of available mounts; for our purposes, we just use the first
     */

    public static ArrayList<String> getExternalMounts() {
        final ArrayList<String> out = new ArrayList<>();
        String reg = "(?i).*vold.*(vfat|ntfs|exfat|fat32|ext3|ext4|sdfat).*rw.*";
        String s = "";
        try {
            final Process process = new ProcessBuilder().command("mount")
                    .redirectErrorStream(true).start();
            process.waitFor();
            final InputStream is = process.getInputStream();
            final byte[] buffer = new byte[1024];
            while (is.read(buffer) != -1) {
                s = s + new String(buffer);
            }
            is.close();
        } catch (final Exception e) {
            e.printStackTrace();
        }

        // parse output
        final String[] lines = s.split("\n");
        for (String line : lines) {
            if (!line.toLowerCase(Locale.US).contains("asec")) {
                if (line.matches(reg)) {
                    String[] parts = line.split(" ");
                    for (String part : parts) {
                        if (part.startsWith("/"))
                            if (!part.toLowerCase(Locale.US).contains("vold"))
                                out.add(part);
                    }
                }
            }
        }
        return out;
    }

    /**
     * Identifies whether the provided string is a content URI as defined in
     * https://developer.android.com/reference/android/content/ContentUris
     */
    public static boolean isContentUri(String input) {
        if (input == null) {
            return false;
        }

        return "content".equals(Uri.parse(input).getScheme());
    }

    /**
     * Turn a filepath into a global android URI that can be passed
     * to an intent.
     */
    public static String getGlobalStringUri(String fileLocation) {
        return "file://" + fileLocation;
    }

    public static void checkReferenceURI(Resource r, String URI, Vector<MissingMediaException> problems) throws InvalidReferenceException {
        Reference mRef = ReferenceManager.instance().DeriveReference(URI);
        String mLocalReference = mRef.getLocalURI();
        try {
            if (!mRef.doesBinaryExist()) {
                problems.addElement(new MissingMediaException(r, "Missing external media: " + mLocalReference, URI,
                        MissingMediaException.MissingMediaExceptionType.FILE_NOT_FOUND));
            }
        } catch (IOException e) {
            problems.addElement(new MissingMediaException(r, "Problem reading external media: " + mLocalReference, URI,
                    MissingMediaException.MissingMediaExceptionType.FILE_NOT_ACCESSIBLE));
        }
    }

    public static boolean referenceFileExists(String uri) {
        if (uri != null && !uri.equals("")) {
            try {
                return new File(ReferenceManager.instance().DeriveReference(uri).getLocalURI()).exists();
            } catch (InvalidReferenceException e) {
                e.printStackTrace();
            }
        }
        return false;
    }


    /**
     * Ensure that everything between "localpart" and f exists
     * and create it if not.
     */
    public static void ensureFilePathExists(File f) {
        File folder = f.getParentFile();
        if (folder != null) {
            //Don't worry about return value
            folder.mkdirs();
        }
    }

    /*
     * if we are on KitKat we need use the new API to find the mounted roots, then append our application
     * specific path that we're allowed to write to
     */
    @SuppressLint("NewApi")
    private static String getExternalDirectoryKitKat(Context c) {
        File[] extMounts = c.getExternalFilesDirs(null);
        // first entry is emualted storage. Second if it exists is secondary (real) SD.

        if (extMounts.length < 2) {
            return null;
        }

        /*
         * First volume returned by getExternalFilesDirs is always "primary" volume,
         * or emulated. Further entries, if they exist, will be "secondary" or external SD
         *
         * http://www.doubleencore.com/2014/03/android-external-storage/
         *
         */

        File sdRoot = extMounts[1];

        // because apparently getExternalFilesDirs entries can be null
        if (sdRoot == null) {
            return null;
        }

        return sdRoot.getAbsolutePath() + "/Android/data/org.commcare.dalvik";
    }

    /*
     * If we're on KitKat use the new OS path
     */
    public static String getDumpDirectory(Context c) {
        return getExternalDirectoryKitKat(c);
    }

    public static Properties loadProperties(File file) throws IOException {
        Properties prop = new Properties();
        InputStream input = null;
        try {
            input = new FileInputStream(file);
            prop.load(input);
            return prop;
        } finally {
            if (input != null) {
                try {
                    input.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public static boolean createFolder(String path) {
        boolean made = true;
        File dir = new File(path);
        if (!dir.exists()) {
            made = dir.mkdirs();
        }
        return made;
    }

    public static String getMd5Hash(File file) {
        try {
            // CTS (6/15/2010) : stream file through digest instead of handing it the byte[]
            MessageDigest md = MessageDigest.getInstance("MD5");
            int chunkSize = 256;

            byte[] chunk = new byte[chunkSize];

            // Get the size of the file
            long lLength = file.length();

            if (lLength > Integer.MAX_VALUE) {
                Log.e(LOG_TOKEN, "File " + file.getName() + "is too large");
                return null;
            }

            int length = (int)lLength;

            InputStream is;
            is = new FileInputStream(file);

            int l;
            for (l = 0; l + chunkSize < length; l += chunkSize) {
                is.read(chunk, 0, chunkSize);
                md.update(chunk, 0, chunkSize);
            }

            int remaining = length - l;
            if (remaining > 0) {
                is.read(chunk, 0, remaining);
                md.update(chunk, 0, remaining);
            }
            byte[] messageDigest = md.digest();

            BigInteger number = new BigInteger(1, messageDigest);
            String md5 = number.toString(16);
            while (md5.length() < 32)
                md5 = "0" + md5;
            is.close();
            return md5;

        } catch (NoSuchAlgorithmException e) {
            Log.e("MD5", e.getMessage());
            return null;

        } catch (FileNotFoundException e) {
            Log.e("No Cache File", e.getMessage());
            return null;
        } catch (IOException e) {
            Log.e("Problem reading file", e.getMessage());
            return null;
        }
    }

    public static String getExtension(String filePath) {
        if (filePath != null && filePath.contains(".")) {
            return last(filePath.split("\\."));
        }
        return "";
    }

    /**
     * Retrieve a file's name from content URI using below process:
     * - Get fileName using {@link UriToFilePath#getPathFromUri(Context, Uri)}.
     * - If the fileName doesn't have an extension, then retrieve fileName using file provider. @see https://developer.android.com/training/secure-file-sharing/retrieve-info.html#RetrieveFileInfo
     * - If the fileName still doesn't have extension, use {@link #getFileExtensionUsingMimeType(Context, Uri, String)}
     *
     * @return FileName with extension
     */
    public static String getFileName(Context context, Uri uri) throws FileExtensionNotFoundException {
        String fileName;
        try {
            fileName = getFileName(UriToFilePath.getPathFromUri(context, uri));
        } catch (UriToFilePath.NoDataColumnForUriException e) {
            fileName = uri.getLastPathSegment();
        }
        if (TextUtils.isEmpty(getExtension(fileName))) {
            try (Cursor cursor = context.getContentResolver().query(
                    uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    fileName = cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME));
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        if (TextUtils.isEmpty(getExtension(fileName))) {
            String ext = getFileExtensionUsingMimeType(context, uri, fileName);
            fileName = fileName + "." + ext;
        }
        return fileName;
    }

    /**
     * @return file extension by getting the mimeType from the URI.
     * @throws FileExtensionNotFoundException if we can't find mimeType from the URI.
     */
    private static String getFileExtensionUsingMimeType(Context context, Uri uri, String fileName) throws FileExtensionNotFoundException {
        String mimeType = getMimeTypeFromUri(context, uri);
        String ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType);
        if (TextUtils.isEmpty(ext)) {
            throw new FileExtensionNotFoundException(
                    "Can't find extension for URI :: " + uri
                            + " and mimeType :: " + mimeType
                            + " and fileName :: " + fileName
            );
        }
        return ext;
    }

    /**
     * @return mimeType for the file denoted by URI
     */
    private static String getMimeTypeFromUri(Context context, Uri uri) {
        String mimeType = context.getContentResolver().getType(uri);
        if (TextUtils.isEmpty(mimeType)) {
            try (Cursor cursor = context.getContentResolver().query(
                    uri, new String[]{MediaStore.MediaColumns.MIME_TYPE}, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    mimeType = cursor.getString(cursor.getColumnIndex(MediaStore.MediaColumns.MIME_TYPE));
                }
            }
        }
        return mimeType;
    }

    public static String getFileName(String filePath) {
        return last(filePath.split("/"));
    }

    /**
     * Get the last element of a String array.
     */
    private static String last(String[] strings) {
        return strings[strings.length - 1];
    }

    private static void copyExifData(ExifInterface sourceExif, ExifInterface destExif, Bitmap scaledBitmap) {
        if (sourceExif == null || destExif == null) {
            return;
        }
        for (String tag : EXIF_TAGS) {
            String value = sourceExif.getAttribute(tag);
            if (value != null) {
                destExif.setAttribute(tag, value);
            }
        }

        // Update dimensions for the scaled image
        destExif.setAttribute(ExifInterface.TAG_IMAGE_WIDTH, String.valueOf(scaledBitmap.getWidth()));
        destExif.setAttribute(ExifInterface.TAG_IMAGE_LENGTH, String.valueOf(scaledBitmap.getHeight()));
    }

    /**
     * @return whether or not originalImage was scaled down according to maxDimen, and saved to
     * the location given by finalFilePath
     */
    public static boolean scaleAndSaveImage(File originalImage, String finalFilePath,
                                            int maxDimen) {
        String extension = getExtension(originalImage.getAbsolutePath());
        ImageType type = ImageType.fromExtension(extension);
        if (type == null) {
            // The selected image is not of a type that can be decoded to or from a bitmap
            Log.i(LOG_TOKEN, "Could not scale image " + originalImage.getAbsolutePath() + " due to incompatible extension");
            return false;
        }

        // Read original EXIF data form the original image file
        ExifInterface originalExif = null;
        try {
            originalExif = new ExifInterface(originalImage.getAbsolutePath());
        } catch (IOException e) {
            Logger.exception("Failed to read EXIF data", e);
        }

        Pair<Bitmap, Boolean> bitmapAndScaledBool = MediaUtil.inflateImageSafe(originalImage.getAbsolutePath());
        if (bitmapAndScaledBool.second) {
            Logger.log(LogTypes.TYPE_FORM_ENTRY,
                    "An image captured during form entry was too large to be processed at its original size, and had to be downsized");
        }
        Bitmap scaledBitmap = getBitmapScaledByMaxDimen(bitmapAndScaledBool.first, maxDimen);

        // Save scaled image and copy EXIF data
        File scaledFile = new File(finalFilePath);
        if (scaledBitmap != null) {
            // Write this scaled bitmap to the final file location
            try {
                writeBitmapToDiskAndCleanupHandles(scaledBitmap, type, scaledFile);
                ExifInterface newExif = new ExifInterface(scaledFile.getAbsolutePath());
                copyExifData(originalExif, newExif, scaledBitmap);
                newExif.saveAttributes();
                return true;
            } catch (Exception e) {
                e.printStackTrace();
                return false;
            }
        }
        return false;
    }

    public static void writeBitmapToDiskAndCleanupHandles(Bitmap bitmap, ImageType type,
                                                          File location) throws IOException {
        FileOutputStream out = null;
        try {
            out = new FileOutputStream(location);
            bitmap.compress(type.getCompressFormat(), 100, out);
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

    /**
     * Progressively scales down a bitmap to avoid moiré patterns by using a step-wise approach.
     * This method first performs progressive halving until the dimensions are close to the target,
     * then completes the scaling with a final resize to exactly match the target dimensions.
     * The step-wise approach reduces aliasing artifacts that would occur with direct scaling,
     * particularly important for images with fine patterns or textures.
     *
     * @param originalBitmap The source bitmap to downscale
     * @param targetWidth    The desired width of the resulting bitmap
     * @param targetHeight   The desired height of the resulting bitmap
     * @return A downscaled bitmap that matches the target dimensions
     */
    private static Bitmap stepDownscale(Bitmap originalBitmap, int targetWidth, int targetHeight) {
        Bitmap currentBitmap = originalBitmap;
        int height = originalBitmap.getHeight();
        int width = originalBitmap.getWidth();

        // First do progressive halving until we get close
        while (height > targetHeight * 2 && width > targetWidth * 2) {
            height /= 2;
            width /= 2;

            Bitmap tempBitmap = Bitmap.createScaledBitmap(currentBitmap, width, height, true);

            if (currentBitmap != originalBitmap && !currentBitmap.isRecycled()) {
                currentBitmap.recycle();
            }

            currentBitmap = tempBitmap;
        }

        // Final step to exactly match target dimensions
        if (width != targetWidth || height != targetHeight) {
            Bitmap finalBitmap = Bitmap.createScaledBitmap(currentBitmap, targetWidth, targetHeight, false);

            if (currentBitmap != originalBitmap && !currentBitmap.isRecycled()) {
                currentBitmap.recycle();
            }

            return finalBitmap;
        }

        return currentBitmap;
    }


    /**
     * Attempts to scale down an image file based on the max dimension given, using the following
     * logic: If at least one of the dimensions of the original image exceeds the max dimension
     * given, then make the larger side's dimension equal to the max dimension, and scale down the
     * smaller side such that the original aspect ratio is maintained.
     *
     * @param maxDimen - the largest dimension that we want either side of the image to have
     * @return A scaled down bitmap, or null if no scale-down is needed
     */
    private static Bitmap getBitmapScaledByMaxDimen(Bitmap originalBitmap, int maxDimen) {
        if (originalBitmap == null) {
            return null;
        }
        int height = originalBitmap.getHeight();
        int width = originalBitmap.getWidth();
        int sideToScale = Math.max(height, width);
        int otherSide = Math.min(height, width);

        if (sideToScale > maxDimen) {
            // If the larger side exceeds our max dimension, scale down accordingly
            double aspectRatio = ((double)otherSide) / sideToScale;
            sideToScale = maxDimen;
            otherSide = (int)Math.floor(maxDimen * aspectRatio);
            int targetWidth, targetHeight;
            if (width > height) {
                // if width was the side that got scaled
                targetWidth = sideToScale;
                targetHeight = otherSide;
            } else {
                targetWidth = otherSide;
                targetHeight = sideToScale;
            }
            return stepDownscale(originalBitmap, targetWidth, targetHeight);
        } else {
            return null;
        }
    }


    public static boolean isFileOversized(File mf) {
        double length = getFileSize(mf);
        return length > WARNING_SIZE;
    }

    public static double getFileSize(File mf) {
        return mf.length() / 1024;
    }

    public static double getFileSizeInMegs(File mf) {
        return bytesToMeg(mf.length());
    }

    private static final long MEGABYTE_IN_BYTES = 1024L * 1024L;

    public static long bytesToMeg(long bytes) {
        return bytes / MEGABYTE_IN_BYTES;
    }

    public static boolean isFileTooLargeToUpload(File mf) {
        return mf.length() > FormUploadUtil.MAX_BYTES;
    }

    /**
     * @return A platform-dependent URI for the file at the provided URI. If using SDK24+ only files
     * supported by a FileProvider are able to be shared externally by these URI's
     */
    public static Uri getUriForExternalFile(Context context, File file) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            return FileProvider.getUriForFile(context,
                    context.getApplicationContext().getPackageName() + ".external.files.provider",
                    file);
        } else {
            return Uri.fromFile(file);
        }
    }

    /**
     * Makes a copy of file represented by inputStream to dstFile
     *
     * @param inputStream inputStream for File that needs to be copied
     * @param dstFile     destination File where we need to copy the inputStream
     */
    public static void copyFile(InputStream inputStream, File dstFile) throws IOException {
        if (inputStream == null) return;
        OutputStream outputStream = new FileOutputStream(dstFile);
        StreamsUtil.writeFromInputToOutputUnmanaged(inputStream, outputStream);
        inputStream.close();
        outputStream.close();
    }

    public static boolean isSupportedMultiMediaFile(Context context, Uri media) {
        try {
            String fileName = getFileName(context, media);
            return FormUploadUtil.isSupportedMultimediaFile(fileName);
        } catch (FileExtensionNotFoundException e) {
            return false;
        }
    }

    /**
     * Tries to get a filePath from an intent returned from file provider and sets it to the given  <code>filePathEditText</code>
     *
     * @param context          Context of the Activity File Provider returned to
     * @param intent           Intent returned from File Provider
     * @param filePathEditText EditText where we need to show the file path
     */
    public static void updateFileLocationFromIntent(Context context, Intent intent, EditText filePathEditText) {
        String filePath = getFileLocationFromIntent(intent);
        if (filePath == null) {
            // issue getting the filepath uri from file browser callout result
            Toast.makeText(context,
                    Localization.get("file.invalid.path"),
                    Toast.LENGTH_SHORT).show();
        } else {
            filePathEditText.setText(filePath);
        }
    }

    // get a filePath from an intent returned from file provider
    @Nullable
    public static String getFileLocationFromIntent(Intent intent) {
        // Android versions 4.4 and up sometimes don't return absolute
        // filepaths from the file chooser. So resolve the URI into a
        // valid file path.
        Uri uriPath = intent.getData();
        if (uriPath != null) {
            String filePath;
            try {
                filePath = UriToFilePath.getPathFromUri(CommCareApplication.instance(), uriPath);
            } catch (UriToFilePath.NoDataColumnForUriException e) {
                filePath = uriPath.toString();
            }
            return isValidFileLocation(filePath) ? filePath : null;
        }
        return null;
    }

    // Retruns true if location is either a content Uri or a valid file path
    public static boolean isValidFileLocation(String location) {
        return location != null && (location.startsWith("content://") || new File(location).exists());
    }

    // returns the duration for a media file otherwise -1 in case of an error
    public static long getDuration(File file) {
        try {
            MediaMetadataRetriever mediaMetadataRetriever = new MediaMetadataRetriever();
            mediaMetadataRetriever.setDataSource(file.getAbsolutePath());
            String durationStr = mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            return Long.parseLong(durationStr);
        } catch (Exception e) {
            Logger.exception("Exception while trying to get duration of a media file", e);
            return -1;
        }
    }

    public static String getMimeType(String filePath) {
        String extension = MimeTypeMap.getFileExtensionFromUrl(filePath);
        String mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
        return mimeType;
    }

    /**
     * @param file
     * @return Boolean indicating whether the method successfully added te file to content provider.
     */

    /**
     * This method will add the file to the content provider.
     * NOTE:- Currently it only support Audio and Video.
     *
     * @param file The File that needs to be inserted to the ContentProvider.
     * @throws UnsupportedMediaException is raised if file other than audio or video is sent to this method.
     * @throws FileNotFoundException     is raised if file doesn't exist.
     */
    public static void addMediaToGallery(Context context, File file) throws
            UnsupportedMediaException, FileNotFoundException {
        if (!file.exists()) {
            throw new FileNotFoundException("Couldn't find file");
        }
        String mimeType = getMimeType(file.getAbsolutePath());

        if (mimeType.startsWith("video")) {
            ContentValues values = new ContentValues(6);
            values.put(MediaStore.Video.Media.TITLE, file.getName());
            values.put(MediaStore.Video.Media.DISPLAY_NAME, file.getName());
            values.put(MediaStore.Video.Media.DATE_ADDED, System.currentTimeMillis());
            values.put(MediaStore.Video.Media.DATA, file.getAbsolutePath());
            values.put(MediaStore.Video.Media.MIME_TYPE, mimeType);
            Uri mediaUri = context.getContentResolver().insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values);
            Log.i("FileUtil", "Inserting video returned uri = " + (mediaUri == null ? "null" : mediaUri.toString()));
        } else if (mimeType.startsWith("audio")) {
            ContentValues values = new ContentValues(6);
            values.put(MediaStore.Audio.Media.TITLE, file.getName());
            values.put(MediaStore.Audio.Media.DISPLAY_NAME, file.getName());
            values.put(MediaStore.Audio.Media.DATE_ADDED, System.currentTimeMillis());
            values.put(MediaStore.Audio.Media.DATA, file.getAbsolutePath());
            Uri mediaUri = context.getContentResolver().insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values);
            Log.i("FileUtil", "Inserting audio returned uri = " + (mediaUri == null ? "null" : mediaUri.toString()));
        } else {
            throw new UnsupportedMediaException("Doesn't support file with mimeType: " + mimeType);
        }
    }

    /**
     * Returns true only when we're certain that the file size is too large.
     * <p> https://developer.android.com/training/secure-file-sharing/retrieve-info.html#RetrieveFileInfo
     */
    public static boolean isFileTooLargeToUpload(ContentResolver contentResolver, Uri uri) {
        try (Cursor returnCursor = contentResolver.query(uri, null, null, null, null)) {
            if (returnCursor == null || returnCursor.getCount() <= 0) {
                return false;
            }
            int sizeIndex = returnCursor.getColumnIndex(OpenableColumns.SIZE);
            returnCursor.moveToFirst();
            return returnCursor.getLong(sizeIndex) > FormUploadUtil.MAX_BYTES;
        }
    }
}
