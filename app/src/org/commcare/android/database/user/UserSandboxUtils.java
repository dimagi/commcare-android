/**
 * 
 */
package org.commcare.android.database.user;

import java.io.File;
import java.io.IOException;
import java.security.GeneralSecurityException;

import javax.crypto.Cipher;

import net.sqlcipher.database.SQLiteDatabase;

import org.commcare.android.crypt.CryptUtil;
import org.commcare.android.database.DbHelper;
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

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;

/**
 * @author ctsims
 *
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
			DbHelper dbh = new DbHelper(c) {
				@Override
				public SQLiteDatabase getHandle() {
					return db;
				}
			};
	
			Cipher incoming;
			Cipher outgoing;
			try {
				//For moving around files
				incoming = CryptUtil.getAesKeyCipher(unwrappedOldKey, Cipher.DECRYPT_MODE);
				outgoing = CryptUtil.getAesKeyCipher(unwrappedNewKey, Cipher.DECRYPT_MODE);
			} catch(GeneralSecurityException gse) {
				gse.printStackTrace();
				//if we got this far with messed up keys we have much bigger problems.
				Logger.log(AndroidLogger.TYPE_ERROR_CRYPTO, "Crypto error during sandbox migration");
				throw new RuntimeException(gse);
			}
			
			//TODO: At some point we should really just encode the existence/operations on files in the record models themselves
			//Models with Files: Form Record. Log Record
			SqlStorage<DeviceReportRecord> reports = new SqlStorage<DeviceReportRecord>(DeviceReportRecord.STORAGE_KEY, DeviceReportRecord.class, dbh);
			
			//Log records
			for(DeviceReportRecord r : reports) {
				File oldPath = new File(r.getFilePath());
				File newPath = FileUtil.getNewFileLocation(oldPath, newSandbox.getUuid(), true);
				
				//Copy to a new location while re-encrypting
				FileUtil.copyFile(oldPath, newPath, incoming, outgoing);
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
				File oldFolder;
				{
					
					//otherwise read and prepare the record
					Cursor oldRecord = cr.query(instanceURI, null, null, null, null);
					
					values.put(InstanceColumns.DISPLAY_NAME, oldRecord.getString(oldRecord.getColumnIndex(InstanceColumns.DISPLAY_NAME)));
					values.put(InstanceColumns.SUBMISSION_URI, oldRecord.getString(oldRecord.getColumnIndex(InstanceColumns.SUBMISSION_URI)));
					values.put(InstanceColumns.JR_FORM_ID, oldRecord.getString(oldRecord.getColumnIndex(InstanceColumns.JR_FORM_ID)));
					values.put(InstanceColumns.STATUS, oldRecord.getString(oldRecord.getColumnIndex(InstanceColumns.STATUS)));
					values.put(InstanceColumns.CAN_EDIT_WHEN_COMPLETE, oldRecord.getString(oldRecord.getColumnIndex(InstanceColumns.CAN_EDIT_WHEN_COMPLETE)));
					values.put(InstanceColumns.LAST_STATUS_CHANGE_DATE, oldRecord.getLong(oldRecord.getColumnIndex(InstanceColumns.LAST_STATUS_CHANGE_DATE)));
					values.put(InstanceColumns.DISPLAY_SUBTEXT, oldRecord.getString(oldRecord.getColumnIndex(InstanceColumns.DISPLAY_SUBTEXT)));
					
					
					//Copy over the other metadata
					
					oldFolder = new File(oldRecord.getString(oldRecord.getColumnIndex(InstanceColumns.INSTANCE_FILE_PATH)));
					
					oldRecord.close();
				}
				
				//find a new spot for it
				File newFolder = FileUtil.getNewFileLocation(oldFolder, newSandbox.getUuid(), true);
				
				//Create the new folder
				newFolder.mkdir();
				
				//Start copying over files
				for(File oldFile : oldFolder.listFiles()) {
					File newFile = new File(newFolder.getPath() + File.separator + oldFile.getName());
					FileUtil.copyFile(oldFile, newFile, incoming, outgoing);
				}
				
				//ok, new directory totally ready. Create a new instanceURI
				values.put(InstanceColumns.INSTANCE_FILE_PATH, newFolder.getAbsolutePath());
				Uri newUri = cr.insert(InstanceColumns.CONTENT_URI, values);
				record = record.updateStatus(newUri.toString(), record.getStatus());
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
	
}
