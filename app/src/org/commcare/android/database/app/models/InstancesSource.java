package org.commcare.android.database.app.models;

import android.database.Cursor;

import net.sqlcipher.database.SQLiteDatabase;

import org.commcare.models.database.app.DatabaseAppOpenHelper;
import org.commcare.provider.InstanceProviderAPI;

public class InstancesSource {

//    public static final String SQL_CREATE_TABLE = "CREATE TABLE " + AppDbContract.InstanceColumns.TABLE_NAME + " ("
//            + AppDbContract.InstanceColumns._ID + " integer primary key, "
//            + AppDbContract.InstanceColumns.DISPLAY_NAME + " text not null, "
//            + AppDbContract.InstanceColumns.SUBMISSION_URI + " text, "
//            + AppDbContract.InstanceColumns.CAN_EDIT_WHEN_COMPLETE + " text, "
//            + AppDbContract.InstanceColumns.INSTANCE_FILE_PATH + " text not null, "
//            + AppDbContract.InstanceColumns.JR_FORM_ID + " text not null, "
//            + AppDbContract.InstanceColumns.STATUS + " text not null, "
//            + AppDbContract.InstanceColumns.LAST_STATUS_CHANGE_DATE + " date not null, "
//            + AppDbContract.InstanceColumns.DISPLAY_SUBTEXT + " text not null );";
//
//    private final DatabaseAppOpenHelper mAppDbHelper;
//
//    public InstancesSource(DatabaseAppOpenHelper appDbHelper) {
//        this.mAppDbHelper = appDbHelper;
//    }
//
//    public String getInstanceFilePath(long instanceId) {
//        SQLiteDatabase db = mAppDbHelper.getReadableDatabase("null");
//        String filePath = null;
//        Cursor cursor = null;
//        try {
//            cursor = db.query(AppDbContract.InstanceColumns.TABLE_NAME,
//                    new String[]{InstanceProviderAPI.InstanceColumns.INSTANCE_FILE_PATH},
//                    InstanceProviderAPI.InstanceColumns._ID + "=?",
//                    new String[]{String.valueOf(instanceId)},
//                    null, null, null);
//
//            if (cursor.moveToFirst()) {
//                filePath = cursor.getString(cursor.getColumnIndex(InstanceProviderAPI.InstanceColumns.INSTANCE_FILE_PATH));
//            }
//        } finally {
//            if (cursor != null) {
//                cursor.close();
//            }
//            db.close();
//        }
//        return filePath;
//    }
}
