package org.commcare.models.database.user.models;

import android.content.ContentValues;
import android.util.Log;

import net.sqlcipher.Cursor;
import net.sqlcipher.database.SQLiteDatabase;

import org.commcare.AppUtils;
import org.commcare.CommCareApplication;
import org.commcare.android.logging.ReportingUtils;
import org.commcare.models.AsyncEntity;
import org.commcare.models.database.DbUtil;
import org.commcare.modern.database.TableBuilder;
import org.commcare.models.database.SqlStorage;
import org.commcare.modern.database.DatabaseHelper;
import org.commcare.modern.database.DatabaseIndexingUtils;
import org.commcare.modern.util.Pair;
import org.commcare.suite.model.Detail;
import org.commcare.suite.model.DetailField;

import java.util.Collection;
import java.util.Hashtable;
import java.util.List;
import java.util.Vector;

/**
 * @author ctsims
 */
public class EntityStorageCache {
    private static final String TAG = EntityStorageCache.class.getSimpleName();
    public static final String TABLE_NAME = "entity_cache";

    public static final String COL_APP_ID = "app_id";
    private static final String COL_CACHE_NAME = "cache_name";
    private static final String COL_ENTITY_KEY = "entity_key";
    private static final String COL_CACHE_KEY = "cache_key";
    private static final String COL_VALUE = "value";
    private static final String COL_TIMESTAMP = "timestamp";
    private static final String ENTITY_CACHE_WIPED_PREF_SUFFIX = "enity_cache_wiped";

    private final SQLiteDatabase db;
    private final String mCacheName;
    private final String mAppId;

    public EntityStorageCache(String cacheName) {
        this(cacheName, CommCareApplication.instance().getUserDbHandle(), AppUtils.getCurrentAppId());
    }

    public EntityStorageCache(String cacheName, SQLiteDatabase db, String appId) {
        this.db = db;
        this.mCacheName = cacheName;
        this.mAppId = appId;
    }

    public static String getTableDefinition() {
        return "CREATE TABLE " + TABLE_NAME + "(" +
                DatabaseHelper.ID_COL + " INTEGER PRIMARY KEY, " +
                COL_CACHE_NAME + ", " +
                COL_APP_ID + ", " +
                COL_ENTITY_KEY + ", " +
                COL_CACHE_KEY + ", " +
                COL_VALUE + ", " +
                COL_TIMESTAMP + ", " +
                "UNIQUE (" + COL_CACHE_NAME + "," + COL_APP_ID + "," + COL_ENTITY_KEY + "," + COL_CACHE_KEY + ")" +
                ")";
    }

    public static void createIndexes(SQLiteDatabase db) {
        db.execSQL(DatabaseIndexingUtils.indexOnTableCommand("CACHE_TIMESTAMP", TABLE_NAME, COL_CACHE_NAME + ", " + COL_TIMESTAMP));
        db.execSQL(DatabaseIndexingUtils.indexOnTableCommand("NAME_ENTITY_KEY", TABLE_NAME, COL_CACHE_NAME + ", " + COL_ENTITY_KEY + ", " + COL_CACHE_KEY));
    }

    //TODO: We should do some synchronization to make it the case that nothing can hold
    //an object for the same cache at once

    public void cache(String entityKey, String cacheKey, String value) {

        ContentValues cv = new ContentValues();
        cv.put(COL_CACHE_NAME, mCacheName);
        cv.put(COL_APP_ID, mAppId);
        cv.put(COL_ENTITY_KEY, entityKey);
        cv.put(COL_CACHE_KEY, cacheKey);
        cv.put(COL_VALUE, value);
        cv.put(COL_TIMESTAMP, System.currentTimeMillis());
        db.insertWithOnConflict(TABLE_NAME, null, cv, SQLiteDatabase.CONFLICT_REPLACE);

        if (SqlStorage.STORAGE_OUTPUT_DEBUG) {
            Log.d(TAG, "Cached value|" + entityKey + "|" + cacheKey);
        }
    }

    public String retrieveCacheValue(String entityKey, String cacheKey) {
        String whereClause = String.format("%s = ? AND %s = ? AND %s = ? AND %s = ?", COL_APP_ID, COL_CACHE_NAME, COL_ENTITY_KEY, COL_CACHE_KEY);

        Cursor c = db.query(TABLE_NAME, new String[]{COL_VALUE}, whereClause, new String[]{mAppId, mCacheName, entityKey, cacheKey}, null, null, null);
        try {
            if (c.moveToNext()) {
                return c.getString(0);
            } else {
                return null;
            }
        } finally {
            c.close();
        }
    }

    /**
     * Removes cache records associated with the provided ID
     */
    public void invalidateCache(String recordId) {
        int removed = db.delete(TABLE_NAME, COL_CACHE_NAME + " = ? AND " + COL_ENTITY_KEY + " = ?", new String[]{mCacheName, recordId});
        if (SqlStorage.STORAGE_OUTPUT_DEBUG) {
            Log.d(TAG, "Invalidated " + removed + " cached values for entity " + recordId);
        }
    }

    /**
     * Removes cache records associated with the provided IDs
     */
    public void invalidateCaches(Collection<Integer> recordIds) {
        List<Pair<String, String[]>> whereParamList = TableBuilder.sqlList(recordIds);
        int removed = 0;
        for (Pair<String, String[]> querySet : whereParamList) {
            removed += db.delete(TABLE_NAME, COL_CACHE_NAME + " = '" + mCacheName + "' AND " +
                    COL_ENTITY_KEY + " IN " + querySet.first, querySet.second);
        }
        if (SqlStorage.STORAGE_OUTPUT_DEBUG) {
            Log.d(TAG, "Invalidated " + removed + " cached values for bulk entities");
        }
    }


