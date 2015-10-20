/*
 * Copyright (C) 2009 University of Washington
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package org.odk.collect.android.utilities;

import android.annotation.SuppressLint;
import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.util.Log;

import org.javarosa.core.io.StreamsUtil;
import org.javarosa.xform.parse.XFormParseException;
import org.javarosa.xform.parse.XFormParser;
import org.kxml2.kdom.Document;
import org.kxml2.kdom.Element;
import org.kxml2.kdom.Node;
import org.odk.collect.android.widgets.ImageWidget;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.nio.channels.FileChannel;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.SecretKeySpec;

/**
 * Static methods used for common file operations.
 * 
 * @author Carl Hartung (carlhartung@gmail.com)
 */
public class FileUtils {
    private final static String t = "FileUtils";

    // Used to validate and display valid form names.
    public static final String VALID_FILENAME = "[ _\\-A-Za-z0-9]*.x[ht]*ml";
    
    //highest allowable file size without warning
    public static int WARNING_SIZE = 3000;

    
    public static boolean createFolder(String path) {
        boolean made = true;
        File dir = new File(path);
        if (!dir.exists()) {
            made = dir.mkdirs();
        }
        return made;
    }

    public static byte[] getFileAsBytes(File file, SecretKeySpec symetricKey) {
        byte[] bytes = null;
        InputStream is = null;
        try {
            is = new FileInputStream(file);
            if(symetricKey != null) {
                Cipher cipher = Cipher.getInstance("AES");
                cipher.init(Cipher.DECRYPT_MODE, symetricKey);
                is = new CipherInputStream(is, cipher);
            }
            
            //CTS - Removed a lot of weird checks  here. file size < max int? We're shoving this 
            //form into a _Byte array_, I don't think there's a lot of concern than 2GB of data
            //are gonna sneak by.
            
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            
            try {
                StreamsUtil.writeFromInputToOutput(is, baos);
                bytes = baos.toByteArray();
            } catch (IOException e) {
                Log.e(t, "Cannot read " + file.getName());
                e.printStackTrace();
                return null;
            }

            //CTS - Removed the byte array length check here. Plenty of
            //files are smaller than their contents (padded encryption data, etc),
            //so you can't actually know that's correct. We should be relying on the
            //methods we use to read data to make sure it's all coming out.

            return bytes;

        } catch (FileNotFoundException e) {
            Log.e(t, "Cannot find " + file.getName());
            e.printStackTrace();
            return null;

        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        } catch (NoSuchPaddingException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        } catch (InvalidKeyException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        } finally {
            // Close the input stream
            try {
                is.close();
            } catch (IOException e) {
                Log.e(t, "Cannot close input stream for " + file.getName());
                e.printStackTrace();
                return null;
            }
        }
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
                Log.e(t, "File " + file.getName() + "is too large");
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

    public static String getExtension(String filePath) {
        if (filePath.contains(".")) {
            return last(filePath.split("\\."));
        }
        return "";
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
        ImageWidget.ImageType type = ImageWidget.ImageType.fromExtension(extension);
        if (type == null) {
            // The selected image is not of a type that can be decoded to or from a bitmap
            Log.i(t, "Could not scale image " + originalImage.getAbsolutePath() + " due to incompatible extension");
            return false;
        }

        Bitmap bitmap = BitmapFactory.decodeFile(originalImage.getAbsolutePath());
        Bitmap scaledBitmap = getBitmapScaledByMaxDimen(bitmap, maxDimen);
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

    /**
     * Attempts to scale down an image file based on the max dimension given, using the following
     * logic: If at least one of the dimensions of the original image exceeds the max dimension
     * given, then make the larger side's dimension equal to the max dimension, and scale down the
     * smaller side such that the original aspect ratio is maintained.
     *
     * @param maxDimen - the largest dimension that we want either side of the image to have
     * @return A scaled down bitmap, or null if no scale-down is needed
     *
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
            double aspectRatio = ((double) otherSide) / sideToScale;
            sideToScale = maxDimen;
            otherSide = (int) Math.floor(maxDimen * aspectRatio);
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

    /**
     * Copies from sourceFile to destFile (either a directory, or a path
     * to the new file) 
     * 
     * @param sourceFile A file pointer to a file on the file system
     * @param destFile Either a file or directory. If a directory, the
     * file name will be taken from the source file 
     */
    public static void copyFile(File sourceFile, File destFile) {
        if (sourceFile.exists()) {
            if(destFile.isDirectory()) {
                destFile = new File(destFile, sourceFile.getName());
            }
            
            FileChannel src;
            try {
                src = new FileInputStream(sourceFile).getChannel();
                FileChannel dst = new FileOutputStream(destFile).getChannel();
                dst.transferFrom(src, 0, src.size());
                src.close();
                dst.close();
            } catch (FileNotFoundException e) {
                Log.e(t, "FileNotFoundExeception while copying audio");
                e.printStackTrace();
            } catch (IOException e) {
                Log.e(t, "IOExeception while copying audio");
                e.printStackTrace();
            }
        } else {
            Log.e(t, "Source file does not exist: " + sourceFile.getAbsolutePath());
        }

    }

    public static String FORMID = "formid";
    public static String UI = "uiversion";
    public static String MODEL = "modelversion";
    public static String TITLE = "title";
    public static String SUBMISSIONURI = "submission";
    public static String BASE64_RSA_PUBLIC_KEY = "base64RsaPublicKey";
    
    public static HashMap<String, String> parseXML(File xmlFile) {
        HashMap<String, String> fields = new HashMap<String, String>();
        InputStream is;
        try {
            is = new FileInputStream(xmlFile);
        } catch (FileNotFoundException e1) {
            throw new IllegalStateException(e1);
        }

        InputStreamReader isr;
        try {
            isr = new InputStreamReader(is, "UTF-8");
        } catch (UnsupportedEncodingException uee) {
            Log.w(t, "UTF 8 encoding unavailable, trying default encoding");
            isr = new InputStreamReader(is);
        }

        if (isr != null) {

            Document doc;
            try {
                doc = XFormParser.getXMLDocument(isr);
            } catch(IOException e) {
                e.printStackTrace();
                throw new XFormParseException("IO Exception during form parsing: " + e.getMessage());
            } finally {
                try {
                    isr.close();
                } catch (IOException e) {
                    Log.w(t, xmlFile.getAbsolutePath() + " Error closing form reader");
                    e.printStackTrace();
                }
            }

            String xforms = "http://www.w3.org/2002/xforms";
            String html = doc.getRootElement().getNamespace();
            
            Element head = doc.getRootElement().getElement(html, "head");
            Element title = head.getElement(html, "title");
            if (title != null) {
                fields.put(TITLE, XFormParser.getXMLText(title, true));
            } 
            
            Element model = getChildElement(head, "model");
            Element cur = getChildElement(model,"instance");
            
            int idx = cur.getChildCount();
            int i;
            for (i = 0; i < idx; ++i) {
                if (cur.isText(i))
                    continue;
                if (cur.getType(i) == Node.ELEMENT) {
                    break;
                }
            }

            if (i < idx) {
                cur = cur.getElement(i); // this is the first data element
                String id = cur.getAttributeValue(null, "id");
                String xmlns = cur.getNamespace();
                String modelVersion = cur.getAttributeValue(null, "version");
                String uiVersion = cur.getAttributeValue(null, "uiVersion");

                fields.put(FORMID, (id == null) ? xmlns : id);
                fields.put(MODEL, (modelVersion == null) ? null : modelVersion);
                fields.put(UI, (uiVersion == null) ? null : uiVersion);
            } else {
                throw new IllegalStateException(xmlFile.getAbsolutePath() + " could not be parsed");
            }
            try {
                Element submission = model.getElement(xforms, "submission");
                String submissionUri = submission.getAttributeValue(null, "action");
                fields.put(SUBMISSIONURI, (submissionUri == null) ? null : submissionUri);
                String base64RsaPublicKey = submission.getAttributeValue(null, "base64RsaPublicKey");
                fields.put(BASE64_RSA_PUBLIC_KEY,
                  (base64RsaPublicKey == null || base64RsaPublicKey.trim().length() == 0) 
                  ? null : base64RsaPublicKey.trim());
            } catch (Exception e) {
                Log.i(t, xmlFile.getAbsolutePath() + " does not have a submission element");
                // and that's totally fine.
            }

        }
        return fields;
    }

    // needed because element.getelement fails when there are attributes
    private static Element getChildElement(Element parent, String childName) {
        Element e = null;
        int c = parent.getChildCount();
        int i = 0;
        for (i = 0; i < c; i++) {
            if (parent.getType(i) == Node.ELEMENT) {
                if (parent.getElement(i).getName().equalsIgnoreCase(childName)) {
                    return parent.getElement(i);
                }
            }
        }
        return e;
    }
    
    public static boolean isFileOversized(File mf){
        double length = getFileSize(mf);
        return length > WARNING_SIZE;
    }
    
    public static double getFileSize(File mf){
        return mf.length()/(1024);
    }

    /* 
     * @param context The context.
     * @param uri The Uri to query.
     * @author paulburke
     * 
     * Get's the correct path for different Android API levels
     * 
     */
    @SuppressLint("NewApi")
    public static String getPath(final Context context, final Uri uri) {

        final boolean isKitKat = Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT;

        // DocumentProvider
        if (isKitKat && DocumentsContract.isDocumentUri(context, uri)) {
            // ExternalStorageProvider
            if (isExternalStorageDocument(uri)) {
                final String docId = DocumentsContract.getDocumentId(uri);
                final String[] split = docId.split(":");
                final String type = split[0];

                if ("primary".equalsIgnoreCase(type)) {
                    return Environment.getExternalStorageDirectory() + "/" + split[1];
                }

                // TODO handle non-primary volumes
            }
            // DownloadsProvider
            else if (isDownloadsDocument(uri)) {

                final String id = DocumentsContract.getDocumentId(uri);
                final Uri contentUri = ContentUris.withAppendedId(
                        Uri.parse("content://downloads/public_downloads"), Long.valueOf(id));

                return getDataColumn(context, contentUri, null, null);
            }
            // MediaProvider
            else if (isMediaDocument(uri)) {
                final String docId = DocumentsContract.getDocumentId(uri);
                final String[] split = docId.split(":");
                final String type = split[0];

                Uri contentUri = null;
                if ("image".equals(type)) {
                    contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
                } else if ("video".equals(type)) {
                    contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
                } else if ("audio".equals(type)) {
                    contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
                }

                final String selection = "_id=?";
                final String[] selectionArgs = new String[] {
                        split[1]
                };

                return getDataColumn(context, contentUri, selection, selectionArgs);
            }
        }
        // MediaStore (and general)
        else if ("content".equalsIgnoreCase(uri.getScheme())) {

            // Return the remote address
            if (isGooglePhotosUri(uri))
                return uri.getLastPathSegment();

            return getDataColumn(context, uri, null, null);
        }
        // File
        else if ("file".equalsIgnoreCase(uri.getScheme())) {
            return uri.getPath();
        }

        return null;
    }

    /**
     * Get the value of the data column for this Uri. This is useful for
     * MediaStore Uris, and other file-based ContentProviders.
     *
     * @param context The context.
     * @param uri The Uri to query.
     * @param selection (Optional) Filter used in the query.
     * @param selectionArgs (Optional) Selection arguments used in the query.
     * @return The value of the _data column, which is typically a file path.
     */
    public static String getDataColumn(Context context, Uri uri, String selection,
            String[] selectionArgs) {

        Cursor cursor = null;
        final String column = "_data";
        final String[] projection = {
                column
        };

        try {
            cursor = context.getContentResolver().query(uri, projection, selection, selectionArgs,
                    null);
            if (cursor != null && cursor.moveToFirst()) {
                final int index = cursor.getColumnIndexOrThrow(column);
                return cursor.getString(index);
            }
        } finally {
            if (cursor != null)
                cursor.close();
        }
        return null;
    }


    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is ExternalStorageProvider.
     */
    public static boolean isExternalStorageDocument(Uri uri) {
        return "com.android.externalstorage.documents".equals(uri.getAuthority());
    }

    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is DownloadsProvider.
     */
    public static boolean isDownloadsDocument(Uri uri) {
        return "com.android.providers.downloads.documents".equals(uri.getAuthority());
    }

    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is MediaProvider.
     */
    public static boolean isMediaDocument(Uri uri) {
        return "com.android.providers.media.documents".equals(uri.getAuthority());
    }

    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is Google Photos.
     */
    public static boolean isGooglePhotosUri(Uri uri) {
        return "com.google.android.apps.photos.content".equals(uri.getAuthority());
    }
}
