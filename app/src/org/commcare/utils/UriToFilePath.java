package org.commcare.utils;

import android.annotation.SuppressLint;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.MediaStore;

import java.util.List;

/**
 * Util for turning URIs into a normal file path strings Needed because new
 * versions of Android have a content resolution abstraction layer that doesn't
 * use vanilla file system paths.
 *
 * Taken from aFileChooser by Paul Burke (paulburke.co)
 * https://github.com/iPaulPro/aFileChooser
 * Under the Apache 2.0 license
 */
public class UriToFilePath {
    /**
     * Get a file path from a Uri. This will get the the path for Storage
     * Access Framework Documents, as well as the _data field for the
     * MediaStore and other file-based ContentProviders. Doesn't handle
     * document Uri's on secondary storage.
     *
     * @param context The context.
     * @param uri     The Uri to query.
     * @return Filepath string extracted from the Uri argument. Returns null if
     * filepath couldn't be succesfully extracted.
     */
    @SuppressLint("NewApi")
    public static String getPathFromUri(final Context context, final Uri uri) throws NoDataColumnForUriException {
        final boolean isKitKat = Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT;

        if (isKitKat && DocumentsContract.isDocumentUri(context, uri)) {
            if (isExternalStorageDocument(uri)) {
                final String docId = DocumentsContract.getDocumentId(uri);
                final String[] split = docId.split(":");
                final String type = split[0];

                if ("primary".equalsIgnoreCase(type)) {
                    return Environment.getExternalStorageDirectory() + "/" + split[1];
                }
            } else if (isDownloadsDocument(uri)) {
                final String id = DocumentsContract.getDocumentId(uri);
                Uri contentUri;
                try {
                    contentUri = ContentUris.withAppendedId(
                            Uri.parse("content://downloads/public_downloads"), Long.valueOf(id));
                } catch (NumberFormatException e) {
                    // id is an actual Path instead of a row id, hence use the original uri as it is.
                    contentUri = uri;
                }

                return getDataColumn(context, contentUri, null, null);
            } else if (isMediaDocument(uri)) {
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
                final String[] selectionArgs = new String[]{
                        split[1]
                };

                return getDataColumn(context, contentUri, selection, selectionArgs);
            }
        } else if ("content".equalsIgnoreCase(uri.getScheme())) {
            // Return the remote address
            if (isGooglePhotosUri(uri)) {
                return uri.getLastPathSegment();
            }
            return getDataColumn(context, uri, null, null);
        } else if ("file".equalsIgnoreCase(uri.getScheme())) {
            return uri.getPath();
        }

        return null;
    }

    /**
     * Get the value of the data column for this Uri. This is useful for
     * MediaStore Uris, and other file-based ContentProviders.
     *
     * @param context       The context.
     * @param uri           The Uri to query.
     * @param selection     (Optional) Filter used in the query.
     * @param selectionArgs (Optional) Selection arguments used in the query.
     * @return The value of the _data column, which is typically a file path.
     */
    private static String getDataColumn(Context context, Uri uri, String selection,
                                        String[] selectionArgs) throws NoDataColumnForUriException {
        Cursor cursor = null;
        final String column = "_data";
        final String[] projection = {
                column
        };

        try {
            cursor = context.getContentResolver().query(uri, projection,
                    selection, selectionArgs, null);
            if (cursor != null && cursor.moveToFirst()) {
                try {
                    final int column_index = cursor.getColumnIndexOrThrow(column);
                    return cursor.getString(column_index);
                } catch (IllegalArgumentException e) {
                    throw new NoDataColumnForUriException();
                }
            }
        } finally {
            if (cursor != null)
                cursor.close();
        }
        return null;
    }

    /**
     * @param uri Check this Uri's authority.
     * @return Is this Uri's authority ExternalStorageProvider?
     */
    private static boolean isExternalStorageDocument(Uri uri) {
        return "com.android.externalstorage.documents".equals(uri.getAuthority());
    }

    /**
     * @param uri Check this Uri's authority.
     * @return Is this Uri's authority DownloadsProvider?
     */
    private static boolean isDownloadsDocument(Uri uri) {
        return "com.android.providers.downloads.documents".equals(uri.getAuthority());
    }

    /**
     * @param uri Check this Uri's authority.
     * @return Is this Uri's authority MediaProvider?
     */
    private static boolean isMediaDocument(Uri uri) {
        return "com.android.providers.media.documents".equals(uri.getAuthority());
    }

    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is Google Photos.
     */
    private static boolean isGooglePhotosUri(Uri uri) {
        return "com.google.android.apps.photos.content".equals(uri.getAuthority());
    }

    /**
     * Gives the activities that can handle given intent permissions for given uri. Not doing so will result in a SecurityException from Android N upwards
     *
     * @param context context of the activity requesting resolution for given intent
     * @param intent  intent that needs to get resolved
     * @param uri     uri for which permissions are to be given
     * @param flags   what permissions are to be given
     */
    public static void grantPermissionForUri(Context context, Intent intent, Uri uri, int flags) {
        List<ResolveInfo> resInfoList = context.getPackageManager().queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY);
        for (ResolveInfo resolveInfo : resInfoList) {
            String packageName = resolveInfo.activityInfo.packageName;
            context.grantUriPermission(packageName, uri, flags);
        }
    }

    public static class NoDataColumnForUriException extends Exception {
    }
}
