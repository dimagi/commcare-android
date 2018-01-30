package org.commcare.provider;

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

import org.commcare.CommCareApplication;
import org.commcare.android.database.app.models.FormDefRecord;
import org.commcare.android.database.app.models.InstanceRecord;
import org.commcare.utils.FileUtil;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;

// Replaced by FormDefRecord in 2.42, only used for DB Migration now
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
                    + FormsProviderAPI.FormsColumns._ID + " integer primary key, "
                    + FormsProviderAPI.FormsColumns.DISPLAY_NAME + " text not null, "
                    + FormsProviderAPI.FormsColumns.DISPLAY_SUBTEXT + " text not null, "
                    + FormsProviderAPI.FormsColumns.DESCRIPTION + " text, "
                    + FormsProviderAPI.FormsColumns.JR_FORM_ID + " text not null, "
                    + FormsProviderAPI.FormsColumns.MODEL_VERSION + " integer, "
                    + FormsProviderAPI.FormsColumns.UI_VERSION + " integer, "
                    + FormsProviderAPI.FormsColumns.MD5_HASH + " text not null, "
                    + FormsProviderAPI.FormsColumns.DATE + " integer not null, " // milliseconds
                    + FormsProviderAPI.FormsColumns.FORM_MEDIA_PATH + " text not null, "
                    + FormsProviderAPI.FormsColumns.FORM_FILE_PATH + " text not null, "
                    + FormsProviderAPI.FormsColumns.LANGUAGE + " text, "
                    + FormsProviderAPI.FormsColumns.SUBMISSION_URI + " text, "
                    + FormsProviderAPI.FormsColumns.BASE64_RSA_PUBLIC_KEY + " text, "
                    + FormsProviderAPI.FormsColumns.JRCACHE_FILE_PATH + " text not null );");
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
            mDbHelper = new DatabaseHelper(CommCareApplication.instance(), dbName, appId);
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
                qb.appendWhere(FormsProviderAPI.FormsColumns._ID + "=" + uri.getPathSegments().get(1));
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
                return FormsProviderAPI.FormsColumns.CONTENT_TYPE;

            case FORM_ID:
                return FormsProviderAPI.FormsColumns.CONTENT_ITEM_TYPE;

            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }
    }

    @Override
    @Deprecated
    public Uri insert(@NonNull Uri uri, ContentValues initialValues) {
        throw new IllegalArgumentException("insert not implemented for " + uri + ". Consider using " + FormDefRecord.class.getName() + " instead");
    }


    @Override
    @Deprecated
    public int delete(@NonNull Uri uri, String where, String[] whereArgs) {
        init();
        SQLiteDatabase db = mDbHelper.getWritableDatabase();
        int count;

        switch (sUriMatcher.match(uri)) {
            case FORMS:
//                Cursor del = null;
//                try {
//                    del = this.query(uri, null, where, whereArgs, null);
//                    del.moveToPosition(-1);
//                    while (del.moveToNext()) {
//                        FileUtil.deleteFileOrDir(del.getString(del
//                                .getColumnIndex(FormsProviderAPI.FormsColumns.JRCACHE_FILE_PATH)));
//                        FileUtil.deleteFileOrDir(del.getString(del.getColumnIndex(FormsProviderAPI.FormsColumns.FORM_FILE_PATH)));
//                        FileUtil.deleteFileOrDir(del.getString(del.getColumnIndex(FormsProviderAPI.FormsColumns.FORM_MEDIA_PATH)));
//                    }
//                } finally {
//                    if (del != null) {
//                        del.close();
//                    }
//                }
                count = db.delete(FORMS_TABLE_NAME, where, whereArgs);
                break;

            case FORM_ID:
                throw new IllegalArgumentException("delete not implemented for " + uri + ". Consider using " + FormDefRecord.class.getName() + " instead");
            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }
        db.close();
        getContext().getContentResolver().notifyChange(uri, null);
        return count;
    }


    @Override
    @Deprecated
    public int update(@NonNull Uri uri, ContentValues values, String where, String[] whereArgs) {
        throw new IllegalArgumentException("update not implemented for " + uri + ". Consider using " + FormDefRecord.class.getName() + " instead");
    }

    static {
        sUriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
        sUriMatcher.addURI(FormsProviderAPI.AUTHORITY, "forms", FORMS);
        sUriMatcher.addURI(FormsProviderAPI.AUTHORITY, "forms/#", FORM_ID);

        sFormsProjectionMap = new HashMap<>();
        sFormsProjectionMap.put(FormsProviderAPI.FormsColumns._ID, FormsProviderAPI.FormsColumns._ID);
        sFormsProjectionMap.put(FormsProviderAPI.FormsColumns.DISPLAY_NAME, FormsProviderAPI.FormsColumns.DISPLAY_NAME);
        sFormsProjectionMap.put(FormsProviderAPI.FormsColumns.DISPLAY_SUBTEXT, FormsProviderAPI.FormsColumns.DISPLAY_SUBTEXT);
        sFormsProjectionMap.put(FormsProviderAPI.FormsColumns.DESCRIPTION, FormsProviderAPI.FormsColumns.DESCRIPTION);
        sFormsProjectionMap.put(FormsProviderAPI.FormsColumns.JR_FORM_ID, FormsProviderAPI.FormsColumns.JR_FORM_ID);
        sFormsProjectionMap.put(FormsProviderAPI.FormsColumns.MODEL_VERSION, FormsProviderAPI.FormsColumns.MODEL_VERSION);
        sFormsProjectionMap.put(FormsProviderAPI.FormsColumns.UI_VERSION, FormsProviderAPI.FormsColumns.UI_VERSION);
        sFormsProjectionMap.put(FormsProviderAPI.FormsColumns.SUBMISSION_URI, FormsProviderAPI.FormsColumns.SUBMISSION_URI);
        sFormsProjectionMap.put(FormsProviderAPI.FormsColumns.BASE64_RSA_PUBLIC_KEY, FormsProviderAPI.FormsColumns.BASE64_RSA_PUBLIC_KEY);
        sFormsProjectionMap.put(FormsProviderAPI.FormsColumns.MD5_HASH, FormsProviderAPI.FormsColumns.MD5_HASH);
        sFormsProjectionMap.put(FormsProviderAPI.FormsColumns.DATE, FormsProviderAPI.FormsColumns.DATE);
        sFormsProjectionMap.put(FormsProviderAPI.FormsColumns.FORM_MEDIA_PATH, FormsProviderAPI.FormsColumns.FORM_MEDIA_PATH);
        sFormsProjectionMap.put(FormsProviderAPI.FormsColumns.FORM_FILE_PATH, FormsProviderAPI.FormsColumns.FORM_FILE_PATH);
        sFormsProjectionMap.put(FormsProviderAPI.FormsColumns.JRCACHE_FILE_PATH, FormsProviderAPI.FormsColumns.JRCACHE_FILE_PATH);
        sFormsProjectionMap.put(FormsProviderAPI.FormsColumns.LANGUAGE, FormsProviderAPI.FormsColumns.LANGUAGE);
    }

}
