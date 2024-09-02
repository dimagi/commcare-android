package org.commcare.models.database.connect;

import android.content.Context;

import net.sqlcipher.database.SQLiteDatabase;
import net.sqlcipher.database.SQLiteException;
import net.sqlcipher.database.SQLiteOpenHelper;

import org.commcare.android.database.connect.models.ConnectAppRecord;
import org.commcare.android.database.connect.models.ConnectJobAssessmentRecord;
import org.commcare.android.database.connect.models.ConnectJobDeliveryRecord;
import org.commcare.android.database.connect.models.ConnectJobLearningRecord;
import org.commcare.android.database.connect.models.ConnectJobPaymentRecord;
import org.commcare.android.database.connect.models.ConnectJobRecord;
import org.commcare.android.database.connect.models.ConnectLearnModuleSummaryRecord;
import org.commcare.android.database.connect.models.ConnectLinkedAppRecord;
import org.commcare.android.database.connect.models.ConnectPaymentUnitRecord;
import org.commcare.android.database.connect.models.ConnectUserRecord;
import org.commcare.logging.DataChangeLog;
import org.commcare.logging.DataChangeLogger;
import org.commcare.models.database.DbUtil;
import org.commcare.models.database.user.UserSandboxUtils;
import org.commcare.modern.database.TableBuilder;
import org.commcare.util.Base64;
import org.commcare.util.Base64DecoderException;

import java.io.File;

/**
 * The helper for opening/updating the Connect (encrypted) db space for CommCare.
 *
 * @author dviggiano
 */
public class DatabaseConnectOpenHelper extends SQLiteOpenHelper {
    /**
     * V.2 - Added ConnectJobRecord, ConnectAppInfo, and ConnectLearningModuleInfo tables
     * V.3 - Added date_claimed column to ConnectJobRecord,
     *          and reason column to ConnectJobDeliveryRecord
     * V.4 - Added confirmed and confirmedDate fields to ConnectJobPaymentRecord
     *          Added link offer info to ConnectLinkedAppRecord
     * V.5 - Added projectStartDate and isActive to ConnectJobRecord
     * V.6 - Added pin,secondaryPhoneVerified, and registrationDate fields to ConnectUserRecord
     * V.7 - Added ConnectPaymentUnitRecord table
     */
    private static final int CONNECT_DB_VERSION = 8;

    private static final String CONNECT_DB_LOCATOR = "database_connect";

    private final Context mContext;

    public DatabaseConnectOpenHelper(Context context) {
        super(context, CONNECT_DB_LOCATOR, null, CONNECT_DB_VERSION);
        this.mContext = context;
    }

    private static File getDbFile(Context context) {
        return context.getDatabasePath(CONNECT_DB_LOCATOR);
    }

    public static boolean dbExists(Context context) {
        return getDbFile(context).exists();
    }

    public static void deleteDb(Context context) {
        getDbFile(context).delete();
    }

    public static void rekeyDB(SQLiteDatabase db, String newPassphrase) throws Base64DecoderException {
        if(db != null) {
            byte[] newBytes = Base64.decode(newPassphrase);
            String newKeyEncoded = UserSandboxUtils.getSqlCipherEncodedKey(newBytes);

            db.query("PRAGMA rekey = '" + newKeyEncoded + "';");
            db.close();
        }
    }

    @Override
    public void onCreate(SQLiteDatabase database) {
        database.beginTransaction();
        try {
            TableBuilder builder = new TableBuilder(ConnectUserRecord.class);
            database.execSQL(builder.getTableCreateString());

            builder = new TableBuilder(ConnectLinkedAppRecord.class);
            database.execSQL(builder.getTableCreateString());

            builder = new TableBuilder(ConnectJobRecord.class);
            database.execSQL(builder.getTableCreateString());

            builder = new TableBuilder(ConnectAppRecord.class);
            database.execSQL(builder.getTableCreateString());

            builder = new TableBuilder(ConnectLearnModuleSummaryRecord.class);
            database.execSQL(builder.getTableCreateString());

            builder = new TableBuilder(ConnectJobLearningRecord.class);
            database.execSQL(builder.getTableCreateString());

            builder = new TableBuilder(ConnectJobAssessmentRecord.class);
            database.execSQL(builder.getTableCreateString());

            builder = new TableBuilder(ConnectJobDeliveryRecord.class);
            database.execSQL(builder.getTableCreateString());

            builder = new TableBuilder(ConnectJobPaymentRecord.class);
            database.execSQL(builder.getTableCreateString());

            builder = new TableBuilder(ConnectPaymentUnitRecord.class);
            database.execSQL(builder.getTableCreateString());

            DbUtil.createNumbersTable(database);

            database.setVersion(CONNECT_DB_VERSION);

            database.setTransactionSuccessful();
        } finally {
            database.endTransaction();
        }
    }

    @Override
    public SQLiteDatabase getWritableDatabase(String key) {
        try {
            return super.getWritableDatabase(key);
        } catch (SQLiteException sqle) {
            DbUtil.trySqlCipherDbUpdate(key, mContext, CONNECT_DB_LOCATOR);
            return super.getWritableDatabase(key);
        }
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        DataChangeLogger.log(new DataChangeLog.DbUpgradeStart("Connect", oldVersion, newVersion));
        new ConnectDatabaseUpgrader(mContext).upgrade(db, oldVersion, newVersion);
        DataChangeLogger.log(new DataChangeLog.DbUpgradeComplete("Connect", oldVersion, newVersion));
    }
}
