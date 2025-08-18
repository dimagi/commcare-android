package org.commcare.models.database.connect;

import android.content.Context;
import android.database.sqlite.SQLiteException;

import net.zetetic.database.sqlcipher.SQLiteDatabase;
import net.zetetic.database.sqlcipher.SQLiteOpenHelper;

import org.commcare.CommCareApplication;
import org.commcare.android.database.connect.models.ConnectAppRecord;
import org.commcare.android.database.connect.models.ConnectChannel;
import org.commcare.android.database.connect.models.ConnectJobAssessmentRecord;
import org.commcare.android.database.connect.models.ConnectJobDeliveryFlagRecord;
import org.commcare.android.database.connect.models.ConnectJobDeliveryRecord;
import org.commcare.android.database.connect.models.ConnectJobLearningRecord;
import org.commcare.android.database.connect.models.ConnectJobPaymentRecord;
import org.commcare.android.database.connect.models.ConnectJobRecord;
import org.commcare.android.database.connect.models.ConnectLearnModuleSummaryRecord;
import org.commcare.android.database.connect.models.ConnectLinkedAppRecord;
import org.commcare.android.database.connect.models.ConnectMessage;
import org.commcare.android.database.connect.models.ConnectMessagingChannelRecord;
import org.commcare.android.database.connect.models.ConnectMessagingMessageRecord;
import org.commcare.android.database.connect.models.ConnectPaymentUnitRecord;
import org.commcare.android.database.connect.models.ConnectUserRecord;
import org.commcare.android.database.connect.models.PersonalIdCredential;
import org.commcare.android.database.connect.models.PushNotificationRecord;
import org.commcare.logging.DataChangeLog;
import org.commcare.logging.DataChangeLogger;
import org.commcare.models.database.IDatabase;
import org.commcare.models.database.DbUtil;
import org.commcare.models.database.EncryptedDatabaseAdapter;
import org.commcare.models.database.user.UserSandboxUtils;
import org.commcare.modern.database.TableBuilder;
import org.commcare.util.Base64;
import org.commcare.util.Base64DecoderException;
import org.commcare.utils.CrashUtil;

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
     * V.8 - Added is_user_suspended to ConnectJobRecord
     * V.9 - Added using_local_passphrase to ConnectLinkedAppRecord
     * V.10 - Added last_accessed column to ConnectLinkedAppRecord
     * V.11 - Added daily start and finish times to ConnectJobRecord
     * V.12 - Added ConnectMessagingChannelRecord table and ConnectMessagingMessageRecord table
     * V.13 - Added ConnectJobDeliveryFlagRecord table
     * V.14 - Added a photo and isDemo field to ConnectUserRecord
     * V.16 - Added  personal_id_credential table
     * V.17 - Added  push_notification_history,connect_channel,connect_message table
     */
    private static final int CONNECT_DB_VERSION = 16;

    private static final String CONNECT_DB_LOCATOR = "database_connect";

    private final Context mContext;
    private final String key;

    public DatabaseConnectOpenHelper(Context context, String key) {
        super(context, CONNECT_DB_LOCATOR, key, null, CONNECT_DB_VERSION, 0, null, null, false);
        this.mContext = context;
        this.key = key;
    }

    private static File getDbFile() {
        return CommCareApplication.instance().getDatabasePath(CONNECT_DB_LOCATOR);
    }

    public static boolean dbExists() {
        return getDbFile().exists();
    }

    public static void deleteDb() {
        getDbFile().delete();
    }

    public static void rekeyDB(IDatabase db, String newPassphrase) throws Base64DecoderException {
        if(db != null) {
            byte[] newBytes = Base64.decode(newPassphrase);
            String newKeyEncoded = UserSandboxUtils.getSqlCipherEncodedKey(newBytes);

            db.execSQL("PRAGMA rekey = '" + newKeyEncoded + "';");
            db.close();
        }
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        IDatabase database = new EncryptedDatabaseAdapter(db);
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

            builder = new TableBuilder(ConnectMessagingChannelRecord.class);
            database.execSQL(builder.getTableCreateString());

            builder = new TableBuilder(ConnectMessagingMessageRecord.class);
            database.execSQL(builder.getTableCreateString());

            builder = new TableBuilder(ConnectJobDeliveryFlagRecord.class);
            database.execSQL(builder.getTableCreateString());

            builder = new TableBuilder(PersonalIdCredential.class);
            database.execSQL(builder.getTableCreateString());

            builder = new TableBuilder(PushNotificationRecord.class);
            database.execSQL(builder.getTableCreateString());

            builder = new TableBuilder(ConnectChannel.class);
            database.execSQL(builder.getTableCreateString());

            builder = new TableBuilder(ConnectMessage.class);
            database.execSQL(builder.getTableCreateString());

            DbUtil.createNumbersTable(database);

            database.setVersion(CONNECT_DB_VERSION);

            database.setTransactionSuccessful();
        } finally {
            database.endTransaction();
        }
    }

    @Override
    public SQLiteDatabase getWritableDatabase() {
        try {
            return super.getWritableDatabase();
        } catch (SQLiteException sqle) {
            DbUtil.trySqlCipherDbUpdate(key, mContext, CONNECT_DB_LOCATOR);
            try {
                return super.getWritableDatabase();
            } catch (SQLiteException e) {
                // Handle the exception, log the error, or inform the user
                CrashUtil.log(e.getMessage());
                throw e;
            }
        }
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        DataChangeLogger.log(new DataChangeLog.DbUpgradeStart("Connect", oldVersion, newVersion));
        new ConnectDatabaseUpgrader(mContext).upgrade(new EncryptedDatabaseAdapter(db), oldVersion, newVersion);
        DataChangeLogger.log(new DataChangeLog.DbUpgradeComplete("Connect", oldVersion, newVersion));
    }
}
