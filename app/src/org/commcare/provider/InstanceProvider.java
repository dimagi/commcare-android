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
import android.support.annotation.NonNull;
import android.text.TextUtils;
import android.util.Log;


import org.commcare.CommCareApplication;
import org.commcare.android.database.app.models.InstanceRecord;
import org.commcare.android.database.user.models.FormRecord;
import org.commcare.android.logging.ForceCloseLogger;
import org.commcare.models.AndroidSessionWrapper;
import org.commcare.models.FormRecordProcessor;
import org.commcare.tasks.FormRecordCleanupTask;
import org.commcare.util.LogTypes;
import org.commcare.views.notifications.NotificationMessage;
import org.commcare.views.notifications.NotificationMessageFactory;
import org.javarosa.core.services.Logger;
import org.javarosa.core.services.storage.IStorageUtilityIndexed;
import org.javarosa.xml.util.InvalidStructureException;
import org.javarosa.xml.util.UnfullfilledRequirementsException;
import org.xmlpull.v1.XmlPullParserException;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;

import javax.crypto.SecretKey;

// Replaced by InstanceRecord in 2.42, only used for DB Migration now
public class InstanceProvider extends ContentProvider {
    private static final String t = "InstancesProvider";

    private static final int DATABASE_VERSION = 2;
    private static final String INSTANCES_TABLE_NAME = "instances";

    private static final HashMap<String, String> sInstancesProjectionMap;

    private static final int INSTANCES = 1;
    private static final int INSTANCE_ID = 2;

    private static final UriMatcher sUriMatcher;

    /**
     * This class helps open, create, and upgrade the database file.
     */
    private static class DatabaseHelper extends SQLiteOpenHelper {

