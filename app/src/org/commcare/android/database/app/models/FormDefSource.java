package org.commcare.android.database.app.models;


import android.content.ContentValues;
import android.database.SQLException;
import android.os.Environment;
import android.util.Log;
import android.util.SparseIntArray;

import net.sqlcipher.Cursor;
import net.sqlcipher.database.SQLiteDatabase;

import org.commcare.models.database.app.DatabaseAppOpenHelper;
import org.commcare.provider.FormsProviderAPI;
import org.commcare.utils.FileUtil;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Performs various operations on 'forms' table in app DB
 */
public class FormDefSource {

    //    public static final String SQL_CREATE_TABLE = "CREATE TABLE " + AppDbContract.FormsColumns.TABLE_NAME + " ("
//            + AppDbContract.FormsColumns._ID + " integer primary key, "
//            + AppDbContract.FormsColumns.COL_DISPLAY_NAME + " text not null, "
//            + AppDbContract.FormsColumns.COL_DISPLAY_SUBTEXT + " text not null, "
//            + AppDbContract.FormsColumns.COL_DESCRIPTION + " text, "
//            + AppDbContract.FormsColumns.COL_JR_FORM_ID + " text not null, "
//            + AppDbContract.FormsColumns.COL_MODEL_VERSION + " integer, "
//            + AppDbContract.FormsColumns.COL_UI_VERSION + " integer, "
//            + AppDbContract.FormsColumns.COL_MD5_HASH + " text not null, "
//            + AppDbContract.FormsColumns.COL_DATE + " integer not null, " // milliseconds
//            + AppDbContract.FormsColumns.COL_FORM_MEDIA_PATH + " text not null, "
//            + AppDbContract.FormsColumns.COL_FORM_FILE_PATH + " text not null, "
//            + AppDbContract.FormsColumns.COL_LANGUAGE + " text, "
//            + AppDbContract.FormsColumns.COL_SUBMISSION_URI + " text, "
//            + AppDbContract.FormsColumns.COL_BASE64_RSA_PUBLIC_KEY + " text, "
//            + AppDbContract.FormsColumns.COL_JRCACHE_FILE_PATH + " text not null );";
//
//    private static final String TAG = FormDefSource.class.getName();
//
    private final DatabaseAppOpenHelper mAppDbHelper;

