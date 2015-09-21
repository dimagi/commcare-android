package org.commcare.android.database.user;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;

import net.sqlcipher.database.SQLiteDatabase;

import org.commcare.android.database.AndroidDbHelper;
import org.commcare.android.database.SqlStorage;
import org.commcare.android.database.app.models.UserKeyRecord;
import org.commcare.android.database.user.models.FormRecord;
import org.commcare.android.javarosa.AndroidLogger;
import org.commcare.android.javarosa.DeviceReportRecord;
import org.commcare.android.util.FileUtil;
import org.commcare.dalvik.application.CommCareApp;
import org.commcare.dalvik.application.CommCareApplication;
import org.commcare.dalvik.odk.provider.InstanceProviderAPI.InstanceColumns;
import org.javarosa.core.services.Logger;

import java.io.File;
import java.io.IOException;

/**
 * @author ctsims
 */
public class UserSandboxUtils {

    public static void migrateData(Context c, CommCareApp app, UserKeyRecord incomingSandbox, byte[] unwrappedOldKey, UserKeyRecord newSandbox, byte[] unwrappedNewKey) throws IOException {
        //Step one: Make a copy of the incoming sandbox's database and re-key it to use the new key.
        Logger.log(AndroidLogger.TYPE_MAINTENANCE, "Migrating an existing user sandbox for " + newSandbox.getUsername());
        
        File oldDb = c.getDatabasePath(CommCareUserOpenHelper.getDbName(incomingSandbox.getUuid()));
        File newDb = c.getDatabasePath(CommCareUserOpenHelper.getDbName(newSandbox.getUuid()));
        
        //TODO: Make sure old sandbox is already on newest version?
        
        if(newDb.exists()) {
            if(!newDb.delete()) { throw new IOException("Couldn't clear file location " + newDb.getAbsolutePath() + " for new sandbox database"); } 
        }
        
        FileUtil.copyFile(oldDb, newDb);
        
        Logger.log(AndroidLogger.TYPE_MAINTENANCE, "Created a copy of the DB for the new sandbox. Re-keying it...");

        
        String oldKeyEncoded = getSqlCipherEncodedKey(unwrappedOldKey);
        String newKeyEncoded = getSqlCipherEncodedKey(unwrappedNewKey);
        SQLiteDatabase rawDbHandle = SQLiteDatabase.openDatabase(newDb.getAbsolutePath(), oldKeyEncoded, null, SQLiteDatabase.OPEN_READWRITE);
        
        rawDbHandle.execSQL("PRAGMA key = '" + oldKeyEncoded + "';");
        rawDbHandle.execSQL("PRAGMA rekey  = '" + newKeyEncoded + "';");
        
        rawDbHandle.close();
        
        Logger.log(AndroidLogger.TYPE_MAINTENANCE, "Database is re-keyed and ready for use. Copying over files now");
        
        //OK, so now we have the Db transitioned. What we need to do now is go through and rekey all of our file references.
        
        final SQLiteDatabase db = new CommCareUserOpenHelper(CommCareApplication._(), newSandbox.getUuid()).getWritableDatabase(newKeyEncoded);
        
        try {
            
            //If we were able to iterate over the users, the key was fine, so let's use it to open our db
            AndroidDbHelper dbh = new AndroidDbHelper(c) {
                @Override
                public SQLiteDatabase getHandle() {
                    return db;
                }
            };
            
            //TODO: At some point we should really just encode the existence/operations on files in the record models themselves
            //Models with Files: Form Record. Log Record
            SqlStorage<DeviceReportRecord> reports = new SqlStorage<DeviceReportRecord>(DeviceReportRecord.STORAGE_KEY, DeviceReportRecord.class, dbh);
            
            //Log records
            for(DeviceReportRecord r : reports) {
                File oldPath = new File(r.getFilePath());
                File newPath = FileUtil.getNewFileLocation(oldPath, newSandbox.getUuid(), true);
                
                //Copy to a new location while re-encrypting
                FileUtil.copyFile(oldPath, newPath);
            }
            
            Logger.log(AndroidLogger.TYPE_MAINTENANCE, "Copied over all of the device reports. Moving on to the form records");
            
            ContentResolver cr = c.getContentResolver();
            
            //Form records are sadly a bit more complex. We need to both move all of the files, 
            //insert a new record in the content provider, and then update the form record.
            SqlStorage<FormRecord> formRecords = new SqlStorage<FormRecord>(FormRecord.STORAGE_KEY, FormRecord.class, dbh);
            for(FormRecord record : formRecords) {
                Uri instanceURI = record.getInstanceURI();
                
                //some records won't have a uri yet.
                if(instanceURI == null) { continue; }
                
                ContentValues values = new ContentValues();
                File oldForm;
                {
                    
                    //otherwise read and prepare the record
                    Cursor oldRecord = cr.query(instanceURI, new String[] {InstanceColumns.INSTANCE_FILE_PATH, InstanceColumns.DISPLAY_NAME, InstanceColumns.SUBMISSION_URI, InstanceColumns.JR_FORM_ID, InstanceColumns.STATUS, InstanceColumns.CAN_EDIT_WHEN_COMPLETE,InstanceColumns.LAST_STATUS_CHANGE_DATE,  InstanceColumns.DISPLAY_SUBTEXT}, null, null, null);
                    if(!oldRecord.moveToFirst()) { throw new IOException("Non existant form record at URI " + instanceURI.toString()); }
                    
                    values.put(InstanceColumns.DISPLAY_NAME, oldRecord.getString(oldRecord.getColumnIndex(InstanceColumns.DISPLAY_NAME)));
                    values.put(InstanceColumns.SUBMISSION_URI, oldRecord.getString(oldRecord.getColumnIndex(InstanceColumns.SUBMISSION_URI)));
                    values.put(InstanceColumns.JR_FORM_ID, oldRecord.getString(oldRecord.getColumnIndex(InstanceColumns.JR_FORM_ID)));
                    values.put(InstanceColumns.STATUS, oldRecord.getString(oldRecord.getColumnIndex(InstanceColumns.STATUS)));
                    values.put(InstanceColumns.CAN_EDIT_WHEN_COMPLETE, oldRecord.getString(oldRecord.getColumnIndex(InstanceColumns.CAN_EDIT_WHEN_COMPLETE)));
                    values.put(InstanceColumns.LAST_STATUS_CHANGE_DATE, oldRecord.getLong(oldRecord.getColumnIndex(InstanceColumns.LAST_STATUS_CHANGE_DATE)));
                    values.put(InstanceColumns.DISPLAY_SUBTEXT, oldRecord.getString(oldRecord.getColumnIndex(InstanceColumns.DISPLAY_SUBTEXT)));
                    
                    
                    //Copy over the other metadata
                    
                    oldForm = new File(oldRecord.getString(oldRecord.getColumnIndex(InstanceColumns.INSTANCE_FILE_PATH)));
                    
                    oldRecord.close();
                }
                
                File oldFolder = oldForm.getParentFile();
                
                //find a new spot for it
                File newFolder = FileUtil.getNewFileLocation(oldFolder, newSandbox.getUuid(), true);
                
                FileUtil.copyFileDeep(oldFolder, newFolder);
                
                File newfileToWrite = null;
                for(File f : newFolder.listFiles()) {
                    if(f.getName().equals(oldForm.getName())) {
                        newfileToWrite = f;
                    }
                }
                
                //ok, new directory totally ready. Create a new instanceURI
                values.put(InstanceColumns.INSTANCE_FILE_PATH, newfileToWrite.getAbsolutePath());
                Uri newUri = cr.insert(InstanceColumns.CONTENT_URI, values);
                record = record.updateInstanceAndStatus(newUri.toString(), record.getStatus());
                formRecords.write(record);
            }
            
        } finally {
            db.close();
        }
        
        Logger.log(AndroidLogger.TYPE_MAINTENANCE, "All form records copied over");
        
        //OK! So we should be all set, here. Mark the new sandbox as ready and the old sandbox as ready for cleanup.
        SqlStorage<UserKeyRecord> ukr = app.getStorage(UserKeyRecord.class);

        SQLiteDatabase ukrdb = ukr.getAccessLock();
        ukrdb.beginTransaction();
        try {
            incomingSandbox.setType(UserKeyRecord.TYPE_PENDING_DELETE);
            ukr.write(incomingSandbox);
            newSandbox.setType(UserKeyRecord.TYPE_NORMAL);
            ukr.write(newSandbox);
            ukrdb.setTransactionSuccessful();
        } finally {
            ukrdb.endTransaction();
        }
    }
    

