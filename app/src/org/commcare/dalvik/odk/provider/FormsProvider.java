package org.commcare.dalvik.odk.provider;

import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.text.TextUtils;
import android.util.Log;

import org.commcare.android.util.FileUtil;
import org.commcare.dalvik.application.CommCareApplication;
import org.commcare.dalvik.odk.provider.FormsProviderAPI.FormsColumns;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;

public class FormsProvider extends ContentProvider {

    private static final String t = "FormsProvider";

    private static final int DATABASE_VERSION = 3;
    private static final String FORMS_TABLE_NAME = "forms";

    private static final HashMap<String, String> sFormsProjectionMap;

    private static final int FORMS = 1;
    private static final int FORM_ID = 2;

    private static final UriMatcher sUriMatcher;

    /**
     * This class helps open, create, and upgrade the database file.
     */
    private static class DatabaseHelper extends SQLiteOpenHelper {

        // the application id of the CCApp for which this db is storing forms
        private final String appId;

        public DatabaseHelper(Context c, String databaseName, String appId) {
            super(c, databaseName, null, DATABASE_VERSION);
            this.appId = appId;
        }

        public String getAppId() {
            return this.appId;
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE " + FORMS_TABLE_NAME + " ("
                    + FormsColumns._ID + " integer primary key, "
                    + FormsColumns.DISPLAY_NAME + " text not null, "
                    + FormsColumns.DISPLAY_SUBTEXT + " text not null, "
                    + FormsColumns.DESCRIPTION + " text, "
                    + FormsColumns.JR_FORM_ID + " text not null, "
                    + FormsColumns.MODEL_VERSION + " integer, "
                    + FormsColumns.UI_VERSION + " integer, "
                    + FormsColumns.MD5_HASH + " text not null, "
                    + FormsColumns.DATE + " integer not null, " // milliseconds
                    + FormsColumns.FORM_MEDIA_PATH + " text not null, "
                    + FormsColumns.FORM_FILE_PATH + " text not null, "
                    + FormsColumns.LANGUAGE + " text, "
                    + FormsColumns.SUBMISSION_URI + " text, "
                    + FormsColumns.BASE64_RSA_PUBLIC_KEY + " text, "
                    + FormsColumns.JRCACHE_FILE_PATH + " text not null );");
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            Log.w(t, "Upgrading database from version " + oldVersion + " to " + newVersion
                    + ", which will destroy all old data");
            db.execSQL("DROP TABLE IF EXISTS forms");
            onCreate(db);
        }
    }

    private DatabaseHelper mDbHelper;

    @Override
    public boolean onCreate() {
        //This is so stupid.
        return true;
    }

    private void init() {
        String appId = ProviderUtils.getSandboxedAppId();
        if (mDbHelper == null || !mDbHelper.getAppId().equals(appId)) {
            String dbName = ProviderUtils.getProviderDbName(ProviderUtils.ProviderType.FORMS, appId);
            mDbHelper = new DatabaseHelper(CommCareApplication._(), dbName, appId);
        }
    }


    @Override
    public Cursor query(@NonNull Uri uri, String[] projection, String selection, String[] selectionArgs,
                        String sortOrder) {
        init();
        SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
        qb.setTables(FORMS_TABLE_NAME);

        switch (sUriMatcher.match(uri)) {
            case FORMS:
                qb.setProjectionMap(sFormsProjectionMap);
                break;

            case FORM_ID:
                qb.setProjectionMap(sFormsProjectionMap);
                qb.appendWhere(FormsColumns._ID + "=" + uri.getPathSegments().get(1));
                break;

            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }

        // Get the database and run the query
        SQLiteDatabase db = mDbHelper.getReadableDatabase();
        Cursor c = qb.query(db, projection, selection, selectionArgs, null, null, sortOrder);

        // Tell the cursor what uri to watch, so it knows when its source data changes
        c.setNotificationUri(getContext().getContentResolver(), uri);
        return c;
    }


