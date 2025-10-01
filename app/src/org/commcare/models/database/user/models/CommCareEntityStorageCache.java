package org.commcare.models.database.user.models;

import static org.commcare.cases.entity.EntityStorageCache.ValueType.TYPE_NORMAL_FIELD;
import static org.commcare.cases.entity.EntityStorageCache.ValueType.TYPE_SORT_FIELD;

import android.content.ContentValues;
import android.database.Cursor;
import android.util.Log;

import com.google.common.collect.ImmutableList;

import org.commcare.AppUtils;
import org.commcare.CommCareApplication;
import org.commcare.cases.entity.AsyncEntity;
import org.commcare.cases.entity.EntityStorageCache;
import org.commcare.engine.cases.CaseUtils;
import org.commcare.models.database.IDatabase;
import org.commcare.models.database.DbUtil;
import org.commcare.models.database.SqlStorage;
import org.commcare.modern.database.DatabaseHelper;
import org.commcare.modern.database.DatabaseIndexingUtils;
import org.commcare.modern.database.TableBuilder;
import org.commcare.modern.util.Pair;
import org.commcare.suite.model.Detail;
import org.commcare.suite.model.DetailField;
import org.commcare.util.LogTypes;
import org.javarosa.core.services.Logger;

import java.io.Closeable;
import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.Set;
import java.util.Vector;

/**
 * @author ctsims
 */
public class CommCareEntityStorageCache implements EntityStorageCache {
    private static final String TAG = CommCareEntityStorageCache.class.getSimpleName();
    public static final String TABLE_NAME = "entity_cache";

    public static final String COL_APP_ID = "app_id";
    private static final String COL_CACHE_NAME = "cache_name";
    private static final String COL_ENTITY_KEY = "entity_key";
    private static final String COL_CACHE_KEY = "cache_key";
    private static final String COL_VALUE = "value";
    private static final String COL_TIMESTAMP = "timestamp";
    private static final String ENTITY_CACHE_WIPED_PREF_SUFFIX = "enity_cache_wiped";

    // flag to record entity ids that has changed since last time around and
    // for which the related entity graph needs to be deleted from cache
    private static final String COL_IS_SHALLOW = "is_shallow";

    private final IDatabase db;
    private final String mCacheName;
    private final String mAppId;

    public CommCareEntityStorageCache(String cacheName) {
        this(cacheName, CommCareApplication.instance().getUserDbHandle(), AppUtils.getCurrentAppId());
    }

    public CommCareEntityStorageCache(String cacheName, IDatabase db, String appId) {
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
                COL_IS_SHALLOW + " INTEGER NOT NULL DEFAULT 0, " +
                "UNIQUE (" + COL_CACHE_NAME + "," + COL_APP_ID + "," + COL_ENTITY_KEY + "," + COL_CACHE_KEY + ")" +
                ")";
    }

    public static void createIndexes(IDatabase db) {
        db.execSQL(DatabaseIndexingUtils.indexOnTableCommand("CACHE_TIMESTAMP", TABLE_NAME, COL_CACHE_NAME + ", " + COL_TIMESTAMP));
        db.execSQL(DatabaseIndexingUtils.indexOnTableCommand("NAME_ENTITY_KEY", TABLE_NAME, COL_CACHE_NAME + ", " + COL_ENTITY_KEY + ", " + COL_CACHE_KEY));
    }

