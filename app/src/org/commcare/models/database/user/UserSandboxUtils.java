package org.commcare.models.database.user;

import android.content.Context;

import org.apache.commons.lang3.StringUtils;
import org.commcare.CommCareApp;
import org.commcare.CommCareApplication;
import org.commcare.android.database.app.models.UserKeyRecord;
import org.commcare.android.database.user.models.FormRecord;
import org.commcare.android.javarosa.DeviceReportRecord;
import org.commcare.models.database.AndroidDbHelper;
import org.commcare.models.database.IDatabase;
import org.commcare.models.database.SqlStorage;
import org.commcare.util.LogTypes;
import org.commcare.utils.FileUtil;
import org.javarosa.core.services.Logger;

import java.io.File;
import java.io.IOException;

import static org.commcare.utils.FileUtil.deleteFileOrDir;

/**
 * @author ctsims
 */
public class UserSandboxUtils {

    public static void migrateData(Context c, CommCareApp app,
                                   UserKeyRecord incomingSandbox, byte[] unwrappedOldKey,
                                   UserKeyRecord newSandbox, byte[] unwrappedNewKey)
            throws IOException {
        Logger.log(LogTypes.TYPE_MAINTENANCE, "Migrating an existing user sandbox for " + newSandbox.getUsername());
        String newKeyEncoded = rekeyDB(c, incomingSandbox, newSandbox, unwrappedOldKey, unwrappedNewKey);

        Logger.log(LogTypes.TYPE_MAINTENANCE, "Database is re-keyed and ready for use. Copying over files now");
        //OK, so now we have the Db transitioned. What we need to do now is go through and rekey all of our file references.
        final IDatabase db = CommCareApplication.instance().getUserDbOpenHelper(newSandbox.getUuid(), newKeyEncoded);

        try {
            //If we were able to iterate over the users, the key was fine, so let's use it to open our db
            AndroidDbHelper dbh = new AndroidDbHelper(c) {
                @Override
                public IDatabase getHandle() {
                    return db;
                }
            };

            //TODO: At some point we should really just encode the existence/operations on files in the record models themselves
            //Models with Files: Form Record. Log Record
            SqlStorage<DeviceReportRecord> reports = new SqlStorage<>(DeviceReportRecord.STORAGE_KEY, DeviceReportRecord.class, dbh);
            migrateDeviceReports(reports, newSandbox);

            Logger.log(LogTypes.TYPE_MAINTENANCE, "Copied over all of the device reports. Moving on to the form records");

            SqlStorage<FormRecord> formRecords = new SqlStorage<>(FormRecord.STORAGE_KEY, FormRecord.class, dbh);
            migrateFormRecords(formRecords, newSandbox);
        } finally {
            db.close();
        }

        Logger.log(LogTypes.TYPE_MAINTENANCE, "All form records copied over");

        //OK! So we should be all set, here. Mark the new sandbox as ready and the old sandbox as ready for cleanup.
        finalizeMigration(app, incomingSandbox, newSandbox);
    }

    /**
     * Make a copy of the incoming sandbox's database and re-key it to use the new key.
     */
    private static String rekeyDB(Context c, UserKeyRecord incomingSandbox, UserKeyRecord newSandbox,
                                  byte[] unwrappedOldKey, byte[] unwrappedNewKey)
            throws IOException {
        File oldDb = c.getDatabasePath(UserDatabaseSchemaManager.getDbName(incomingSandbox.getUuid()));
        File newDb = c.getDatabasePath(UserDatabaseSchemaManager.getDbName(newSandbox.getUuid()));

        //TODO: Make sure old sandbox is already on newest version?
        if (newDb.exists()) {
            if (!newDb.delete()) {
                throw new IOException("Couldn't clear file location " + newDb.getAbsolutePath() + " for new sandbox database");
            }
        }

        FileUtil.copyFile(oldDb, newDb);

        Logger.log(LogTypes.TYPE_MAINTENANCE, "Created a copy of the DB for the new sandbox. Re-keying it...");

        String oldKeyEncoded = getSqlCipherEncodedKey(unwrappedOldKey);
        String newKeyEncoded = getSqlCipherEncodedKey(unwrappedNewKey);
        IDatabase rawDbHandle = CommCareApplication.instance().getUserDbOpenHelperFromFile(newDb.getAbsolutePath(), oldKeyEncoded);

        rawDbHandle.execSQL("PRAGMA key = '" + oldKeyEncoded + "';");
        rawDbHandle.execSQL("PRAGMA rekey  = '" + newKeyEncoded + "';");
        rawDbHandle.close();
        return newKeyEncoded;
    }

