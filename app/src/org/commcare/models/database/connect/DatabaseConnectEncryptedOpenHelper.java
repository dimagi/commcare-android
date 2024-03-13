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
import org.commcare.android.database.connect.models.ConnectUserRecord;
import org.commcare.models.database.DbUtil;
import org.commcare.modern.database.TableBuilder;

public class DatabaseConnectEncryptedOpenHelper extends SQLiteOpenHelper {
    private static final int CONNECT_ENCRYPTED_DB_VERSION = 1;

    private static final String CONNECT_ENCRYPTED_DB_LOCATOR = "database_connect_encrypted";

    private final Context mContext;

    public static String getDbName() { return CONNECT_ENCRYPTED_DB_LOCATOR; }

    public DatabaseConnectEncryptedOpenHelper(Context context) {
        super(context, CONNECT_ENCRYPTED_DB_LOCATOR, null, CONNECT_ENCRYPTED_DB_VERSION);
        this.mContext = context;
    }

    @Override
    public void onCreate(SQLiteDatabase database) {
        database.beginTransaction();
        try {
            TableBuilder builder = new TableBuilder(ConnectUserRecord.class);
            database.execSQL(builder.getTableCreateString());

//            builder = new TableBuilder(ConnectLinkedAppRecord.class);
//            database.execSQL(builder.getTableCreateString());
//
//            builder = new TableBuilder(ConnectJobRecord.class);
//            database.execSQL(builder.getTableCreateString());
//
//            builder = new TableBuilder(ConnectAppRecord.class);
//            database.execSQL(builder.getTableCreateString());

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

            DbUtil.createNumbersTable(database);

            database.setVersion(CONNECT_ENCRYPTED_DB_VERSION);

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
            DbUtil.trySqlCipherDbUpdate(key, mContext, CONNECT_ENCRYPTED_DB_LOCATOR);
            return super.getWritableDatabase(key);
        }
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
    }
}
