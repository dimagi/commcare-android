package org.commcare.models.database.user;

import android.content.ContentValues;

import net.sqlcipher.database.SQLiteDatabase;

import org.commcare.models.database.SqlStorage;
import org.javarosa.core.services.storage.ExpressionCacheStorage;
import org.javarosa.xpath.InFormCacheableExpr;

/**
 * Created by amstone326 on 1/10/18.
 */

public class AndroidExpressionCacheStorage implements ExpressionCacheStorage {
    private final static String formExpressionCacheTableName = "InFormExpressionCache";
    private final static String CACHE_KEY_COL = "cache_key";
    private final static String CACHE_VAL_COL = "cache_value";

    private SQLiteDatabase db;

    public AndroidExpressionCacheStorage(SQLiteDatabase userDb) {
        createExpressionCacheTable(userDb);
    }

    @Override
    public void cache(InFormCacheableExpr key, Object value) {
        ContentValues cv = new ContentValues();
        db.insertOrThrow(formExpressionCacheTableName, null, cv);
    }

    @Override
    public Object getCachedValue(InFormCacheableExpr key) {
        return null;
    }

    public static void createExpressionCacheTable(SQLiteDatabase db) {
        String createStatement =
                "CREATE TABLE IF NOT EXISTS "
                        + formExpressionCacheTableName
                        + " (" + CACHE_KEY_COL + ", " + CACHE_VAL_COL + ");";
        db.execSQL(createStatement);
    }

    public static void wipeExpressionCacheTable(SQLiteDatabase db) {
        SqlStorage.wipeTable(db, formExpressionCacheTableName);
    }
}
