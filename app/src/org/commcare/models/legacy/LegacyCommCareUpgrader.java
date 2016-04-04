package org.commcare.models.legacy;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.preference.PreferenceManager;

import org.commcare.logging.AndroidLogEntry;
import org.commcare.logging.AndroidLogger;
import org.commcare.logging.DeviceReportRecord;
import org.commcare.models.database.user.models.FormRecord;
import org.commcare.models.database.user.models.GeocodeCacheModel;
import org.commcare.models.database.user.models.SessionStateDescriptor;
import org.commcare.resources.model.Resource;
import org.javarosa.core.services.Logger;

/**
 * This class exists in order to handle all of the logic associated with upgrading from one version
 * of CommCare ODK to another. It is going to get big and annoying.
 *
 * @author ctsims
 */
public class LegacyCommCareUpgrader {

    private final Context context;

    public LegacyCommCareUpgrader(Context c) {
        this.context = c;
    }

    public boolean doUpgrade(SQLiteDatabase database, int from, int to) {
        Logger.log(AndroidLogger.TYPE_MAINTENANCE, String.format("App DB Upgrade needed! Starting upgrade from %d to %d", from, to));
        if (from == 1) {
            if (upgradeOneTwo(database)) {
                from = 2;
            } else {
                return false;
            }
        }

        if (from == 26) {
            if (upgradeTwoSixtoTwoSeven(database)) {
                from = 27;
            } else {
                return false;
            }
        }

        if (from == 27) {
            if (upgradeTwoSeventoTwoEight(database)) {
                from = 28;
            } else {
                return false;
            }
        }

        if (from == 28) {
            if (upgradeTwoEighttoTwoNine(database)) {
                from = 29;
            } else {
                return false;
            }
        }

        if (from == 29) {
            if (upgradeTwoNineToThreeOh(database)) {
                from = 30;
            } else {
                return false;
            }
        }

        Logger.log(AndroidLogger.TYPE_MAINTENANCE, String.format("Upgrade %s", from == to ? "succesful" : "unsuccesful"));

        return from == to;
    }


    private boolean upgradeTwoNineToThreeOh(SQLiteDatabase database) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        prefs.edit().putBoolean("isValidated", true).commit();
        return true;
    }

    private boolean upgradeOneTwo(SQLiteDatabase database) {
        database.beginTransaction();
        LegacyTableBuilder builder = new LegacyTableBuilder("UPGRADE_RESOURCE_TABLE");
        builder.addData(new Resource());
        database.execSQL(builder.getTableCreateString());

        database.setVersion(2);
        database.setTransactionSuccessful();
        database.endTransaction();
        return true;
    }

    /**
     * Previous FormRecord entries were lacking, we're going to
     * wipe them out.
     */
    private boolean upgradeTwoSixtoTwoSeven(SQLiteDatabase database) {
        database.beginTransaction();

        //wipe out old Form Record table
        database.execSQL("drop table FORMRECORDS");

        //Build us a new one with the new structure
        LegacyTableBuilder builder = new LegacyTableBuilder("FORMRECORDS");
        builder.addData(new FormRecord());
        database.execSQL(builder.getTableCreateString());

        database.setTransactionSuccessful();
        database.endTransaction();
        return true;
    }


    private boolean upgradeTwoSeventoTwoEight(SQLiteDatabase database) {
        database.beginTransaction();

        LegacyTableBuilder builder = new LegacyTableBuilder(GeocodeCacheModel.STORAGE_KEY);
        builder.addData(new GeocodeCacheModel());
        database.execSQL(builder.getTableCreateString());

        database.setTransactionSuccessful();
        database.endTransaction();
        return true;
    }

    private boolean upgradeTwoEighttoTwoNine(SQLiteDatabase database) {

        String ssdTable = LegacyTableBuilder.scrubName("android_cc_session");
        String tempssdTable = LegacyTableBuilder.scrubName("android_cc_session" + "temp");

        int oldRows = countRows(database, ssdTable);
        try {
            database.beginTransaction();

            LegacyTableBuilder builder = new LegacyTableBuilder(AndroidLogEntry.STORAGE_KEY);
            builder.addData(new AndroidLogEntry());
            database.execSQL(builder.getTableCreateString());

            builder = new LegacyTableBuilder("log_records");
            builder.addData(new DeviceReportRecord());
            database.execSQL(builder.getTableCreateString());

            //SQLite can't add column constraints. You've gotta make a new table, copy everything over, and 
            //wipe the old one

            database.execSQL(String.format("ALTER TABLE %s RENAME TO %s;", ssdTable, tempssdTable));

            builder = new LegacyTableBuilder("android_cc_session");
            builder.setUnique(SessionStateDescriptor.META_FORM_RECORD_ID);
            builder.addData(new SessionStateDescriptor());
            database.execSQL(builder.getTableCreateString());

            String cols = builder.getColumns();

            database.execSQL(String.format("INSERT OR REPLACE INTO %s (%s) " +
                    "SELECT %s " +
                    "FROM %s;", ssdTable, cols, cols, tempssdTable));

            database.execSQL(String.format("DROP TABLE %s;", tempssdTable));

            database.setTransactionSuccessful();

            int newRows = countRows(database, ssdTable);
            if (oldRows != newRows) {
                Logger.log(AndroidLogger.TYPE_MAINTENANCE, String.format("Removed %s duplicate SessionStateDescriptor rows during DB Upgrade", String.valueOf(oldRows - newRows)));
            }

            return true;
        } finally {
            database.endTransaction();
        }
    }

    private int countRows(SQLiteDatabase database, String table) {
        Cursor c = null;
        try {
            c = database.rawQuery(String.format("SELECT COUNT(*) AS total FROM %s", table), new String[0]);
            c.moveToFirst();
            return c.getInt(0);
        } catch (Exception e) {
            return -1;
        } finally {
            if (c != null) {
                c.close();
            }
        }
    }
}
