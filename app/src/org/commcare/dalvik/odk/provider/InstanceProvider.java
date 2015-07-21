package org.commcare.dalvik.odk.provider;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;

import org.commcare.android.database.user.models.FormRecord;
import org.commcare.android.javarosa.AndroidLogger;
import org.commcare.android.models.AndroidSessionWrapper;
import org.commcare.android.models.logic.FormRecordProcessor;
import org.commcare.android.tasks.ExceptionReportTask;
import org.commcare.android.tasks.FormRecordCleanupTask;
import org.commcare.dalvik.application.CommCareApplication;
import org.commcare.dalvik.odk.provider.InstanceProviderAPI.InstanceColumns;
import org.javarosa.core.services.Logger;

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
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.util.Log;

public class InstanceProvider extends ContentProvider {
    private static final String t = "InstancesProvider";

    private static final String DATABASE_NAME = "instances.db";
    private static final int DATABASE_VERSION = 2;
    private static final String INSTANCES_TABLE_NAME = "instances";

    private static HashMap<String, String> sInstancesProjectionMap;

    private static final int INSTANCES = 1;
    private static final int INSTANCE_ID = 2;

    @NonNull
    private static final UriMatcher sUriMatcher;

    /**
     * This class helps open, create, and upgrade the database file.
     */
    private static class DatabaseHelper extends SQLiteOpenHelper {

        public DatabaseHelper(Context c, String databaseName) {
            super(c, databaseName, null, DATABASE_VERSION);
        }

        /*
         * (non-Javadoc)
         * @see android.database.sqlite.SQLiteOpenHelper#onCreate(android.database.sqlite.SQLiteDatabase)
         */
        @Override
        public void onCreate(@NonNull SQLiteDatabase db) {
           db.execSQL("CREATE TABLE " + INSTANCES_TABLE_NAME + " (" 
               + InstanceColumns._ID + " integer primary key, " 
               + InstanceColumns.DISPLAY_NAME + " text not null, "
               + InstanceColumns.SUBMISSION_URI + " text, "
               + InstanceColumns.CAN_EDIT_WHEN_COMPLETE + " text, "
               + InstanceColumns.INSTANCE_FILE_PATH + " text not null, "
               + InstanceColumns.JR_FORM_ID + " text not null, "
               + InstanceColumns.STATUS + " text not null, "
               + InstanceColumns.LAST_STATUS_CHANGE_DATE + " date not null, "
               + InstanceColumns.DISPLAY_SUBTEXT + " text not null );");   
        }


        /*
         * (non-Javadoc)
         * @see android.database.sqlite.SQLiteOpenHelper#onUpgrade(android.database.sqlite.SQLiteDatabase, int, int)
         */
        @Override
        public void onUpgrade(@NonNull SQLiteDatabase db, int oldVersion, int newVersion) {
            Log.w(t, "Upgrading database from version " + oldVersion + " to " + newVersion
                    + ", which will destroy all old data");
            db.execSQL("DROP TABLE IF EXISTS instances");
            onCreate(db);
        }
    }

    private DatabaseHelper mDbHelper;

    /*
     * (non-Javadoc)
     * @see android.content.ContentProvider#onCreate()
     */
    @Override
    public boolean onCreate() {
        //This is so stupid.
        return true;
    }

    /**
     * Setup helper to access database.
     */
    public void init() {
        //this is terrible, we need to be binding to the cc service, etc. Temporary code for testing
        if(mDbHelper == null) {
            mDbHelper = new DatabaseHelper(CommCareApplication._(), DATABASE_NAME);
        }
    }

    /*
     * (non-Javadoc)
     * @see android.content.ContentProvider#query(android.net.Uri, java.lang.String[], java.lang.String, java.lang.String[], java.lang.String)
     */
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
                qb.appendWhere(InstanceColumns._ID + "=" + uri.getPathSegments().get(1));
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

