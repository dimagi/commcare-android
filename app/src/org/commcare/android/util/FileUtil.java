package org.commcare.android.util;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.Log;

import org.commcare.resources.model.MissingMediaException;
import org.commcare.resources.model.Resource;
import org.javarosa.core.reference.InvalidReferenceException;
import org.javarosa.core.reference.Reference;
import org.javarosa.core.reference.ReferenceManager;
import org.javarosa.core.util.PropertyUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Vector;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;

/**
 * @author ctsims
 */
public class FileUtil {
    
    public static final String LOG_TOKEN = "cc-file-util";

    public static boolean createFolder(String path) {
            boolean made = true;
            File dir = new File(path);
            if (!dir.exists()) {
                made = dir.mkdirs();
            }
            return made;
    }

    public static boolean deleteFileOrDir(String path) {
        return deleteFileOrDir(new File(path));
    }

    // Returns true if the file and all of its contents were deleted successfully, false otherwise
    public static boolean deleteFileOrDir(File f) {
        if (!f.exists()) {
            return true;
        }
        if (f.isDirectory()) {
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
        if(extendedPath == null) { return true;}
        
        //Something's weird, bail!
        if(!fullPath.contains(extendedPath)) { return true;}
        
        //Get the root that we should stop at
        File terminal = new File(fullPath.replace(extendedPath, ""));
        
        File walker = new File(fullPath);
        
        //technically we shouldn't ever hit the first case here, but also don't wanna get stuck by a weird equality bug. 
        while(walker != null && !terminal.equals(walker)) {
            if(walker.isDirectory()) {
                //only wipe out empty directories.
                if(walker.list().length == 0) {
                    if(!walker.delete()) {
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

            int length = (int) lLength;

            InputStream is = null;
            is = new FileInputStream(file);

            int l = 0;
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

    private static final String illegalChars = "'*','+'~|<> !?:./\\";
    public static String SanitizeFileName(String input) {
        for(char c : illegalChars.toCharArray()) {
            input = input.replace(c, '_');
        }
        return input;
    }

    public static void copyFile(File oldPath, File newPath) throws IOException {
        copyFile(oldPath, newPath, null, null);
    }

    public static void copyFile(File oldPath, File newPath, Cipher oldRead, Cipher newWrite) throws IOException {

        if(!newPath.createNewFile()) { throw new IOException("Couldn't create new file @ " + newPath.toString()); }

        InputStream is = null;
        OutputStream os = null;
        try {

            is = new FileInputStream(oldPath);
            if(oldRead != null) {
                is = new CipherInputStream(is, oldRead);
            }

            os = new FileOutputStream(newPath);
            if(newWrite != null) {
                os = new CipherOutputStream(os, newWrite);
            }

            AndroidStreamUtil.writeFromInputToOutput(is, os);
        } finally {
            try{
                if(is != null) {
                    is.close();
                }
            } catch(IOException e) {

            }

            try{
                if(os != null) {
                    os.close();
                }
            } catch(IOException e) {

            }
        }
    }
        
    /**
     * Get a new, clean location to put a file in the same path as the incoming file
     *
     * @param f The existing file
     * @param slug A new chunk to append to the file name
     * @param removeExisting Whether to remove any files which already appear in this location.
     * If false, the method will continue trying to generate new paths until there is no conflict
     *
     * @return A new file location which does not reference an existing file.
     */
    public static File getNewFileLocation(File f, String slug, boolean removeExisting) {
        if(slug == null) {
            slug = PropertyUtils.genGUID(5);
        }
        String name = f.getName();

        int lastDot = name.lastIndexOf(".");
        if(lastDot != -1) {
            String prefix = name.substring(0, lastDot);
            String postfix = name.substring(lastDot);

            name = prefix + "_" + slug + postfix;
        } else {
            name = name + "_" + slug;
        }

        File newLocation = new File(f.getParent() + File.separator + name);
        if(newLocation.exists()) {
            if(removeExisting) {
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

        if(oldFolder.listFiles() != null) {
            //Start copying over files
            for(File oldFile : oldFolder.listFiles()) {
                File newFile = new File(newFolder.getPath() + File.separator + oldFile.getName());
                if(oldFile.isDirectory()) {
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
        final ArrayList<String> out = new ArrayList<String>();
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

    public static void checkReferenceURI(Resource r, String URI, Vector<MissingMediaException> problems) throws IOException{
        try{
            Reference mRef = ReferenceManager._().DeriveReference(URI);

            if(!mRef.doesBinaryExist()){
                String mLocalReference = mRef.getLocalURI();
                problems.addElement(new MissingMediaException(r,"Missing external media: " + mLocalReference, mLocalReference));
            }

        } catch(InvalidReferenceException ire){
            //do nothing for now
        }
    }

    /**
     * Ensure that everything between "localpart" and f exists
     * and create it if not.
     */
    public static void ensureFilePathExists(File f) {
        File folder = f.getParentFile();
        if(folder != null) {
            //Don't worry about return value
            folder.mkdirs();
        }
    }


    /*
     * if we are on KitKat we need use the new API to find the mounted roots, then append our application
     * specific path that we're allowed to write to
     */
    @SuppressLint("NewApi")
    private static String getExternalDirectoryKitKat(Context c){
        File[] extMounts = c.getExternalFilesDirs(null);
        // first entry is emualted storage. Second if it exists is secondary (real) SD.

        if(extMounts.length <2){
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
        if(sdRoot == null){
            return null;
        }

        String domainedFolder = sdRoot.getAbsolutePath() + "/Android/data/org.commcare.dalvik";
        return domainedFolder;
    }
    /*
     * If we're on KitKat use the new OS path
     */
    public static String getDumpDirectory(Context c){
        if (android.os.Build.VERSION.SDK_INT>=19){
            return getExternalDirectoryKitKat(c);
        } else{
            ArrayList<String> mArrayList = getExternalMounts();
            if (mArrayList.size() > 0){
                return getExternalMounts().get(0);
            }
            return null;
        }
    }


}