    public static int getSortFieldIdFromCacheKey(String detailId, String cacheKey) {
        String intId = cacheKey.substring(detailId.length() + 1);
        try {
            return Integer.parseInt(intId);
        } catch (NumberFormatException nfe) {
            //TODO: Kill this cache key if this didn't work
            return -1;
        }
    }

    public static void wipeCacheForCurrentAppWithoutCommit(SQLiteDatabase userDb) {
        userDb.delete(TABLE_NAME, COL_APP_ID + " = ?", new String[]{AppUtils.getCurrentAppId()});
        setEntityCacheWipedPref();
    }

    public static void wipeCacheForCurrentApp() {
        SQLiteDatabase userDb = CommCareApplication.instance().getUserDbHandle();
        userDb.beginTransaction();
        try {
            userDb.delete(TABLE_NAME, COL_APP_ID + " = ?", new String[]{AppUtils.getCurrentAppId()});
            setEntityCacheWipedPref();
            userDb.setTransactionSuccessful();
        } finally {
            userDb.endTransaction();
        }
    }

    public static void setEntityCacheWipedPref() {
        String uuid = CommCareApplication.instance().getSession().getLoggedInUser().getUniqueId();
        int versionNumber = CommCareApplication.instance().getCurrentApp().getAppRecord().getVersionNumber();
        CommCareApplication.instance().getCurrentApp().getAppPreferences().edit()
                .putInt(uuid + "_" + ENTITY_CACHE_WIPED_PREF_SUFFIX, versionNumber).apply();
    }

    public static int getEntityCacheWipedPref(String uuid) {
        return CommCareApplication.instance().getCurrentApp().getAppPreferences()
                .getInt(uuid + "_" + ENTITY_CACHE_WIPED_PREF_SUFFIX, -1);
    }

    public static void primeCache(Hashtable<String, AsyncEntity> entitySet, String[][] cachePrimeKeys,
            Detail detail) {
        Vector<Integer> sortKeys = new Vector<>();
        String validKeys = buildValidKeys(sortKeys, detail.getFields());
        if ("".equals(validKeys)) {
            return;
        }

        //Create our full args tree. We need the elements from the cache primer
        //along with the specific keys we wanna pull out

        String[] args = new String[cachePrimeKeys[1].length + sortKeys.size()];
        System.arraycopy(cachePrimeKeys[1], 0, args, 0, cachePrimeKeys[1].length);

        for (int i = 0; i < sortKeys.size(); ++i) {
            args[cachePrimeKeys[1].length + i] = getCacheKey(detail.getId(), String.valueOf(sortKeys.get(i)));
        }

        String[] names = cachePrimeKeys[0];
        String whereClause = buildKeyNameWhereClause(names);
        long now = System.currentTimeMillis();
        String sqlStatement = "SELECT entity_key, cache_key, value FROM entity_cache JOIN AndroidCase ON entity_cache.entity_key = AndroidCase.commcare_sql_id WHERE " +
                whereClause + " AND " + EntityStorageCache.COL_APP_ID + " = '" + AppUtils.getCurrentAppId() +
                "' AND cache_key IN " + validKeys;
        SQLiteDatabase db = CommCareApplication.instance().getUserDbHandle();
        if (SqlStorage.STORAGE_OUTPUT_DEBUG) {
            DbUtil.explainSql(db, sqlStatement, args);
        }

        populateEntitySet(db, sqlStatement, args, entitySet);

        if (SqlStorage.STORAGE_OUTPUT_DEBUG) {
            Log.d(TAG, "Sequential Cache Load: " + (System.currentTimeMillis() - now) + "ms");
        }
    }

    public static String getCacheKey(String detailId, String mFieldId) {
        return detailId + "_" + mFieldId;
    }

    private static String buildValidKeys(Vector<Integer> sortKeys, DetailField[] fields) {
        String validKeys = "(";
        boolean added = false;
        for (int i = 0; i < fields.length; ++i) {
            //We're only gonna pull out the fields we can index/sort on
            if (fields[i].getSort() != null) {
                sortKeys.add(i);
                validKeys += "?, ";
                added = true;
            }
        }
        if (added) {
            return validKeys.substring(0, validKeys.length() - 2) + ")";
        } else {
            return "";
        }
    }

    private static String buildKeyNameWhereClause(String[] names) {
        String whereClause = "";
        for (int i = 0; i < names.length; ++i) {
            whereClause += TableBuilder.scrubName(names[i]) + " = ?";
            if (i + 1 < names.length) {
                whereClause += " AND ";
            }
        }
        return whereClause;
    }

    private static void populateEntitySet(SQLiteDatabase db, String sqlStatement, String[] args,
            Hashtable<String, AsyncEntity> entitySet) {
        //TODO: This will _only_ query up to about a meg of data, which is an un-great limitation.
        //Should probably split this up SQL LIMIT based looped
        //For reference the current limitation is about 10k rows with 1 field each.
        Cursor walker = db.rawQuery(sqlStatement, args);
        while (walker.moveToNext()) {
            String entityId = walker.getString(walker.getColumnIndex("entity_key"));
            String cacheId = walker.getString(walker.getColumnIndex("cache_key"));
            String val = walker.getString(walker.getColumnIndex("value"));
            if (entitySet.containsKey(entityId)) {
                entitySet.get(entityId).setSortData(cacheId, val);
            }
        }
        walker.close();
    }
}