    /*
     * (non-Javadoc)
     * @see android.content.ContentProvider#getType(android.net.Uri)
     */
    @Override
    public String getType(@NonNull Uri uri) {
        switch (sUriMatcher.match(uri)) {
            case INSTANCES:
                return InstanceColumns.CONTENT_TYPE;

            case INSTANCE_ID:
                return InstanceColumns.CONTENT_ITEM_TYPE;

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
    public Uri insert(@NonNull Uri uri, @Nullable ContentValues initialValues) {
        // Validate the requested uri
        if (sUriMatcher.match(uri) != INSTANCES) {
            throw new IllegalArgumentException("Unknown URI " + uri);
        }
        init();

        ContentValues values;
        if (initialValues != null) {
            values = new ContentValues(initialValues);
        } else {
            values = new ContentValues();
        }


        // Make sure that the fields are all set
        if (!values.containsKey(InstanceColumns.LAST_STATUS_CHANGE_DATE)) {
            // set the change date to now
            values.put(InstanceColumns.LAST_STATUS_CHANGE_DATE, Long.valueOf(System.currentTimeMillis()));
        }

        if (!values.containsKey(InstanceColumns.DISPLAY_SUBTEXT)) {
            // set display subtext to detail save date
            values.put(InstanceColumns.DISPLAY_SUBTEXT,
                    getDisplaySubtext(InstanceProviderAPI.STATUS_INCOMPLETE));
        }

        if (!values.containsKey(InstanceColumns.STATUS)) {
            values.put(InstanceColumns.STATUS, InstanceProviderAPI.STATUS_INCOMPLETE);
        }

        SQLiteDatabase db = mDbHelper.getWritableDatabase();
        long rowId = db.insert(INSTANCES_TABLE_NAME, null, values);
        db.close();

        if (rowId > 0) {
            Uri instanceUri = ContentUris.withAppendedId(InstanceColumns.CONTENT_URI, rowId);
            getContext().getContentResolver().notifyChange(instanceUri, null);

            try {
                linkToSessionFormRecord(instanceUri);
            } catch (Exception e) {
                throw new SQLException("Failed to insert row into " + uri);
            }

            return instanceUri;
        }

        throw new SQLException("Failed to insert row into " + uri);
    }

    /**
     * Create display subtext for current date and time
     *
     * @param state is the status column of an instance entry
     */
    @NonNull
    private String getDisplaySubtext(@Nullable String state) {
        String ts = new SimpleDateFormat("EEE, MMM dd, yyyy 'at' HH:mm").format(new Date());

        if (state == null) {
            return "Added on " + ts;
        } else if (InstanceProviderAPI.STATUS_INCOMPLETE.equalsIgnoreCase(state)) {
            return "Saved on " + ts;
        } else if (InstanceProviderAPI.STATUS_COMPLETE.equalsIgnoreCase(state)) {
            return "Finalized on " + ts;
        } else if (InstanceProviderAPI.STATUS_SUBMITTED.equalsIgnoreCase(state)) {
            return "Sent on " + ts;
        } else if (InstanceProviderAPI.STATUS_SUBMISSION_FAILED.equalsIgnoreCase(state)) {
            return "Sending failed on " + ts;
        } else {
            return "Added on " + ts;
        }
    }

    private void deleteFileOrDir(@NonNull String fileName ) {
        File file = new File(fileName);
        if (file.exists()) {
            if (file.isDirectory()) {
                // delete all the containing files
                File[] files = file.listFiles();
                for (File f : files) {
                    // should make this recursive if we get worried about
                    // the media directory containing directories
                    f.delete();
                }
            }
            file.delete();
        }
    }

    /*
     * (non-Javadoc)
     * @see android.content.ContentProvider#delete(android.net.Uri, java.lang.String, java.lang.String[])
     * 
     * This method removes the entry from the content provider, and also removes any associated files.
     * files:  form.xml, [formmd5].formdef, formname-media {directory}
     */
    @Override
    public int delete(@NonNull Uri uri, String where, String[] whereArgs) {
        init();
        SQLiteDatabase db = mDbHelper.getWritableDatabase();
        int count;
        
        switch (sUriMatcher.match(uri)) {
            case INSTANCES:                
                Cursor del = null;
                try {
                    del = this.query(uri, null, where, whereArgs, null);
                    del.moveToPosition(-1);
                    while (del.moveToNext()) {
                        String instanceFile = del.getString(del.getColumnIndex(InstanceColumns.INSTANCE_FILE_PATH));
                        String instanceDir = (new File(instanceFile)).getParent();
                        deleteFileOrDir(instanceDir);
                    }
                } finally {
                    if ( del != null ) {
                        del.close();
                    }
                }
                count = db.delete(INSTANCES_TABLE_NAME, where, whereArgs);
                break;

            case INSTANCE_ID:
                String instanceId = uri.getPathSegments().get(1);

                Cursor c = null;
                try {
                    c = this.query(uri, null, where, whereArgs, null);
                    // This should only ever return 1 record.  I hope.
                    c.moveToPosition(-1);
                    while (c.moveToNext()) {
                        String instanceFile = c.getString(c.getColumnIndex(InstanceColumns.INSTANCE_FILE_PATH));
                        String instanceDir = (new File(instanceFile)).getParent();
                        deleteFileOrDir(instanceDir);           
                    }
                } finally {
                    if ( c != null ) {
                        c.close();
                    }
                }
                
                count =
                    db.delete(INSTANCES_TABLE_NAME,
                        InstanceColumns._ID + "=" + instanceId
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


    /**
     * {@inheritDoc}
     *
     * @see android.content.ContentProvider#update(android.net.Uri, android.content.ContentValues, java.lang.String, java.lang.String[])
     */
    @Override
    public int update(@NonNull Uri uri, @NonNull ContentValues values, String where, String[] whereArgs) {
        int count;

        init();
        SQLiteDatabase db = mDbHelper.getWritableDatabase();

        // Given a value in the status column and none in the display subtext
        // column, set the display subtext column from the status value.
        if (values.containsKey(InstanceColumns.STATUS) &&
                !values.containsKey(InstanceColumns.DISPLAY_SUBTEXT)) {
            values.put(InstanceColumns.DISPLAY_SUBTEXT,
                    getDisplaySubtext(values.getAsString(InstanceColumns.STATUS)));
        }

        switch (sUriMatcher.match(uri)) {
            case INSTANCES:
                // assumes where/whereArgs were constructed to point to the
                // entry to update
                count = db.update(INSTANCES_TABLE_NAME, values, where, whereArgs);
                break;

            case INSTANCE_ID:
                // use the uri to manually build an update query
                String instanceId = uri.getPathSegments().get(1);

                count =
                    db.update(INSTANCES_TABLE_NAME, values, InstanceColumns._ID + "=" + instanceId
                            + (!TextUtils.isEmpty(where) ? " AND (" + where + ')' : ""), whereArgs);
                break;

            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }
        db.close();

        // If we've changed a particular form instance's status, and not
        // created a new entry (hence count > 0 check), we need to mirror the
        // change in that form's record.  NOTE: this conditional is crucial,
        // since updating a form record in turn calls this update function, so
        // we need to break the infinite loop by only updating the form record
        // when the the status changes.
        if (values.containsKey(InstanceColumns.STATUS) && count > 0) {
            try {
                linkToSessionFormRecord(getInstanceRowUri(uri, where, whereArgs));
            } catch (Exception e) {
                throw new SQLException("Failed to update row " + uri);
            }
        }

        getContext().getContentResolver().notifyChange(uri, null);

        return count;
    }

    /**
     * Check if a URI points to a concrete instance; if it doesn't
     * then rebuild the uri from the result of a query using the where
     * arguments.
     *
     * @param potentialUri URI pointing to the instance table or a particular
     * entry in that table
     * @param selection A selection criteria to apply when filtering rows. If
     * null then all rows are included.
     * @param selectionArgs You may include ?s in selection, which will be
     * replaced by the values from selectionArgs, in order that they appear in
     * the selection. The values will be bound as Strings.
     * @return URI pointing to a row in the instance table, either the one passed
     * in or built from a query using the method arguments.
     */
    private Uri getInstanceRowUri(@NonNull Uri potentialUri, String selection, String[] selectionArgs) {
        switch (sUriMatcher.match(potentialUri)) {
            case INSTANCES:
                // the potential URI points to the instance table, so use the
                // selection args to find a specific row id.
                Cursor c = null;
                try {
                    c = this.query(potentialUri, null, selection, selectionArgs, null);
                    c.moveToPosition(-1);
                    if (c.moveToNext()) {
                        // there should only be one result for this query
                        String instanceId = c.getString(c.getColumnIndex("_id"));
                        return InstanceColumns.CONTENT_URI.buildUpon().appendPath(instanceId).build();
                    }
                } finally {
                    if (c != null ) {
                        c.close();
                    }
                }
                break;
            case INSTANCE_ID:
                // the potential URI points to a row in the instance table
                return potentialUri;
            default:
                throw new IllegalArgumentException("Unknown URI " + potentialUri);
        }
        return null;
    }

    /**
     * Register an instance with the session's form record.
     *
     * @param instanceUri points to a concrete instance we want to register
     */
    private void linkToSessionFormRecord(@NonNull Uri instanceUri) {
        if (getType(instanceUri) != InstanceColumns.CONTENT_ITEM_TYPE) {
            Log.w(t, "Tried to link a FormRecord to a URI that doesn't point " +
                    "to a concrete instance.");
            return;
        }
        AndroidSessionWrapper currentState = CommCareApplication._().getCurrentSessionWrapper();

        if (instanceUri == null) {
            raiseFormEntryError("Form Entry did not return a form", currentState);
            return;
        }

        Cursor c = getContext().getContentResolver().query(instanceUri, null, null, null, null);
        boolean complete = false;
        try {
            // register the instance uri and its status with the session
            complete = currentState.beginRecordTransaction(instanceUri, c);
        } catch (IllegalArgumentException iae) {
            iae.printStackTrace();
            // TODO: Fail more hardcore here? Wipe the form record and its ties?
            raiseFormEntryError("Unrecoverable error when trying to read form|" + iae.getMessage(),
                    currentState);
            return;
        } finally {
            c.close();
        }

        FormRecord current;
        try {
            current = currentState.commitRecordTransaction();
        } catch (Exception e) {
            // Something went wrong with all of the connections which should exist.
            FormRecordCleanupTask.wipeRecord(getContext(), currentState);

            // Notify the server of this problem (since we aren't going to crash)
            ExceptionReportTask ert = new ExceptionReportTask();
            ert.execute(e);

            raiseFormEntryError("An error occurred: " + e.getMessage() +
                    " and your data could not be saved.", currentState);
            return;
        }

        Logger.log(AndroidLogger.TYPE_FORM_ENTRY, "Form Entry Completed");

        // The form is either ready for processing, or not, depending on how it was saved
        if (complete) {
            // Form record should now be up to date now and stored correctly.

            // ctsims - App stack workflows require us to have processed _this_ specific form before
            // we can move on, and that needs to be synchronous. We'll go ahead and try to process just
            // this form before moving on. We'll catch any errors here and just eat them (since the
            // task will also try the process and fail if it does.
            if (FormRecord.STATUS_COMPLETE.equals(current.getStatus())) {
                try {
                    new FormRecordProcessor(getContext()).process(current);
                } catch (Exception e) {
                    Logger.log(AndroidLogger.TYPE_ERROR_WORKFLOW,
                            "Error processing form. Should be recaptured during async processing: " + e.getMessage());
                }
            }
        }
    }

    static {
        sUriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
        sUriMatcher.addURI(InstanceProviderAPI.AUTHORITY, "instances", INSTANCES);
        sUriMatcher.addURI(InstanceProviderAPI.AUTHORITY, "instances/#", INSTANCE_ID);

        sInstancesProjectionMap = new HashMap<String, String>();
        sInstancesProjectionMap.put(InstanceColumns._ID, InstanceColumns._ID);
        sInstancesProjectionMap.put(InstanceColumns.DISPLAY_NAME, InstanceColumns.DISPLAY_NAME);
        sInstancesProjectionMap.put(InstanceColumns.SUBMISSION_URI, InstanceColumns.SUBMISSION_URI);
        sInstancesProjectionMap.put(InstanceColumns.CAN_EDIT_WHEN_COMPLETE, InstanceColumns.CAN_EDIT_WHEN_COMPLETE);
        sInstancesProjectionMap.put(InstanceColumns.INSTANCE_FILE_PATH, InstanceColumns.INSTANCE_FILE_PATH);
        sInstancesProjectionMap.put(InstanceColumns.JR_FORM_ID, InstanceColumns.JR_FORM_ID);
        sInstancesProjectionMap.put(InstanceColumns.STATUS, InstanceColumns.STATUS);
        sInstancesProjectionMap.put(InstanceColumns.LAST_STATUS_CHANGE_DATE, InstanceColumns.LAST_STATUS_CHANGE_DATE);
        sInstancesProjectionMap.put(InstanceColumns.DISPLAY_SUBTEXT, InstanceColumns.DISPLAY_SUBTEXT);
    }

    /**
     * Throw and Log FormEntry-related errors
     *
     * @param loggerText String sent to javarosa logger
     * @param currentState session to be cleared
     */
    private void raiseFormEntryError(String loggerText, @NonNull AndroidSessionWrapper currentState) throws RuntimeException {
        Logger.log(AndroidLogger.TYPE_ERROR_WORKFLOW, loggerText);

        currentState.reset();
        throw new RuntimeException(loggerText);
    }
}