    public Closeable lockCache() {
        //Get a db handle so we can get an outer lock
        IDatabase db = CommCareApplication.instance().getUserDbHandle();
        //get the db lock
        db.beginTransaction();
        return () -> {
            db.setTransactionSuccessful();
            db.endTransaction();
        };
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
        db.insertWithOnConflict(TABLE_NAME, null, cv, db.getConflictReplace());

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
     * Inserts a shallow record for the recordId to mark the record needing invalidation
     */
    public void invalidateRecord(String recordId) {
        if (isEmpty()) {
            return;
        }
        markRecordsAsShallow(ImmutableList.of(Integer.parseInt(recordId)));
    }

    /**
     * Inserts shallow records for the recordIds to signify the records needing invalidation
     */
    public void invalidateRecords(Collection<Integer> recordIds) {
        if (isEmpty()) {
            return;
        }
        db.beginTransaction();
        try {
            markRecordsAsShallow(recordIds);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    /**
     * Removes cache records associated with the provided IDs
     */
    private void deleteRecords(Vector<Integer> recordIds) {
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

    private Set<String> getShallowRecords() {
        String whereClause = String.format("%s = ? AND %s = ?", COL_CACHE_NAME, COL_IS_SHALLOW);
        Cursor c = db.query(TABLE_NAME, new String[]{COL_ENTITY_KEY}, whereClause,
                new String[]{mCacheName, "1"}, null, null, null);
        Set<String> resultSet = new HashSet<>();
        try {
            while (c.moveToNext()) {
                resultSet.add(c.getString(0));
            }
        } finally {
            c.close();
        }
        return resultSet;
    }


    public int getFieldIdFromCacheKey(String detailId, String cacheKey) {
        cacheKey = cacheKey.replace(TYPE_SORT_FIELD + "_", "");
        cacheKey = cacheKey.replace(TYPE_NORMAL_FIELD + "_", "");
        String intId = cacheKey.substring(detailId.length() + 1);
        try {
            return Integer.parseInt(intId);
        } catch (NumberFormatException nfe) {
            Logger.log(LogTypes.TYPE_MAINTENANCE, "Unable to parse cache key " + cacheKey);
            //TODO: Kill this cache key if this didn't work
            return -1;
        }
    }

    public static void wipeCacheForCurrentAppWithoutCommit(IDatabase userDb) {
        userDb.delete(TABLE_NAME, COL_APP_ID + " = ?", new String[]{AppUtils.getCurrentAppId()});
        setEntityCacheWipedPref();
    }

    public static void wipeCacheForCurrentApp() {
        IDatabase userDb = CommCareApplication.instance().getUserDbHandle();
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

    public void primeCache(Hashtable<String, AsyncEntity> entitySet, String[][] cachePrimeKeys,
            Detail detail) {
        if (detail.isCacheEnabled()) {
            // first make sure to process shallow records in case they are present
            processShallowRecords();

            Vector<String> cacheKeys = new Vector<>();
            String validKeys = buildValidKeys(cacheKeys, detail);
            if (validKeys.isEmpty()) {
                return;
            }

            //Create our full args tree. We need the elements from the cache primer
            //along with the specific keys we wanna pull out
            String[] args = new String[cachePrimeKeys[1].length + cacheKeys.size()];
            System.arraycopy(cachePrimeKeys[1], 0, args, 0, cachePrimeKeys[1].length);

            for (int i = 0; i < cacheKeys.size(); ++i) {
                args[cachePrimeKeys[1].length + i] = cacheKeys.get(i);
            }

            String[] names = cachePrimeKeys[0];
            String whereClause = buildKeyNameWhereClause(names);
            long now = System.currentTimeMillis();
            String sqlStatement =
                    "SELECT entity_key, cache_key, value FROM entity_cache JOIN AndroidCase ON entity_cache"
                            + ".entity_key = AndroidCase.commcare_sql_id WHERE "
                            + whereClause + " AND " + CommCareEntityStorageCache.COL_APP_ID + " = '"
                            + AppUtils.getCurrentAppId() +
                            "' AND cache_key IN " + validKeys;
            IDatabase db = CommCareApplication.instance().getUserDbHandle();
            if (SqlStorage.STORAGE_OUTPUT_DEBUG) {
                DbUtil.explainSql(db, sqlStatement, args);
            }
            populateEntitySet(db, sqlStatement, args, entitySet);
            if (SqlStorage.STORAGE_OUTPUT_DEBUG) {
                Log.d(TAG, "Sequential Cache Load: " + (System.currentTimeMillis() - now) + "ms");
            }
        } else {
            primeCacheOld(entitySet, cachePrimeKeys, detail);
        }
    }

    /**
     * Gets all shallow records and it's related graph and delete those records from the cache
     */
    public void processShallowRecords() {
        try (Closeable ignored = lockCache()) {
            Set<String> shallowRecordIds = getShallowRecords();
            Vector<Integer> relatedRecordIds = CaseUtils.getRelatedCases(shallowRecordIds);
            deleteRecords(relatedRecordIds);
        } catch (IOException e) {
            Logger.exception("Error while processing shallow records", e);
            throw new RuntimeException(e);
        }
    }

    @Deprecated
    private void primeCacheOld(Hashtable<String, AsyncEntity> entitySet, String[][] cachePrimeKeys,
            Detail detail) {
        Vector<Integer> sortKeys = new Vector<>();
        String validKeys = buildValidSortKeys(sortKeys, detail.getFields());
        if ("".equals(validKeys)) {
            return;
        }

        //Create our full args tree. We need the elements from the cache primer
        //along with the specific keys we wanna pull out

        String[] args = new String[cachePrimeKeys[1].length + sortKeys.size()];
        System.arraycopy(cachePrimeKeys[1], 0, args, 0, cachePrimeKeys[1].length);

        for (int i = 0; i < sortKeys.size(); ++i) {
            args[cachePrimeKeys[1].length + i] = detail.getId() + "_" + sortKeys.get(i);
        }

        String[] names = cachePrimeKeys[0];
        String whereClause = buildKeyNameWhereClause(names);
        long now = System.currentTimeMillis();
        String sqlStatement =
                "SELECT entity_key, cache_key, value FROM entity_cache JOIN AndroidCase ON entity_cache"
                        + ".entity_key = AndroidCase.commcare_sql_id WHERE "
                        +
                        whereClause + " AND " + CommCareEntityStorageCache.COL_APP_ID + " = '"
                        + AppUtils.getCurrentAppId() +
                        "' AND cache_key IN " + validKeys;
        IDatabase db = CommCareApplication.instance().getUserDbHandle();
        if (SqlStorage.STORAGE_OUTPUT_DEBUG) {
            DbUtil.explainSql(db, sqlStatement, args);
        }

        populateEntitySet(db, sqlStatement, args, entitySet);

        if (SqlStorage.STORAGE_OUTPUT_DEBUG) {
            Log.d(TAG, "Sequential Cache Load: " + (System.currentTimeMillis() - now) + "ms");
        }
    }

    public String getCacheKey(String detailId, String mFieldId, ValueType valueType) {
        return valueType + "_" + detailId + "_" + mFieldId;
    }

    @Deprecated
    private static String buildValidSortKeys(Vector<Integer> sortKeys, DetailField[] fields) {
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

    private String buildValidKeys(Vector<String> keys, Detail detail) {
        StringBuilder validKeys = new StringBuilder("(");
        DetailField[] fields = detail.getFields();
        for (int i = 0; i < fields.length; ++i) {
            if (fields[i].isCacheEnabled()) {
                keys.add(getCacheKey(detail.getId(), String.valueOf(i),
                        TYPE_NORMAL_FIELD));
                validKeys.append("?, ");
                if (fields[i].getSort() != null) {
                    keys.add(getCacheKey(detail.getId(), String.valueOf(i),
                            ValueType.TYPE_SORT_FIELD));
                    validKeys.append("?, ");
                }
            }
        }
        if (!keys.isEmpty()) {
            return validKeys.substring(0, validKeys.length() - 2) + ")";
        }
        return "";
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

    private static void populateEntitySet(IDatabase db, String sqlStatement, String[] args,
                                          Hashtable<String, AsyncEntity> entitySet) {
        Cursor walker = db.rawQuery(sqlStatement, args);
        while (walker.moveToNext()) {
            String entityKey = walker.getString(walker.getColumnIndex("entity_key"));
            if (entitySet.containsKey(entityKey)) {
                String cacheKey = walker.getString(walker.getColumnIndex("cache_key"));
                String value = walker.getString(walker.getColumnIndex("value"));
                if (cacheKey.startsWith(TYPE_NORMAL_FIELD.toString())) {
                    entitySet.get(entityKey).setFieldData(cacheKey, value);
                } else {
                    entitySet.get(entityKey).setSortData(cacheKey, value);
                }
            }
        }
        walker.close();
    }

    private void markRecordsAsShallow(Collection<Integer> recordIds) {
        for (Integer recordId : recordIds) {
            ContentValues cv = new ContentValues();
            cv.put(COL_CACHE_NAME, mCacheName);
            cv.put(COL_APP_ID, mAppId);
            cv.put(COL_ENTITY_KEY, recordId);
            cv.put(COL_IS_SHALLOW, 1);
            cv.put(COL_TIMESTAMP, System.currentTimeMillis());
            db.insertWithOnConflict(TABLE_NAME, null, cv, db.getConflictReplace());
        }
    }

    /**
     * Checks whether the cache is empty
     * @return if the cache is empty
     */
    public boolean isEmpty() {
        return getCount() == 0;
    }

    private int getCount() {
        String sql = "SELECT COUNT(*) FROM " + TABLE_NAME + " WHERE " + COL_CACHE_NAME + " = ?";
        Cursor cursor = db.rawQuery(sql, new String[]{mCacheName});
        if (cursor != null) {
            try (cursor) {
                if (cursor.moveToFirst()) {
                    return cursor.getInt(0);
                }
            }
        }
        return 0;
    }
}