    public static String getSqlCipherEncodedKey(byte[] bytes) {
        String hexString = "x\"";
        for (int i = 0; i < bytes.length; i++) {
            String hexDigits = Integer.toHexString(0xFF & bytes[i]).toUpperCase();
            while(hexDigits.length() < 2) { 
                hexDigits = "0" + hexDigits;
            }
            hexString += hexDigits; 
        }
        hexString = hexString + "\"";
        return hexString;
    }


    public static void purgeSandbox(Context context, CommCareApp app, UserKeyRecord sandbox, byte[] key) {
        
        Logger.log(AndroidLogger.TYPE_MAINTENANCE, "Wiping sandbox " + sandbox.getUuid());
        
        //Ok, three steps here. Wipe files out, wipe database, remove key record
        
        //If the db is gone already, just remove the record and move on (something odd has happened)
        if(!context.getDatabasePath(CommCareUserOpenHelper.getDbName(sandbox.getUuid())).exists()) {
            Logger.log(AndroidLogger.TYPE_MAINTENANCE, "Sandbox " + sandbox.getUuid() + " has already been purged. removing the record");
            
            SqlStorage<UserKeyRecord> ukr = app.getStorage(UserKeyRecord.class);
            ukr.remove(sandbox);
        }
        
        final SQLiteDatabase db = new CommCareUserOpenHelper(CommCareApplication._(), sandbox.getUuid()).getWritableDatabase(getSqlCipherEncodedKey(key));
        
        try {
            AndroidDbHelper dbh = new AndroidDbHelper(context) {
                @Override
                public SQLiteDatabase getHandle() {
                    return db;
                }
            };
            
            SqlStorage<DeviceReportRecord> reports = new SqlStorage<DeviceReportRecord>(DeviceReportRecord.STORAGE_KEY, DeviceReportRecord.class, dbh);
            
            //Log records
            for(DeviceReportRecord r : reports) {
                File oldPath = new File(r.getFilePath());
                if(oldPath.exists()) {
                    FileUtil.deleteFile(oldPath);
                }
            }
            
            Logger.log(AndroidLogger.TYPE_MAINTENANCE, "Device Report files removed");
            
            //Form records are sadly a bit more complex. We need to both move all of the files, 
            //insert a new record in the content provider, and then update the form record.
            SqlStorage<FormRecord> formRecords = new SqlStorage<FormRecord>(FormRecord.STORAGE_KEY, FormRecord.class, dbh);
            for(FormRecord record : formRecords) {
                Uri formUri = record.getInstanceURI();
                if(formUri == null) { continue; }
                Cursor c = context.getContentResolver().query(formUri, new String[] {InstanceColumns._ID}, null, null, null);
                try {
                    //See if the record is still here
                    if(c.moveToFirst()) {
                        //If so, just grab the ID and delete this record (it'll take the files with it)
                        long id = c.getLong(0);
                        context.getContentResolver().delete(ContentUris.withAppendedId(InstanceColumns.CONTENT_URI, id), null, null);
                    }
                } finally {
                    c.close();
                }
            }
            
        } finally {
            db.close();
        }
        
        Logger.log(AndroidLogger.TYPE_MAINTENANCE, "All files removed for sandbox. Deleting DB");
        
        context.getDatabasePath(CommCareUserOpenHelper.getDbName(sandbox.getUuid())).delete();
        
        Logger.log(AndroidLogger.TYPE_MAINTENANCE, "Database is gone. Get rid of this record");
        
        //OK! So we should be all set, here. Mark the new sandbox as ready and the old sandbox as ready for cleanup.
        SqlStorage<UserKeyRecord> ukr = app.getStorage(UserKeyRecord.class);
        ukr.remove(sandbox);
        
        Logger.log(AndroidLogger.TYPE_MAINTENANCE, "Purge complete");
    }
    
}
