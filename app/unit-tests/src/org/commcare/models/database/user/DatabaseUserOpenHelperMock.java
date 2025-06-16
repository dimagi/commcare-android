package org.commcare.models.database.user;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import org.commcare.models.database.UnencryptedDatabaseAdapter;

import static org.commcare.models.database.user.UserDatabaseSchemaManager.USER_DB_VERSION;
import static org.commcare.models.database.user.UserDatabaseSchemaManager.getDbName;

public class DatabaseUserOpenHelperMock extends SQLiteOpenHelper {

    public DatabaseUserOpenHelperMock(Context context, String userKeyRecordId) {
        super(context, getDbName(userKeyRecordId), null, USER_DB_VERSION, null);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        UserDatabaseSchemaManager.initializeSchema(new UnencryptedDatabaseAdapter(db));
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {

    }
}
