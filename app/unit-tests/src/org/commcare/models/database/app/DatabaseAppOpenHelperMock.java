package org.commcare.models.database.app;

import static org.commcare.models.database.app.AppDatabaseSchemaManager.getDbName;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import org.commcare.models.database.UnencryptedDatabaseAdapter;

public class DatabaseAppOpenHelperMock extends SQLiteOpenHelper {

    public DatabaseAppOpenHelperMock(Context context, String appId) {
        super(context, getDbName(appId), null, AppDatabaseSchemaManager.DB_VERSION_APP);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        AppDatabaseSchemaManager.initializeSchema(new UnencryptedDatabaseAdapter(db));
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
    }
}