        // the application id of the CCApp for which this db is storing instances
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
            db.execSQL("CREATE TABLE " + INSTANCES_TABLE_NAME + " ("
                    + InstanceProviderAPI.InstanceColumns._ID + " integer primary key, "
                    + InstanceProviderAPI.InstanceColumns.DISPLAY_NAME + " text not null, "
                    + InstanceProviderAPI.InstanceColumns.SUBMISSION_URI + " text, "
                    + InstanceProviderAPI.InstanceColumns.CAN_EDIT_WHEN_COMPLETE + " text, "
                    + InstanceProviderAPI.InstanceColumns.INSTANCE_FILE_PATH + " text not null, "
                    + InstanceProviderAPI.InstanceColumns.JR_FORM_ID + " text not null, "
                    + InstanceProviderAPI.InstanceColumns.STATUS + " text not null, "
                    + InstanceProviderAPI.InstanceColumns.LAST_STATUS_CHANGE_DATE + " date not null, "
                    + InstanceProviderAPI.InstanceColumns.DISPLAY_SUBTEXT + " text not null );");
        }


        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            Log.w(t, "Upgrading database from version " + oldVersion + " to " + newVersion
                    + ", which will destroy all old data");
            db.execSQL("DROP TABLE IF EXISTS instances");
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
        if (mDbHelper == null || !appId.equals(mDbHelper.getAppId())) {
            String dbName = ProviderUtils.getProviderDbName(ProviderUtils.ProviderType.INSTANCES, appId);
            mDbHelper = new DatabaseHelper(CommCareApplication.instance(), dbName, appId);
        }
    }

    @Override
    public Cursor query(@NonNull Uri uri, String[] projection, String selection, String[] selectionArgs,
                        String sortOrder) {
        init();
        SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
        qb.setTables(INSTANCES_TABLE_NAME);

        switch (sUriMatcher.match(uri)) {
            case INSTANCES:
                qb.setProjectionMap(sInstancesProjectionMap);
                break;

            case INSTANCE_ID:
                qb.setProjectionMap(sInstancesProjectionMap);
                qb.appendWhere(InstanceProviderAPI.InstanceColumns._ID + "=" + uri.getPathSegments().get(1));
                break;

            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }

        // Get the database and run the query
        SQLiteDatabase db = mDbHelper.getReadableDatabase();
        Cursor c = qb.query(db, projection, selection, selectionArgs, null, null, sortOrder);

        // Tell the cursor what uri to watch, so it knows when its source data changes
        Context context = getContext();
        if (context != null) {
            c.setNotificationUri(context.getContentResolver(), uri);
        }
        return c;
    }

    @Override
    public String getType(@NonNull Uri uri) {
        switch (sUriMatcher.match(uri)) {
            case INSTANCES:
                return InstanceProviderAPI.InstanceColumns.CONTENT_TYPE;

            case INSTANCE_ID:
                return InstanceProviderAPI.InstanceColumns.CONTENT_ITEM_TYPE;

            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }
    }

    /**
     * {@inheritDoc}
     * Starting with the ContentValues passed in, finish setting up the
     * instance entry and write it database.
     *
     * @see android.content.ContentProvider#insert(android.net.Uri, android.content.ContentValues)
     */
    @Override
    @Deprecated
    public Uri insert(@NonNull Uri uri, ContentValues initialValues) {
        throw new IllegalArgumentException("insert not implemented for " + uri + ". Consider using " + InstanceRecord.class.getName() + " instead");
    }

    /**
     * This method removes the entry from the content provider, and also removes any associated files.
     * files:  form.xml, [formmd5].formdef, formname-media {directory}
     */
    @Override
    @Deprecated
    public int delete(@NonNull Uri uri, String where, String[] whereArgs) {
        throw new IllegalArgumentException("delete not implemented for " + uri + ". Consider using " + InstanceRecord.class.getName() + " instead");
    }

    @Override
    @Deprecated
    public int update(@NonNull Uri uri, ContentValues values, String where, String[] whereArgs) {
        throw new IllegalArgumentException("update not implemented for " + uri + ". Consider using " + InstanceRecord.class.getName() + " instead");
    }

    static {
        sUriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
        sUriMatcher.addURI(InstanceProviderAPI.AUTHORITY, "instances", INSTANCES);
        sUriMatcher.addURI(InstanceProviderAPI.AUTHORITY, "instances/#", INSTANCE_ID);

        sInstancesProjectionMap = new HashMap<>();
        sInstancesProjectionMap.put(InstanceProviderAPI.InstanceColumns._ID, InstanceProviderAPI.InstanceColumns._ID);
        sInstancesProjectionMap.put(InstanceProviderAPI.InstanceColumns.DISPLAY_NAME, InstanceProviderAPI.InstanceColumns.DISPLAY_NAME);
        sInstancesProjectionMap.put(InstanceProviderAPI.InstanceColumns.SUBMISSION_URI, InstanceProviderAPI.InstanceColumns.SUBMISSION_URI);
        sInstancesProjectionMap.put(InstanceProviderAPI.InstanceColumns.CAN_EDIT_WHEN_COMPLETE, InstanceProviderAPI.InstanceColumns.CAN_EDIT_WHEN_COMPLETE);
        sInstancesProjectionMap.put(InstanceProviderAPI.InstanceColumns.INSTANCE_FILE_PATH, InstanceProviderAPI.InstanceColumns.INSTANCE_FILE_PATH);
        sInstancesProjectionMap.put(InstanceProviderAPI.InstanceColumns.JR_FORM_ID, InstanceProviderAPI.InstanceColumns.JR_FORM_ID);
        sInstancesProjectionMap.put(InstanceProviderAPI.InstanceColumns.STATUS, InstanceProviderAPI.InstanceColumns.STATUS);
        sInstancesProjectionMap.put(InstanceProviderAPI.InstanceColumns.LAST_STATUS_CHANGE_DATE, InstanceProviderAPI.InstanceColumns.LAST_STATUS_CHANGE_DATE);
        sInstancesProjectionMap.put(InstanceProviderAPI.InstanceColumns.DISPLAY_SUBTEXT, InstanceProviderAPI.InstanceColumns.DISPLAY_SUBTEXT);
    }
}