    private static void migrateDeviceReports(SqlStorage<DeviceReportRecord> reports,
                                             UserKeyRecord newSandbox) throws IOException {
        for (DeviceReportRecord r : reports) {
            File oldPath = new File(r.getFilePath());
            File newPath = FileUtil.getNewFileLocation(oldPath, newSandbox.getUuid(), true);

            // Copy to a new location while re-encrypting
            FileUtil.copyFile(oldPath, newPath);
        }
    }

    /**
     * Move all files and update the form record with the new file path.
     */
    private static void migrateFormRecords(SqlStorage<FormRecord> formRecords,
                                           UserKeyRecord newSandbox) throws IOException {
        for (FormRecord record : formRecords) {
            // Skip records with no files associated with them
            if (StringUtils.isEmpty(record.getFilePath())) {
                continue;
            }

            //Copy over the other metadata
            File oldForm = new File(record.getFilePath());
            File oldFolder = oldForm.getParentFile();

            //find a new spot for it and copy
            File newFolder = FileUtil.getNewFileLocation(oldFolder, newSandbox.getUuid(), true);
            FileUtil.copyFileDeep(oldFolder, newFolder);

            File newfileToWrite = null;
            for (File f : newFolder.listFiles()) {
                if (f.getName().equals(oldForm.getName())) {
                    newfileToWrite = f;
                }
            }

            //ok, new directory totally ready. Update the record filepath
            record.setFilePath(newfileToWrite.getAbsolutePath());
            formRecords.write(record);
        }
    }

    private static void finalizeMigration(CommCareApp app, UserKeyRecord incomingSandbox, UserKeyRecord newSandbox) {
        SqlStorage<UserKeyRecord> ukr = app.getStorage(UserKeyRecord.class);

        IDatabase ukrdb = ukr.getAccessLock();
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
        for (byte aByte : bytes) {
            String hexDigits = Integer.toHexString(0xFF & aByte).toUpperCase();
            while (hexDigits.length() < 2) {
                hexDigits = "0" + hexDigits;
            }
            hexString += hexDigits;
        }
        hexString = hexString + "\"";
        return hexString;
    }


    public static void purgeSandbox(Context context, CommCareApp app, UserKeyRecord sandbox, byte[] key) {

        Logger.log(LogTypes.TYPE_MAINTENANCE, "Wiping sandbox " + sandbox.getUuid());

        //Ok, three steps here. Wipe files out, wipe database, remove key record

        //If the db is gone already, just remove the record and move on (something odd has happened)
        if (!context.getDatabasePath(UserDatabaseSchemaManager.getDbName(sandbox.getUuid())).exists()) {
            Logger.log(LogTypes.TYPE_MAINTENANCE, "Sandbox " + sandbox.getUuid() + " has already been purged. removing the record");

            SqlStorage<UserKeyRecord> ukr = app.getStorage(UserKeyRecord.class);
            ukr.remove(sandbox);
        }

        final IDatabase db = CommCareApplication.instance().getUserDbOpenHelper(sandbox.getUuid(), getSqlCipherEncodedKey(key));

        try {
            AndroidDbHelper dbh = new AndroidDbHelper(context) {
                @Override
                public IDatabase getHandle() {
                    return db;
                }
            };

            SqlStorage<DeviceReportRecord> reports = new SqlStorage<>(DeviceReportRecord.STORAGE_KEY, DeviceReportRecord.class, dbh);

            //Log records
            for (DeviceReportRecord r : reports) {
                File oldPath = new File(r.getFilePath());
                if (oldPath.exists()) {
                    FileUtil.deleteFileOrDir(oldPath);
                }
            }

            Logger.log(LogTypes.TYPE_MAINTENANCE, "Device Report files removed");

            // Delete Form Files
            SqlStorage<FormRecord> formRecords = new SqlStorage<>(FormRecord.STORAGE_KEY, FormRecord.class, dbh);
            for (FormRecord record : formRecords) {
                if (!StringUtils.isEmpty(record.getFilePath())) {
                    String instanceDir = (new File(record.getFilePath())).getParent();
                    deleteFileOrDir(instanceDir);
                }
            }

        } finally {
            db.close();
        }

        Logger.log(LogTypes.TYPE_MAINTENANCE, "All files removed for sandbox. Deleting DB");

        context.getDatabasePath(UserDatabaseSchemaManager.getDbName(sandbox.getUuid())).delete();

        Logger.log(LogTypes.TYPE_MAINTENANCE, "Database is gone. Get rid of this record");

        //OK! So we should be all set, here. Mark the new sandbox as ready and the old sandbox as ready for cleanup.
        SqlStorage<UserKeyRecord> ukr = app.getStorage(UserKeyRecord.class);
        ukr.remove(sandbox);

        Logger.log(LogTypes.TYPE_MAINTENANCE, "Purge complete");
    }

}
