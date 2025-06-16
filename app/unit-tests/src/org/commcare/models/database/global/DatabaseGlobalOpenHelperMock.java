package org.commcare.models.database.global;

import android.content.Context;

import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import org.commcare.models.database.UnencryptedDatabaseAdapter;

public class DatabaseGlobalOpenHelperMock extends SQLiteOpenHelper {

    public DatabaseGlobalOpenHelperMock(Context context) {
        super(context, null, null, GlobalDatabaseSchemaManager.GLOBAL_DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        GlobalDatabaseSchemaManager.initializeSchema(new UnencryptedDatabaseAdapter(db));
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {

    }

}