    @Override
    public String getType(@NonNull Uri uri) {
        switch (sUriMatcher.match(uri)) {
            case FORMS:
                return FormsColumns.CONTENT_TYPE;

            case FORM_ID:
                return FormsColumns.CONTENT_ITEM_TYPE;

            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }
    }

    @Override
    public Uri insert(@NonNull Uri uri, ContentValues initialValues) {
        init();
        // Validate the requested uri
        if (sUriMatcher.match(uri) != FORMS) {
            throw new IllegalArgumentException("Unknown URI " + uri);
        }

        ContentValues values;
        if (initialValues != null) {
            values = new ContentValues(initialValues);
        } else {
            values = new ContentValues();
        }

        Long now = System.currentTimeMillis();

        // Make sure that the necessary fields are all set
        if (!values.containsKey(FormsColumns.DATE)) {
            values.put(FormsColumns.DATE, now);
        }

        if (!values.containsKey(FormsColumns.DISPLAY_SUBTEXT)) {
            Date today = new Date();
            String ts = new SimpleDateFormat("EEE, MMM dd, yyyy 'at' HH:mm").format(today);
            values.put(FormsColumns.DISPLAY_SUBTEXT, "Added on " + ts);
        }

        // if we don't have a path to the file, the rest are irrelevant.
        // it should fail anyway because you can't have a null file path.
        if (values.containsKey(FormsColumns.FORM_FILE_PATH)) {
            String filePath = values.getAsString(FormsColumns.FORM_FILE_PATH);
            File form = new File(filePath);

            if (!values.containsKey(FormsColumns.DISPLAY_NAME)) {
                values.put(FormsColumns.DISPLAY_NAME, form.getName());
            }

            // don't let users put in a manual md5 hash
            if (values.containsKey(FormsColumns.MD5_HASH)) {
                values.remove(FormsColumns.MD5_HASH);
            }
            String md5 = FileUtil.getMd5Hash(form);
            values.put(FormsColumns.MD5_HASH, md5);

            if (!values.containsKey(FormsColumns.JRCACHE_FILE_PATH)) {
                String cachePath = Environment.getExternalStorageDirectory().getPath() + "odk/.cache/" + md5 + ".formdef";
                values.put(FormsColumns.JRCACHE_FILE_PATH, cachePath);
            }
            if (!values.containsKey(FormsColumns.FORM_MEDIA_PATH)) {
                String pathNoExtension = filePath.substring(0, filePath.lastIndexOf("."));
                String mediaPath = pathNoExtension + "-media";
                values.put(FormsColumns.FORM_MEDIA_PATH, mediaPath);
            }

        }

        SQLiteDatabase db = mDbHelper.getWritableDatabase();
        long rowId = db.insert(FORMS_TABLE_NAME, null, values);
        db.close();

        if (rowId > 0) {
            Uri formUri = ContentUris.withAppendedId(FormsColumns.CONTENT_URI, rowId);
            getContext().getContentResolver().notifyChange(formUri, null);
            return formUri;
        }

        throw new SQLException("Failed to insert row into " + uri);
    }

