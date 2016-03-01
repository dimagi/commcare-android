package org.odk.collect.android.activities.components;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;
import android.util.Log;

import org.commcare.activities.FormEntryActivity;
import org.commcare.dalvik.odk.provider.FormsProviderAPI;
import org.commcare.dalvik.odk.provider.InstanceProviderAPI;

import java.io.File;

public class FormFileSystemHelpers {
    private static final String TAG = FormFileSystemHelpers.class.getSimpleName();

    public static String getFormPath(Context context, Uri uri) throws FormEntryActivity.FormQueryException {
        Cursor c = null;
        try {
            c = context.getContentResolver().query(uri, null, null, null, null);
            if (c == null) {
                throw new FormEntryActivity.FormQueryException("Bad URI: resolved to null");
            } else if (c.getCount() != 1) {
                throw new FormEntryActivity.FormQueryException("Bad URI: " + uri);
            } else {
                c.moveToFirst();
                return c.getString(c.getColumnIndex(FormsProviderAPI.FormsColumns.FORM_FILE_PATH));
            }
        } finally {
            if (c != null) {
                c.close();
            }
        }
    }

    public static int getFormInstanceCount(Context context,
                                           String instancePath,
                                           Uri instanceProviderContentURI) {
        String selection =
                InstanceProviderAPI.InstanceColumns.INSTANCE_FILE_PATH + " like '"
                        + instancePath + "'";
        Cursor c = null;
        int instanceCount = 0;
        try {
            c = context.getContentResolver().query(instanceProviderContentURI, null, selection, null, null);
            if (c != null) {
                instanceCount = c.getCount();
            }
        } finally {
            if (c != null) {
                c.close();
            }
        }
        return instanceCount;
    }

    public static void removeMediaAttachedToUnsavedForm(Context context,
                                                        String instancePath,
                                                        Uri instanceProviderContentURI) {
        int instanceCount = getFormInstanceCount(context, instancePath, instanceProviderContentURI);
        // if it's not already saved, erase everything
        if (instanceCount < 1) {
            int images = 0;
            int audio = 0;
            int video = 0;
            // delete media first
            String instanceFolder =
                    instancePath.substring(0,
                            instancePath.lastIndexOf("/") + 1);
            Log.i(TAG, "attempting to delete: " + instanceFolder);

            String where =
                    MediaStore.Images.Media.DATA + " like '" + instanceFolder + "%'";


            // images
            Cursor imageCursor = null;
            try {
                String[] projection = {
                        MediaStore.Images.ImageColumns._ID
                };
                imageCursor = context.getContentResolver().query(
                        android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        projection, where, null, null);
                if (imageCursor != null && imageCursor.getCount() > 0) {
                    imageCursor.moveToFirst();
                    int columnIndex =
                            imageCursor.getColumnIndex(MediaStore.Images.ImageColumns._ID);
                    String id = imageCursor.getString(columnIndex);

                    Log.i(TAG,
                            "attempting to delete: "
                                    + Uri.withAppendedPath(
                                    android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                                    id));
                    images = context.getContentResolver()
                            .delete(
                                    Uri.withAppendedPath(
                                            android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                                            id), null, null);
                }
            } finally {
                if (imageCursor != null) {
                    imageCursor.close();
                }
            }

            // audio
            Cursor audioCursor = null;
            try {
                String[] projection = {
                        MediaStore.Audio.AudioColumns._ID
                };
                audioCursor = context.getContentResolver().query(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        projection, where, null, null);
                if (audioCursor != null && audioCursor.getCount() > 0) {
                    audioCursor.moveToFirst();
                    int columnIndex =
                            audioCursor.getColumnIndex(MediaStore.Audio.AudioColumns._ID);
                    String id = audioCursor.getString(columnIndex);

                    Log.i(
                            TAG,
                            "attempting to delete: "
                                    + Uri.withAppendedPath(
                                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                                    id));
                    audio =
                            context.getContentResolver()
                                    .delete(
                                            Uri.withAppendedPath(
                                                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                                                    id), null, null);
                }
            } finally {
                if (audioCursor != null) {
                    audioCursor.close();
                }
            }

            // video
            Cursor videoCursor = null;
            try {
                String[] projection = {
                        MediaStore.Video.VideoColumns._ID
                };
                videoCursor = context.getContentResolver().query(
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                        projection, where, null, null);
                if (videoCursor != null && videoCursor.getCount() > 0) {
                    videoCursor.moveToFirst();
                    int columnIndex =
                            videoCursor.getColumnIndex(MediaStore.Video.VideoColumns._ID);
                    String id = videoCursor.getString(columnIndex);

                    Log.i(
                            TAG,
                            "attempting to delete: "
                                    + Uri.withAppendedPath(
                                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                                    id));
                    video =
                            context.getContentResolver()
                                    .delete(
                                            Uri.withAppendedPath(
                                                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                                                    id), null, null);
                }
            } finally {
                if (videoCursor != null) {
                    videoCursor.close();
                }
            }

            Log.i(TAG, "removed from content providers: " + images
                    + " image files, " + audio + " audio files,"
                    + " and " + video + " video files.");
            File f = new File(instanceFolder);
            if (f.exists() && f.isDirectory()) {
                for (File del : f.listFiles()) {
                    Log.i(TAG, "deleting file: " + del.getAbsolutePath());
                    del.delete();
                }
                f.delete();
            }
        }
    }
}
