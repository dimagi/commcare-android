package org.commcare.models.database.global;

import android.content.Context;

import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import org.commcare.android.database.global.models.AndroidSharedKeyRecord;
import org.commcare.android.database.global.models.AppAvailableToInstall;
import org.commcare.android.database.global.models.ApplicationRecord;
import org.commcare.android.database.global.models.ConnectKeyRecord;
import org.commcare.android.javarosa.AndroidLogEntry;
import org.commcare.android.logging.ForceCloseLogEntry;
import org.commcare.models.database.DbUtil;
import org.commcare.models.database.IDatabase;
import org.commcare.models.database.UnencryptedDatabaseAdapter;
import org.commcare.modern.database.TableBuilder;

public class DatabaseGlobalOpenHelperMock extends SQLiteOpenHelper {

    private final Context mContext;

    public DatabaseGlobalOpenHelperMock(Context context) {
        super(context, null, null, 1);
        this.mContext = context;
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        IDatabase database = new UnencryptedDatabaseAdapter(db);
        database.beginTransaction();
        try {
            TableBuilder builder = new TableBuilder(ApplicationRecord.class);
            database.execSQL(builder.getTableCreateString());
            
            builder = new TableBuilder(AndroidSharedKeyRecord.class);
            database.execSQL(builder.getTableCreateString());

            builder = new TableBuilder(AndroidLogEntry.STORAGE_KEY);
            builder.addData(new AndroidLogEntry());
            database.execSQL(builder.getTableCreateString());

            builder = new TableBuilder(ForceCloseLogEntry.STORAGE_KEY);
            builder.addData(new ForceCloseLogEntry());
            database.execSQL(builder.getTableCreateString());

            builder = new TableBuilder(AppAvailableToInstall.STORAGE_KEY);
            builder.addData(new AppAvailableToInstall());
            database.execSQL(builder.getTableCreateString());

            builder = new TableBuilder(ConnectKeyRecord.STORAGE_KEY);
            builder.addData(new ConnectKeyRecord());
            database.execSQL(builder.getTableCreateString());

            DbUtil.createNumbersTable(database);

            database.setVersion(1);

            database.setTransactionSuccessful();
        } finally {
            database.endTransaction();
        }
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {

    }

}