    /**
     * This method removes the entry from the content provider, and also removes any associated
     * files. files: form.xml, [formmd5].formdef, formname-media {directory}
     */
    @Override
    public int delete(@NonNull Uri uri, String where, String[] whereArgs) {
        init();
        SQLiteDatabase db = mDbHelper.getWritableDatabase();
        int count;

        switch (sUriMatcher.match(uri)) {
            case FORMS:
                Cursor del = null;
                try {
                    del = this.query(uri, null, where, whereArgs, null);
                    del.moveToPosition(-1);
                    while (del.moveToNext()) {
                        FileUtil.deleteFileOrDir(del.getString(del
                                .getColumnIndex(FormsColumns.JRCACHE_FILE_PATH)));
                        FileUtil.deleteFileOrDir(del.getString(del.getColumnIndex(FormsColumns.FORM_FILE_PATH)));
                        FileUtil.deleteFileOrDir(del.getString(del.getColumnIndex(FormsColumns.FORM_MEDIA_PATH)));
                    }
                } finally {
                    if (del != null) {
                        del.close();
                    }
                }
                count = db.delete(FORMS_TABLE_NAME, where, whereArgs);
                break;

            case FORM_ID:
                String formId = uri.getPathSegments().get(1);

                Cursor c = null;
                try {
                    c = this.query(uri, null, where, whereArgs, null);
                    // This should only ever return 1 record.
                    c.moveToPosition(-1);
                    while (c.moveToNext()) {
                        FileUtil.deleteFileOrDir(c.getString(c.getColumnIndex(FormsColumns.JRCACHE_FILE_PATH)));
                        FileUtil.deleteFileOrDir(c.getString(c.getColumnIndex(FormsColumns.FORM_FILE_PATH)));
                        FileUtil.deleteFileOrDir(c.getString(c.getColumnIndex(FormsColumns.FORM_MEDIA_PATH)));
                    }
                } finally {
                    if (c != null) {
                        c.close();
                    }
                }

                count =
                        db.delete(FORMS_TABLE_NAME,
                                FormsColumns._ID + "=" + formId
                                        + (!TextUtils.isEmpty(where) ? " AND (" + where + ')' : ""),
                                whereArgs);
                break;

            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }

        db.close();

        getContext().getContentResolver().notifyChange(uri, null);
        return count;
    }


    @Override
    public int update(@NonNull Uri uri, ContentValues values, String where, String[] whereArgs) {
        init();
        SQLiteDatabase db = mDbHelper.getWritableDatabase();
        int count = 0;
        switch (sUriMatcher.match(uri)) {
            case FORMS:
                // don't let users manually update md5
                if (values.containsKey(FormsColumns.MD5_HASH)) {
                    values.remove(FormsColumns.MD5_HASH);
                }
                // if values contains path, then all filepaths and md5s will get updated
                // this probably isn't a great thing to do.
                if (values.containsKey(FormsColumns.FORM_FILE_PATH)) {
                    String formFile = values.getAsString(FormsColumns.FORM_FILE_PATH);
                    values.put(FormsColumns.MD5_HASH, FileUtil.getMd5Hash(new File(formFile)));
                }

                //We used to delete the old files here, but we don't do that in CCODK. the
                //app is responsible for those resources;

                // Make sure that the necessary fields are all set
                if (values.containsKey(FormsColumns.DATE)) {
                    Date today = new Date();
                    String ts = new SimpleDateFormat("EEE, MMM dd, yyyy 'at' HH:mm").format(today);
                    values.put(FormsColumns.DISPLAY_SUBTEXT, "Added on " + ts);
                }

                count = db.update(FORMS_TABLE_NAME, values, where, whereArgs);
                break;

            case FORM_ID:
                String formId = uri.getPathSegments().get(1);
                // Whenever file paths are updated, delete the old files.

                Cursor update = null;
                try {
                    update = this.query(uri, null, where, whereArgs, null);

                    // This should only ever return 1 record.
                    if (update.getCount() > 0) {
                        update.moveToFirst();

                        // don't let users manually update md5
                        if (values.containsKey(FormsColumns.MD5_HASH)) {
                            values.remove(FormsColumns.MD5_HASH);
                        }

                        // the order here is important (jrcache needs to be before form file)
                        // because we update the jrcache file if there's a new form file
                        if (values.containsKey(FormsColumns.JRCACHE_FILE_PATH)) {
                            FileUtil.deleteFileOrDir(update.getString(update
                                    .getColumnIndex(FormsColumns.JRCACHE_FILE_PATH)));
                        }

                        if (values.containsKey(FormsColumns.FORM_FILE_PATH)) {
                            String formFile = values.getAsString(FormsColumns.FORM_FILE_PATH);
                            String oldFile =
                                    update.getString(update.getColumnIndex(FormsColumns.FORM_FILE_PATH));

                            try {
                                if (new File(oldFile).getCanonicalPath().equals(new File(formFile).getCanonicalPath())) {
                                    // Files are the same, so we may have just copied over something we had
                                    // already
                                } else {
                                    // New file name. This probably won't ever happen, though.
                                    FileUtil.deleteFileOrDir(oldFile);
                                }
                            } catch (IOException ioe) {
                                //we only get here if we couldn't canonicalize, in which case we can't risk deleting the old file
                                //so don't do anything.
                            }

                            // we're updating our file, so update the md5
                            // and get rid of the cache (doesn't harm anything)
                            FileUtil.deleteFileOrDir(update.getString(update
                                    .getColumnIndex(FormsColumns.JRCACHE_FILE_PATH)));
                            String newMd5 = FileUtil.getMd5Hash(new File(formFile));
                            values.put(FormsColumns.MD5_HASH, newMd5);
                            values.put(FormsColumns.JRCACHE_FILE_PATH, Environment.getExternalStorageDirectory().getPath() + "odk/.cache" + newMd5
                                    + ".formdef");
                        }

                        // Make sure that the necessary fields are all set
                        if (values.containsKey(FormsColumns.DATE)) {
                            Date today = new Date();
                            String ts =
                                    new SimpleDateFormat("EEE, MMM dd, yyyy 'at' HH:mm").format(today);
                            values.put(FormsColumns.DISPLAY_SUBTEXT, "Added on " + ts);
                        }

                        count =
                                db.update(FORMS_TABLE_NAME, values, FormsColumns._ID + "=" + formId
                                                + (!TextUtils.isEmpty(where) ? " AND (" + where + ')' : ""),
                                        whereArgs);
                    } else {
                        Log.e(t, "Attempting to update row that does not exist");
                    }
                } finally {
                    if (update != null) {
                        update.close();
                    }
                }
                break;

            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }

        db.close();

        getContext().getContentResolver().notifyChange(uri, null);
        return count;
    }

