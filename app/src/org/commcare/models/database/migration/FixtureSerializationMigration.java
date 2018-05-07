package org.commcare.models.database.migration;

import android.content.Context;
import android.util.Log;

import net.sqlcipher.Cursor;
import net.sqlcipher.database.SQLiteDatabase;

import org.commcare.CommCareApplication;
import org.commcare.models.database.ConcreteAndroidDbHelper;
import org.commcare.models.database.DbUtil;
import org.commcare.models.database.HybridFileBackedSqlStorage;
import org.commcare.models.database.SqlStorage;
import org.commcare.models.database.SqlStorageIterator;
import org.commcare.models.database.UnencryptedHybridFileBackedSqlStorage;
import org.commcare.modern.database.TableBuilder;
import org.commcare.util.LogTypes;
import org.javarosa.core.model.instance.FormInstance;
import org.javarosa.core.services.Logger;
import org.javarosa.core.services.storage.Persistable;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;

/**
 * Deserialize all fixtures in a db using old form instance serialization
 * scheme, and re-serialize them using the new scheme.
 *
 * The updated form instance serialization scheme provides more comprehensive
 * handling of attributes, preserving datatypes, and other attributes across
 * serializations.
 *
 * Used in app database migration V.7 and user database migration V.9
 *
 * This code becomes irrelevant once no more devices need to be migrated off of
 * 2.24
 *
 * @author Phillip Mates (pmates@dimagi.com).
 */
public class FixtureSerializationMigration {
    private static final String TAG = FixtureSerializationMigration.class.getSimpleName();

    public static boolean migrateUnencryptedFixtureDbBytes(SQLiteDatabase db,
                                                           Context c) {
        return migrateFixtureDbBytes(db, c, null, null);
    }

    public static boolean migrateFixtureDbBytes(SQLiteDatabase db, Context c,
                                                String directoryName,
                                                byte[] fileMigrationKeySeed) {
        long start = System.currentTimeMillis();

        ConcreteAndroidDbHelper helper = new ConcreteAndroidDbHelper(c, db);
        DataInputStream fixtureByteStream = null;

        db.beginTransaction();
        try {
            HybridFileBackedSqlStorage<Persistable> fixtureStorage;
            if (fileMigrationKeySeed != null) {
                fixtureStorage =
                        new HybridFileBackedSqlStorageForMigration<Persistable>("fixture", FormInstance.class, helper, directoryName, fileMigrationKeySeed);
            } else {
                fixtureStorage =
                        new UnencryptedHybridFileBackedSqlStorage<Persistable>("fixture",
                                FormInstance.class, helper, CommCareApplication.instance().getCurrentApp());
            }
            SqlStorage<Persistable> oldUserFixtureStorage =
                    new SqlStorage<Persistable>("oldfixture", FormInstance.class, helper);
            int migratedFixtureCount = 0;
            for (SqlStorageIterator i = oldUserFixtureStorage.iterate(false); i.hasMore(); ) {
                int id = i.nextID();
                migratedFixtureCount++;
                Log.d(TAG, "migrating fixture " + migratedFixtureCount);
                FormInstance fixture = new FormInstance();

                fixtureByteStream =
                        new DataInputStream(new ByteArrayInputStream(oldUserFixtureStorage.readBytes(id)));
                fixture.migrateSerialization(fixtureByteStream, helper.getPrototypeFactory());
                fixture.setID(-1);
                fixtureStorage.write(fixture);
            }
            db.setTransactionSuccessful();
            return true;
        } catch (Exception e) {
            // Even if it failed, let the migration think it was successful,
            // otherwise the app will crash. It's important to let user get to
            // a point where they can sync data and then clear user data and
            // restore, which ultimately has the same effect as running the
            // fixture serialization migration.
            db.setTransactionSuccessful();
            Logger.exception("fixture serialization db migration failed", e);
            // allow subsequent migrations to be processed. Will potentially
            // lead to failure if those migrations make use of fixtures.
            return true;
        } finally {
            if (fixtureByteStream != null) {
                try {
                    fixtureByteStream.close();
                } catch (Exception e) {
                }
            }
            db.endTransaction();
            long elapse = System.currentTimeMillis() - start;
            Log.d(TAG, "Serialized fixture update complete in " + elapse + "ms");
        }
    }

    public static void stageFixtureTables(SQLiteDatabase db) {
        db.beginTransaction();
        try {
            boolean resumingMigration = doesTempFixtureTableExist(db);

            DbUtil.createOrphanedFileTable(db);
            if (resumingMigration) {
                db.execSQL("DROP TABLE IF EXISTS fixture;");
            } else {
                db.execSQL("ALTER TABLE fixture RENAME TO oldfixture;");
            }

            // make new fixture db w/ filepath and encryption key columns
            TableBuilder builder = new TableBuilder("fixture");
            builder.addFileBackedData(new FormInstance());
            db.execSQL(builder.getTableCreateString());
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    private static boolean doesTempFixtureTableExist(SQLiteDatabase db) {
        // "SELECT name FROM sqlite_master WHERE type='table' AND name='oldfixture';";
        String whereClause = "type =? AND name =?";
        String[] whereArgs = new String[]{
                "table",
                "oldfixture"
        };
        Cursor cursor = null;
        try {
            cursor = db.query("sqlite_master", new String[]{"name"},
                    whereClause, whereArgs, null, null, null);
            return cursor.getCount() > 0;
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    public static void dropTempFixtureTable(SQLiteDatabase db) {
        db.beginTransaction();
        try {
            db.execSQL("DROP TABLE oldfixture;");
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }
}
