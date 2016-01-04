package org.commcare.android.database;

import android.util.Pair;

import net.sqlcipher.Cursor;
import net.sqlcipher.database.SQLiteDatabase;

import org.commcare.android.util.SessionUnavailableException;
import org.commcare.modern.database.DatabaseHelper;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;

import java.io.File;
import java.io.IOException;
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

    protected static void removeFiles(List<Integer> idsBeingRemoved,
                                      AndroidDbHelper helper,
                                      String table) {
        // delete files storing data for entries being removed
        for (Integer id : idsBeingRemoved) {
            String filename = HybridFileBackedSqlHelpers.getEntryFilename(helper, table, id);
            if (filename != null) {
                File datafile = new File(filename);
                datafile.delete();
            }
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
        String uniqueSuffix = "_" + (Math.random() * 5);
        String timeAsString =
                DateTime.now().toString(DateTimeFormat.forPattern("MM_dd_yyyy_HH_mm_ss"));
        String filename = timeAsString + uniqueSuffix;
        File newFile = new File(dbDir, filename);
        // keep trying until we find a filename that doesn't already exist
        while (newFile.exists()) {
            newFile = getUniqueFilename(dbDir);
        }
        return newFile;
    }
}
