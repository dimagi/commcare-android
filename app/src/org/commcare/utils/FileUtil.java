package org.commcare.utils;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;

import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;

import android.util.Log;
import android.util.Pair;
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

/**
 * @author ctsims
 */
public class FileUtil {

    private static final int WARNING_SIZE = 3000;

    private static final String LOG_TOKEN = "cc-file-util";

    public static boolean deleteFileOrDir(String path) {
        return deleteFileOrDir(new File(path));
    }

    // Returns true if the file and all of its contents were deleted successfully, false otherwise
    public static boolean deleteFileOrDir(File f) {
        if (!f.exists()) {
            return true;
        }
        if (f.isDirectory() && f.listFiles().length > 0) {
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
     *
     * Used in SD Card functionality to get the location of the SD card for reads and writes
     * Returns a list of available mounts; for our purposes, we just use the first
     */

    public static ArrayList<String> getExternalMounts() {
        final ArrayList<String> out = new ArrayList<>();
        String reg = "(?i).*vold.*(vfat|ntfs|exfat|fat32|ext3|ext4).*rw.*";
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
     * Turn a filepath into a global android URI that can be passed
     * to an intent.
     */
    public static String getGlobalStringUri(String fileLocation) {
        return "file://" + fileLocation;
    }

    public static void checkReferenceURI(Resource r, String URI, Vector<MissingMediaException> problems) throws IOException {
        try {
            Reference mRef = ReferenceManager.instance().DeriveReference(URI);

            if (!mRef.doesBinaryExist()) {
                String mLocalReference = mRef.getLocalURI();
                problems.addElement(new MissingMediaException(r, "Missing external media: " + mLocalReference, mLocalReference));
            }

        } catch (InvalidReferenceException ire) {
            //do nothing for now
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
        if (android.os.Build.VERSION.SDK_INT >= 19) {
            return getExternalDirectoryKitKat(c);
        } else {
            ArrayList<String> mArrayList = getExternalMounts();
            if (mArrayList.size() > 0) {
                return getExternalMounts().get(0);
            }
            return null;
        }
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
        if (filePath.contains(".")) {
            return last(filePath.split("\\."));
        }
        return "";
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

    /**
     * @return whether or not originalImage was scaled down according to maxDimen, and saved to
     * the location given by finalFilePath
     */
    public static boolean scaleAndSaveImage(File originalImage, String finalFilePath, int maxDimen) {
        String extension = getExtension(originalImage.getAbsolutePath());
        ImageType type = ImageType.fromExtension(extension);
        if (type == null) {
            // The selected image is not of a type that can be decoded to or from a bitmap
            Log.i(LOG_TOKEN, "Could not scale image " + originalImage.getAbsolutePath() + " due to incompatible extension");
            return false;
        }

        Pair<Bitmap, Boolean> bitmapAndScaledBool = MediaUtil.inflateImageSafe(originalImage.getAbsolutePath());
        if (bitmapAndScaledBool.second) {
            Logger.log(LogTypes.TYPE_FORM_ENTRY,
                    "An image captured during form entry was too large to be processed at its original size, and had to be downsized");
        }
        Bitmap scaledBitmap = getBitmapScaledByMaxDimen(bitmapAndScaledBool.first, maxDimen);
        if (scaledBitmap != null) {
            // Write this scaled bitmap to the final file location
            try {
                writeBitmapToDiskAndCleanupHandles(scaledBitmap, type, new File(finalFilePath));
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
            if (width > height) {
                // if width was the side that got scaled
                return Bitmap.createScaledBitmap(originalBitmap, sideToScale, otherSide, false);
            } else {
                return Bitmap.createScaledBitmap(originalBitmap, otherSide, sideToScale, false);
            }
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

    public static boolean isSupportedMultiMediaFile(Uri media) {
        try {
            String binaryPath = UriToFilePath.getPathFromUri(CommCareApplication.instance(), media);
            return FormUploadUtil.isSupportedMultimediaFile(binaryPath);
        } catch (UriToFilePath.NoDataColumnForUriException e) {
            // No file path available, work with the media uri instead
            return FormUploadUtil.isSupportedMultimediaFile(media.getPath());
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
}
