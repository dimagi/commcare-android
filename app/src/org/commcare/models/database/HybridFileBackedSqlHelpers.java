package org.commcare.models.database;

import android.content.ContentValues;
import android.util.Pair;

import net.sqlcipher.Cursor;
import net.sqlcipher.database.SQLiteDatabase;

import org.commcare.android.logging.AndroidLogger;
import org.commcare.modern.database.DatabaseHelper;
import org.commcare.utils.SessionUnavailableException;
import org.javarosa.core.services.Logger;
import org.javarosa.core.util.PropertyUtils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Methods for performing column queries on database table that uses files to
 * store serialized object payloads.
 *
 * @author Phillip Mates (pmates@dimagi.com).
 */
public class HybridFileBackedSqlHelpers {

    protected static Pair<String, byte[]> getEntryFilenameAndKey(AndroidDbHelper helper,
                                                                 String table,
                                                                 int id) {
        Cursor c;
        try {
            c = helper.getHandle().query(table, HybridFileBackedSqlStorage.dataColumns,
                    DatabaseHelper.ID_COL + "=?",
                    new String[]{String.valueOf(id)}, null, null, null);
        } catch (SessionUnavailableException e) {
            throw new UserStorageClosedException(e.getMessage());
        }

        try {
            c.moveToFirst();
            return new Pair<>(c.getString(c.getColumnIndexOrThrow(DatabaseHelper.FILE_COL)),
                    c.getBlob(c.getColumnIndexOrThrow(DatabaseHelper.AES_COL)));
        } finally {
            if (c != null) {
                c.close();
            }
        }
    }

    protected static String getEntryFilename(AndroidDbHelper helper,
                                             String table, int id) {
        Cursor c;
        SQLiteDatabase db;
        try {
            db = helper.getHandle();
        } catch (SessionUnavailableException e) {
            throw new UserStorageClosedException(e.getMessage());
        }

        String[] columns = new String[]{DatabaseHelper.FILE_COL};
        c = db.query(table, columns, DatabaseHelper.ID_COL + "=?",
                new String[]{String.valueOf(id)}, null, null, null);

        try {
            c.moveToFirst();
            return c.getString(c.getColumnIndexOrThrow(DatabaseHelper.FILE_COL));
        } finally {
            if (c != null) {
                c.close();
            }
        }
    }

    protected static List<String> getFilesToRemove(List<Integer> idsBeingRemoved,
                                                   AndroidDbHelper helper,
                                                   String table) {
        ArrayList<String> files = new ArrayList<>();
        // delete files storing data for entries being removed
        for (Integer id : idsBeingRemoved) {
            String filename = HybridFileBackedSqlHelpers.getEntryFilename(helper, table, id);
            if (filename != null && new File(filename).exists()) {
                files.add(filename);
            }
        }
        return files;
    }

    protected static void removeFiles(List<String> filesToRemove) {
        for (String filename : filesToRemove) {
            File datafile = new File(filename);
            datafile.delete();
        }
    }

    protected static void setFileAsOrphan(SQLiteDatabase db, String filename) {
        db.beginTransaction();
        try {
            ContentValues cv = new ContentValues();
            cv.put(DatabaseHelper.FILE_COL, filename);
            db.insertOrThrow(DbUtil.orphanFileTableName, DatabaseHelper.FILE_COL, cv);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    protected static void unsetFileAsOrphan(SQLiteDatabase db, String filename) {
        int deleteCount = db.delete(DbUtil.orphanFileTableName, DatabaseHelper.FILE_COL + "=?", new String[]{filename});
        if (deleteCount != 1) {
            Logger.log(AndroidLogger.SOFT_ASSERT,
                    "Unable to unset orphaned file: " + deleteCount + " entries effected.b");
        }
    }

    /**
     * Remove files in the orphaned file table. Files are added to this table
     * when file-backed db transactions fail, leaving the file on the
     * filesystem.
     *
     * Order of operations expects filenames to be globally unique.
     */
    public static void removeOrphanedFiles(SQLiteDatabase db) {
        Cursor cur = db.query(DbUtil.orphanFileTableName, new String[]{DatabaseHelper.FILE_COL}, null, null, null, null, null);
        ArrayList<String> files = new ArrayList<>();
        try {
            if (cur.getCount() > 0) {
                cur.moveToFirst();
                int fileColIndex = cur.getColumnIndexOrThrow(DatabaseHelper.FILE_COL);
                while (!cur.isAfterLast()) {
                    files.add(cur.getString(fileColIndex));
                    cur.moveToNext();
                }
            }
        } finally {
            if (cur != null) {
                cur.close();
            }
        }

        removeFiles(files);

        db.beginTransaction();
        try {
            db.delete(DbUtil.orphanFileTableName, null, null);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    protected static File newFileForEntry(File dbDir) throws IOException {
        File newFile = getUniqueFilename(dbDir);

        if (!newFile.createNewFile()) {
            throw new RuntimeException("Trying to create a new file using existing filename; " +
                    "Shouldn't be possible since we already checked for uniqueness");
        }

        return newFile;
    }

    private static File getUniqueFilename(File dbDir) {
        String filename = PropertyUtils.genUUID();
        File newFile = new File(dbDir, filename);
        // keep trying until we find a filename that doesn't already exist
        while (newFile.exists()) {
            newFile = getUniqueFilename(dbDir);
        }
        return newFile;
    }
}