    static {
        sUriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
        sUriMatcher.addURI(FormsProviderAPI.AUTHORITY, "forms", FORMS);
        sUriMatcher.addURI(FormsProviderAPI.AUTHORITY, "forms/#", FORM_ID);

        sFormsProjectionMap = new HashMap<>();
        sFormsProjectionMap.put(FormsColumns._ID, FormsColumns._ID);
        sFormsProjectionMap.put(FormsColumns.DISPLAY_NAME, FormsColumns.DISPLAY_NAME);
        sFormsProjectionMap.put(FormsColumns.DISPLAY_SUBTEXT, FormsColumns.DISPLAY_SUBTEXT);
        sFormsProjectionMap.put(FormsColumns.DESCRIPTION, FormsColumns.DESCRIPTION);
        sFormsProjectionMap.put(FormsColumns.JR_FORM_ID, FormsColumns.JR_FORM_ID);
        sFormsProjectionMap.put(FormsColumns.MODEL_VERSION, FormsColumns.MODEL_VERSION);
        sFormsProjectionMap.put(FormsColumns.UI_VERSION, FormsColumns.UI_VERSION);
        sFormsProjectionMap.put(FormsColumns.SUBMISSION_URI, FormsColumns.SUBMISSION_URI);
        sFormsProjectionMap.put(FormsColumns.BASE64_RSA_PUBLIC_KEY, FormsColumns.BASE64_RSA_PUBLIC_KEY);
        sFormsProjectionMap.put(FormsColumns.MD5_HASH, FormsColumns.MD5_HASH);
        sFormsProjectionMap.put(FormsColumns.DATE, FormsColumns.DATE);
        sFormsProjectionMap.put(FormsColumns.FORM_MEDIA_PATH, FormsColumns.FORM_MEDIA_PATH);
        sFormsProjectionMap.put(FormsColumns.FORM_FILE_PATH, FormsColumns.FORM_FILE_PATH);
        sFormsProjectionMap.put(FormsColumns.JRCACHE_FILE_PATH, FormsColumns.JRCACHE_FILE_PATH);
        sFormsProjectionMap.put(FormsColumns.LANGUAGE, FormsColumns.LANGUAGE);
    }

}
