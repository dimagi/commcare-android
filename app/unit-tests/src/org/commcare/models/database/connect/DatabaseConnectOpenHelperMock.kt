package org.commcare.models.database.connect

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.commcare.models.database.UnencryptedDatabaseAdapter

class DatabaseConnectOpenHelperMock(
    context: Context,
) : SQLiteOpenHelper(context, null, null, ConnectDatabaseSchemaManager.DB_VERSION_CONNECT) {
    override fun onCreate(db: SQLiteDatabase) {
        ConnectDatabaseSchemaManager.initializeSchema(UnencryptedDatabaseAdapter(db))
    }

    override fun onUpgrade(
        db: SQLiteDatabase,
        oldVersion: Int,
        newVersion: Int,
    ): Unit = throw UnsupportedOperationException("DatabaseConnectOpenHelperMock does not support onUpgrade")
}
