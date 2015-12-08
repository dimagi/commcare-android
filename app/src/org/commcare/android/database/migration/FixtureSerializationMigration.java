package org.commcare.android.database.migration;

import android.content.Context;
import android.util.Log;

import net.sqlcipher.Cursor;
import net.sqlcipher.database.SQLiteDatabase;

import org.commcare.android.database.ConcreteAndroidDbHelper;
import org.commcare.android.database.DbUtil;
import org.commcare.android.database.SqlStorage;
import org.commcare.android.javarosa.AndroidLogger;
import org.commcare.dalvik.application.CommCareApplication;
import org.javarosa.core.model.instance.FormInstance;
import org.javarosa.core.services.Logger;
import org.javarosa.core.services.storage.Persistable;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.util.Vector;

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

    public static boolean migrateFixtureDbBytes(SQLiteDatabase db, Context c) {
        // Not sure how long this process should take, so tell the service to
        // wait longer to make sure this can finish.
        CommCareApplication._().setCustomServiceBindTimeout(60 * 5 * 1000);
        long start = System.currentTimeMillis();
        db.beginTransaction();
        ConcreteAndroidDbHelper helper = new ConcreteAndroidDbHelper(c, db);
        Cursor cur = null;
        DataInputStream fixtureByteStream = null;
        try {
            SqlStorage<Persistable> userFixtureStorage =
                    new SqlStorage<Persistable>("fixture", FormInstance.class, helper);
            cur = db.query("fixture", new String[]{DbUtil.ID_COL}, null, null, null, null, null);
            Vector<Integer> ids = SqlStorage.fillIdWindow(cur, DbUtil.ID_COL);
            for (Integer id : ids) {
                FormInstance fixture = new FormInstance();

                fixtureByteStream =
                        new DataInputStream(new ByteArrayInputStream(userFixtureStorage.readBytes(id)));
                fixture.migrateSerialization(fixtureByteStream, helper.getPrototypeFactory());
                userFixtureStorage.update(id, fixture);
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
            Logger.log(AndroidLogger.SOFT_ASSERT, "fixture serialization db migration failed");
            Logger.exception(e);
            return false;
        } finally {
            if (fixtureByteStream != null) {
                try {
                    fixtureByteStream.close();
                } catch (Exception e) {
                }
            }
            if (cur != null) {
                cur.close();
            }
            db.endTransaction();
            long elapse = System.currentTimeMillis() - start;
            Log.d(TAG, "Serialized fixture update complete in " + elapse + "ms");
        }
    }
}
