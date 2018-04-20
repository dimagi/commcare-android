package org.commcare.activities.components;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;
import android.util.Log;

import org.commcare.android.database.app.models.FormDefRecord;
import org.commcare.android.database.user.models.FormRecord;
import org.commcare.models.database.SqlStorage;

import java.io.File;

public class FormFileSystemHelpers {
    private static final String TAG = FormFileSystemHelpers.class.getSimpleName();

    public static String getFormDefPath(SqlStorage<FormDefRecord> formDefStorage, int formId) {
        FormDefRecord formDefRecord = FormDefRecord.getFormDef(formDefStorage, formId);
        return formDefRecord.getFilePath();
    }

    public static void removeMediaAttachedToUnsavedForm(Context context, String formRecordPath, SqlStorage<FormRecord> formRecordStorage) {
        FormRecord formRecord = FormRecord.getFormRecord(formRecordStorage, formRecordPath);
        // if it's not already saved, erase everything
        if (formRecord == null) {
            int images = 0;
            int audio = 0;
            int video = 0;
            // delete media first
            String formRecordFolder =
                    formRecordPath.substring(0,
                            formRecordPath.lastIndexOf("/") + 1);
            Log.i(TAG, "attempting to delete: " + formRecordFolder);

            String where =
                    MediaStore.Images.Media.DATA + " like '" + formRecordFolder + "%'";


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
            File f = new File(formRecordFolder);
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
