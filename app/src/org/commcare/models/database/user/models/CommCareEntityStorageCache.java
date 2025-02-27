package org.commcare.models.database.user.models;

import static org.commcare.cases.entity.EntityStorageCache.ValueType.TYPE_NORMAL_FIELD;
import static org.commcare.cases.entity.EntityStorageCache.ValueType.TYPE_SORT_FIELD;

import android.content.ContentValues;
import android.util.Log;

import net.sqlcipher.Cursor;
import net.sqlcipher.database.SQLiteDatabase;

import org.commcare.AppUtils;
import org.commcare.CommCareApplication;
import org.commcare.cases.entity.AsyncEntity;
import org.commcare.cases.entity.EntityStorageCache;
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
import java.util.Collection;
import java.util.Hashtable;
import java.util.List;
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

    private final SQLiteDatabase db;
    private final String mCacheName;
    private final String mAppId;

    public CommCareEntityStorageCache(String cacheName) {
        this(cacheName, CommCareApplication.instance().getUserDbHandle(), AppUtils.getCurrentAppId());
    }

    public CommCareEntityStorageCache(String cacheName, SQLiteDatabase db, String appId) {
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

    public Closeable lockCache() {
        //Get a db handle so we can get an outer lock
        SQLiteDatabase db = CommCareApplication.instance().getUserDbHandle();
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


    /**
     * Extracts the field ID from a composite cache key.
     *
     * <p>The cache key is expected to contain a type prefix (indicating a sort or normal field),
     * followed by the detail identifier and an underscore, and then the numeric field ID. This method
     * removes the type-specific prefixes and extracts the field ID. If the numeric conversion fails,
     * an error is logged and -1 is returned.
     *
     * @param detailId the identifier of the detail present in the cache key
     * @param cacheKey the composite cache key containing type information, the detail ID, and the field ID
     * @return the numeric field ID extracted from the cache key, or -1 if parsing fails
     */
    public int getFieldIdFromCacheKey(String detailId, String cacheKey) {
        cacheKey = cacheKey.replace(String.valueOf(TYPE_SORT_FIELD) + "_", "");
        cacheKey = cacheKey.replace(String.valueOf(TYPE_NORMAL_FIELD) + "_", "");
        String intId = cacheKey.substring(detailId.length() + 1);
        try {
            return Integer.parseInt(intId);
        } catch (NumberFormatException nfe) {
            Logger.log(LogTypes.TYPE_MAINTENANCE, "Unable to parse cache key " + cacheKey);
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

    /**
     * Populates the provided entity set with cached values from the database.
     *
     * <p>If caching is enabled for the given detail, this method builds a dynamic SQL query using valid cache keys,
     * based on the provided key arrays, to retrieve matching cache entries. If valid keys are found, the query results
     * are used to update the entity set. If no valid keys exist, the method exits early. When caching is not enabled
     * for the detail, the legacy cache priming method is invoked.</p>
     *
     * @param entitySet a mapping from entity keys to asynchronous entities that will be updated with cached data
     * @param cachePrimeKeys a two-dimensional array where the first sub-array contains field names for the SQL WHERE clause,
     *                       and the second sub-array contains corresponding primer values used in the query
     * @param detail the detail object that determines whether caching is enabled and aids in constructing valid cache keys
     */
    public void primeCache(Hashtable<String, AsyncEntity> entitySet, String[][] cachePrimeKeys,
            Detail detail) {
        if (detail.isCacheEnabled()) {
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
                            +
                            whereClause + " AND " + CommCareEntityStorageCache.COL_APP_ID + " = '"
                            + AppUtils.getCurrentAppId() +
                            "' AND cache_key IN " + validKeys;
            SQLiteDatabase db = CommCareApplication.instance().getUserDbHandle();
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
     * Primes the cache by populating the provided entity set with cached asynchronous entity data using an older caching mechanism.
     *
     * <p>This deprecated method constructs a SQL query based on a combination of sort keys derived from the detail's fields
     * and additional cache key parameters. It retrieves matching cache entries from the database by joining the entity cache
     * with the AndroidCase table, and populates the given entity set with the results. If no valid sort keys are generated,
     * no operation is performed.</p>
     *
     * @param entitySet a hashtable to be populated with cached asynchronous entities
     * @param cachePrimeKeys a two-dimensional array containing key names (at index 0) and key values (at index 1) used to filter the cache query
     * @param detail the detail instance whose fields are used to generate sort keys for constructing the query
     *
     * @deprecated Use {@link #primeCache(Hashtable, String[][], Detail)} for improved cache priming logic.
     */
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
        SQLiteDatabase db = CommCareApplication.instance().getUserDbHandle();
        if (SqlStorage.STORAGE_OUTPUT_DEBUG) {
            DbUtil.explainSql(db, sqlStatement, args);
        }

        populateEntitySet(db, sqlStatement, args, entitySet);

        if (SqlStorage.STORAGE_OUTPUT_DEBUG) {
            Log.d(TAG, "Sequential Cache Load: " + (System.currentTimeMillis() - now) + "ms");
        }
    }

    /**
     * Constructs a cache key string by concatenating the value type, detail identifier, and field identifier.
     *
     * <p>The generated key uniquely identifies a cached entry in the entity storage by combining the cache value category
     * (e.g., normal or sort field), the detail record ID, and the field ID, separated by underscores.
     *
     * @param detailId the identifier of the detail record associated with the cache entry
     * @param mFieldId the identifier of the field within the detail record
     * @param valueType the category of the cache key, distinguishing between different value types (e.g., normal or sort field)
     * @return a composite cache key in the format: valueType_detailId_mFieldId
     */
    public String getCacheKey(String detailId, String mFieldId, ValueType valueType) {
        return valueType + "_" + detailId + "_" + mFieldId;
    }

    /**
     * Constructs a SQL placeholder string for sortable fields.
     * <p>
     * Iterates through the array of fields, and for each field with a non-null sort configuration,
     * adds its index to the provided vector and appends a placeholder ("?") to the result string.
     * If at least one sortable field is found, returns a string containing the placeholders enclosed in parentheses
     * (e.g., "(?, ?, ...)"). Otherwise, returns an empty string.
     * </p>
     *
     * @param sortKeys a collection to be populated with indices of fields that can be sorted
     * @param fields the array of DetailField objects to evaluate for sortability
     * @return a SQL placeholder string for sortable fields, or an empty string if no sortable fields exist
     *
     * @deprecated Use updated key-building logic that supports both normal and sort field keys.
     */
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

    /**
     * Constructs a SQL placeholder list for cache keys based on cache-enabled fields in the provided detail.
     * <p>
     * Iterates over each field in the detail and, for fields with caching enabled, appends a SQL placeholder
     * ("?") and adds the generated cache key for the normal field type to the given list. If a field supports sorting,
     * an additional placeholder is appended and the corresponding sort field cache key is added.
     * </p>
     *
     * @param keys a vector that is populated with generated cache keys for both normal and sort fields
     * @param detail the detail instance whose fields are evaluated for caching and sorting configurations
     * @return a SQL placeholder string (e.g., "(?, ?)") matching the added cache keys, or an empty string if no valid keys were found
     */
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

    /**
     * Constructs a SQL WHERE clause for the provided column names.
     *
     * <p>This method iterates through the given array of column names, sanitizes each using
     * {@code TableBuilder.scrubName}, and builds an equality condition ("column = ?") for each.
     * When more than one column name is provided, the conditions are concatenated using " AND ".</p>
     *
     * @param names an array of column names to include in the WHERE clause
     * @return a SQL WHERE clause string with equality conditions for each column, or an empty string if no names are provided
     */
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

    /**
     * Retrieves cache entries from the database using the provided SQL query and populates the corresponding
     * entities in the given mapping with the retrieved data. For each query result, if the entity exists in the
     * entity set, the cache key is inspected to determine whether the value should be stored as normal field data
     * or sort field data.
     *
     * @param db the SQLite database used to execute the query
     * @param sqlStatement the SQL query to retrieve cache data
     * @param args the arguments for the SQL query
     * @param entitySet the mapping of entity keys to AsyncEntity instances to update with the retrieved cache values
     */
    private static void populateEntitySet(SQLiteDatabase db, String sqlStatement, String[] args,
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
}