    public FormDefSource(DatabaseAppOpenHelper appDbHelper) {
        this.mAppDbHelper = appDbHelper;
    }

//    public SparseIntArray getFormDefsByJrFormId(String jrFormId) {
//        SQLiteDatabase db = mAppDbHelper.getReadableDatabase("null");
//        SparseIntArray formdefs = new SparseIntArray();
//        Cursor cursor = null;
//        try {
//            cursor = db.query(AppDbContract.FormsColumns.TABLE_NAME,
//                    new String[]{FormsProviderAPI.FormsColumns._ID},
//                    FormsProviderAPI.FormsColumns.JR_FORM_ID + "=?",
//                    new String[]{jrFormId},
//                    null, null, null);
//
//
//            if (cursor != null && cursor.getCount() > 0) {
//                while (cursor.moveToNext()) {
//                    formdefs.append(cursor.getPosition(), cursor.getInt(cursor.getColumnIndex(AppDbContract.FormsColumns._ID)));
//                }
//            }
//        } finally {
//            if (cursor != null) {
//                cursor.close();
//            }
//            db.close();
//        }
//
//        return formdefs;
//    }

//    public long saveFormDef(ContentValues initialValues) {
//        SQLiteDatabase db = mAppDbHelper.getWritableDatabase("null");
//
//        ContentValues values;
//        if (initialValues != null) {
//            values = new ContentValues(initialValues);
//        } else {
//            values = new ContentValues();
//        }
//
//        // Make sure that the necessary fields are all set
//        if (!values.containsKey(FormsProviderAPI.FormsColumns.DATE)) {
//            values.put(FormsProviderAPI.FormsColumns.DATE, System.currentTimeMillis());
//        }
//
//        if (!values.containsKey(FormsProviderAPI.FormsColumns.DISPLAY_SUBTEXT)) {
//            values.put(FormsProviderAPI.FormsColumns.DISPLAY_SUBTEXT, getDisplaySubtext());
//        }
//
//        // if we don't have a path to the file, the rest are irrelevant.
//        // it should fail anyway because you can't have a null file path.
//        if (values.containsKey(FormsProviderAPI.FormsColumns.FORM_FILE_PATH)) {
//            String filePath = values.getAsString(FormsProviderAPI.FormsColumns.FORM_FILE_PATH);
//            File form = new File(filePath);
//
//            if (!values.containsKey(FormsProviderAPI.FormsColumns.DISPLAY_NAME)) {
//                values.put(FormsProviderAPI.FormsColumns.DISPLAY_NAME, form.getName());
//            }
//
//            // don't let users put in a manual md5 hash
//            if (values.containsKey(FormsProviderAPI.FormsColumns.MD5_HASH)) {
//                values.remove(FormsProviderAPI.FormsColumns.MD5_HASH);
//            }
//
//            String md5 = FileUtil.getMd5Hash(form);
//            values.put(FormsProviderAPI.FormsColumns.MD5_HASH, md5);
//
//            if (!values.containsKey(FormsProviderAPI.FormsColumns.JRCACHE_FILE_PATH)) {
//                values.put(FormsProviderAPI.FormsColumns.JRCACHE_FILE_PATH, getCachePath(md5));
//            }
//            if (!values.containsKey(FormsProviderAPI.FormsColumns.FORM_MEDIA_PATH)) {
//                values.put(FormsProviderAPI.FormsColumns.FORM_MEDIA_PATH, getMediaPath(filePath));
//            }
//        }
//
//        long rowId = db.insert(AppDbContract.FormsColumns.TABLE_NAME, null, values);
//        db.close();
//
//        if (rowId > 0) {
//            return rowId;
//        }
//
//        throw new SQLException("Failed to insert row into FormDefSource");
//    }

//    public int updateFormDef(long formDefId, ContentValues values) {
//        SQLiteDatabase db = mAppDbHelper.getWritableDatabase("null");
//        int count = 0;
//
//        // Whenever file paths are updated, delete the old files.
//        Cursor existingRecord = null;
//        try {
//            existingRecord = db.query(AppDbContract.FormsColumns.TABLE_NAME,
//                    null,
//                    AppDbContract.FormsColumns._ID + "=?",
//                    new String[]{String.valueOf(formDefId)},
//                    null, null, null);
//
//            // This should only ever return 1 record.
//            if (existingRecord.getCount() > 0) {
//                existingRecord.moveToFirst();
//
//                // don't let users manually update md5
//                if (values.containsKey(FormsProviderAPI.FormsColumns.MD5_HASH)) {
//                    values.remove(FormsProviderAPI.FormsColumns.MD5_HASH);
//                }
//
//                // the order here is important (jrcache needs to be before form file)
//                // because we update the jrcache file if there's a new form file
//                if (values.containsKey(FormsProviderAPI.FormsColumns.JRCACHE_FILE_PATH)) {
//                    FileUtil.deleteFileOrDir(existingRecord.getString(existingRecord
//                            .getColumnIndex(FormsProviderAPI.FormsColumns.JRCACHE_FILE_PATH)));
//                }
//
//                if (values.containsKey(FormsProviderAPI.FormsColumns.FORM_FILE_PATH)) {
//                    String formFile = values.getAsString(FormsProviderAPI.FormsColumns.FORM_FILE_PATH);
//                    String oldFile = existingRecord.getString(existingRecord.getColumnIndex(FormsProviderAPI.FormsColumns.FORM_FILE_PATH));
//
//                    try {
//                        if (new File(oldFile).getCanonicalPath().equals(new File(formFile).getCanonicalPath())) {
//                            // Files are the same, so we may have just copied over something we had already
//                        } else {
//                            // New file name. This probably won't ever happen, though.
//                            FileUtil.deleteFileOrDir(oldFile);
//                        }
//                    } catch (IOException ioe) {
//                        //we only get here if we couldn't canonicalize, in which case we can't risk deleting the old file
//                        //so don't do anything.
//                    }
//
//                    // we're updating our file, so update the md5
//                    // and get rid of the cache (doesn't harm anything)
//                    FileUtil.deleteFileOrDir(existingRecord.getString(
//                            existingRecord.getColumnIndex(FormsProviderAPI.FormsColumns.JRCACHE_FILE_PATH)));
//                    String newMd5 = FileUtil.getMd5Hash(new File(formFile));
//                    values.put(FormsProviderAPI.FormsColumns.MD5_HASH, newMd5);
//                    values.put(FormsProviderAPI.FormsColumns.JRCACHE_FILE_PATH, getCachePath(newMd5));
//                }
//
//                // Make sure that the necessary fields are all set
//                if (values.containsKey(FormsProviderAPI.FormsColumns.DATE)) {
//                    values.put(FormsProviderAPI.FormsColumns.DISPLAY_SUBTEXT, getDisplaySubtext());
//                }
//
//                count = db.update(AppDbContract.FormsColumns.TABLE_NAME,
//                        values, FormsProviderAPI.FormsColumns._ID + "=" + formDefId, null);
//            } else {
//                Log.e(TAG, "Attempting to update row that does not exist");
//            }
//        } finally {
//            if (existingRecord != null) {
//                existingRecord.close();
//            }
//        }
//        return count;
//    }

//    private String getCachePath(String md5) {
//        return Environment.getExternalStorageDirectory().getPath() + "odk/.cache" + md5 + ".formdef";
//    }
//
//    private String getDisplaySubtext() {
//        Date today = new Date();
//        String ts = new SimpleDateFormat("EEE, MMM dd, yyyy 'at' HH:mm").format(today);
//        return "Added on " + ts;
//    }
//
//    private String getMediaPath(String filePath) {
//        String pathNoExtension = filePath.substring(0, filePath.lastIndexOf("."));
//        return pathNoExtension + "-media";
//    }
}
