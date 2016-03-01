/**
 *
 */
package org.commcare.models.legacy;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDatabase.CursorFactory;
import android.database.sqlite.SQLiteOpenHelper;

import org.commcare.android.logging.AndroidLogEntry;
import org.commcare.android.logging.DeviceReportRecord;
import org.commcare.android.logic.GlobalConstants;
import org.commcare.models.database.user.models.ACase;
import org.commcare.models.database.user.models.FormRecord;
import org.commcare.models.database.user.models.GeocodeCacheModel;
import org.commcare.models.database.user.models.SessionStateDescriptor;
import org.commcare.resources.model.Resource;
import org.javarosa.core.model.User;
import org.javarosa.core.model.instance.FormInstance;

/**
 * @author ctsims
 */
public class LegacyCommCareOpenHelper extends SQLiteOpenHelper {
    /*
     * Version History:
     * 28 - Added the geocaching table
     * 29 - Added Logging table. Made SSD FormRecord_ID unique
     * 30 - Added validation, need to pre-flag validation
     */

    private static final int DATABASE_VERSION = 30;
    private final Context context;

    public LegacyCommCareOpenHelper(Context context) {
        this(context, null);
    }

    public LegacyCommCareOpenHelper(Context context, CursorFactory factory) {
        super(context, GlobalConstants.CC_DB_NAME, factory, DATABASE_VERSION);
        this.context = context;
    }

    @Override
    public void onCreate(SQLiteDatabase database) {
        try {
            database.beginTransaction();

            LegacyTableBuilder builder = new LegacyTableBuilder(ACase.STORAGE_KEY);
            builder.addData(new ACase());
            builder.setUnique(ACase.INDEX_CASE_ID);
            database.execSQL(builder.getTableCreateString());

            builder = new LegacyTableBuilder("USER");
            builder.addData(new User());
            database.execSQL(builder.getTableCreateString());

            builder = new LegacyTableBuilder("GLOBAL_RESOURCE_TABLE");
            builder.addData(new Resource());
            database.execSQL(builder.getTableCreateString());

            builder = new LegacyTableBuilder("UPGRADE_RESOURCE_TABLE");
            builder.addData(new Resource());
            database.execSQL(builder.getTableCreateString());

            builder = new LegacyTableBuilder("FORMRECORDS");
            builder.addData(new FormRecord());
            database.execSQL(builder.getTableCreateString());

            builder = new LegacyTableBuilder("android_cc_session");
            builder.setUnique(SessionStateDescriptor.META_FORM_RECORD_ID);
            builder.addData(new SessionStateDescriptor());
            database.execSQL(builder.getTableCreateString());

            builder = new LegacyTableBuilder("fixture");
            builder.addData(new FormInstance());
            database.execSQL(builder.getTableCreateString());

            builder = new LegacyTableBuilder(GeocodeCacheModel.STORAGE_KEY);
            builder.addData(new GeocodeCacheModel());
            database.execSQL(builder.getTableCreateString());

            builder = new LegacyTableBuilder(AndroidLogEntry.STORAGE_KEY);
            builder.addData(new AndroidLogEntry());
            database.execSQL(builder.getTableCreateString());

            builder = new LegacyTableBuilder("log_records");
            builder.addData(new DeviceReportRecord());
            database.execSQL(builder.getTableCreateString());


            database.setVersion(DATABASE_VERSION);

            database.setTransactionSuccessful();
        } finally {
            database.endTransaction();
        }
    }

    @Override
    public void onUpgrade(SQLiteDatabase database, int oldVersion, int newVersion) {
        LegacyCommCareUpgrader upgrader = new LegacyCommCareUpgrader(context);

        //Evaluate success here somehow. Also, we'll need to log in to
        //mess with anything in the DB, or any old encrypted files, we need a hook for that...
        upgrader.doUpgrade(database, oldVersion, newVersion);
    }
}
