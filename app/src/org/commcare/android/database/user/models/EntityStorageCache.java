/**
 * 
 */
package org.commcare.android.database.user.models;

import net.sqlcipher.Cursor;
import net.sqlcipher.database.SQLiteDatabase;

import org.commcare.android.database.DbUtil;
import org.commcare.android.database.SqlStorage;
import org.commcare.android.database.UserStorageClosedException;
import org.commcare.android.util.SessionUnavailableException;
import org.commcare.dalvik.application.CommCareApplication;
import org.javarosa.core.services.Logger;

import android.content.ContentValues;

/**
 * @author ctsims
 *
 */
public class EntityStorageCache {
    public static final String TABLE_NAME = "entity_cache";
    
    public static final String COL_CACHE_NAME = "cache_name";
    public static final String COL_ENTITY_KEY = "entity_key";
    public static final String COL_CACHE_KEY = "cache_key";
    public static final String COL_VALUE = "value";
    public static final String COL_TIMESTAMP= "timestamp";
    
    
    public static String getTableDefinition() {
        String tableCreate = "CREATE TABLE " + TABLE_NAME + "(" +
                DbUtil.ID_COL + " INTEGER PRIMARY KEY, " + 
                COL_CACHE_NAME + ", " +
                COL_ENTITY_KEY + ", " +
                COL_CACHE_KEY + ", " +
                COL_VALUE + ", " + 
                COL_TIMESTAMP + 
                ")";
        return tableCreate;
    }
    
    public static void createIndexes(SQLiteDatabase db ) {
        //To query what 
        db.execSQL("CREATE INDEX CACHE_TIMESTAMP ON " + TABLE_NAME + " (" + COL_CACHE_NAME + ", " + COL_TIMESTAMP  + " )");
        db.execSQL("CREATE INDEX NAME_ENTITY_KEY ON " + TABLE_NAME + " (" + COL_CACHE_NAME + ", "+ COL_ENTITY_KEY + ", " + COL_CACHE_KEY  + " )");
    }
    
    //TODO: We should do some synchronization to make it the case that nothing can hold
    //an object for the same cache at once
    
    public EntityStorageCache(String cacheName) {
        // TODO PLM: refactor so that error handling occurs by caller and this
        // method can call 'this'.
        try {
            this.db = CommCareApplication._().getUserDbHandle();
        } catch (SessionUnavailableException e) {
            throw new UserStorageClosedException(e.getMessage());
        }
        this.mCacheName = cacheName;
    }
    
    SQLiteDatabase db;
    String mCacheName;
    public EntityStorageCache(String cacheName, SQLiteDatabase db) {
        this.db = db;
        this.mCacheName = cacheName;
    }
    
    public void cache(String entityKey, String cacheKey, String value) {
        long timestamp = System.currentTimeMillis();
        //TODO: this should probably just be an ON CONFLICT REPLACE call
        int removed = db.delete(TABLE_NAME, COL_CACHE_NAME + " = ? AND " + COL_ENTITY_KEY + " = ? AND " + COL_CACHE_KEY + " =?", new String[] {this.mCacheName, entityKey, cacheKey});
        if(SqlStorage.STORAGE_OUTPUT_DEBUG) {
            System.out.println("Deleted " + removed + " cached values for existing cache value on entity " + entityKey +" on insert");
        }
        //We need to clear this cache value if it exists first.
        ContentValues cv = new ContentValues();
        cv.put(COL_CACHE_NAME, mCacheName);
        cv.put(COL_ENTITY_KEY, entityKey);
        cv.put(COL_CACHE_KEY, cacheKey);
        cv.put(COL_VALUE, value);
        cv.put(COL_TIMESTAMP, timestamp);
        db.insert(TABLE_NAME, null, cv);
        
        if(SqlStorage.STORAGE_OUTPUT_DEBUG) {
            System.out.println("Cached value|" + entityKey + "|" + cacheKey);
        }
    }
    
    public String retrieveCacheValue(String entityKey, String cacheKey) {
        String whereClause = String.format("%s = ? AND %s = ? AND %s = ?", COL_CACHE_NAME, COL_ENTITY_KEY, COL_CACHE_KEY);
        
        Cursor c = db.query(TABLE_NAME, new String[] {COL_VALUE}, whereClause, new String[] {mCacheName, entityKey, cacheKey},null, null, null);
        try{
            if(c.moveToNext()) {
                return c.getString(0);
            } else {
                return null;
            }
        }finally {
            c.close();
        }
    }
    
    /**
     * Removes cache records associated with the provided ID
     * 
     * @param recordId
     */
    public void invalidateCache(String recordId) {
        int removed = db.delete(TABLE_NAME, COL_CACHE_NAME + " = ? AND " + COL_ENTITY_KEY + " = ?", new String[] {this.mCacheName, recordId});
        if(SqlStorage.STORAGE_OUTPUT_DEBUG) {
            System.out.println("Invalidated " + removed + " cached values for entity " + recordId);
        }
    }
    
    public void clearCache() {
        long now = System.currentTimeMillis();
        db.beginTransaction();
        try {
            int removed = db.delete(TABLE_NAME, COL_CACHE_NAME + " = ?" , new String[] {this.mCacheName});
            db.setTransactionSuccessful();  
            Logger.log("cache", "Cleared " + removed + " records from cache: " + COL_CACHE_NAME + " - in " + (System.currentTimeMillis() - now) + "ms");
        } finally {
            db.endTransaction();
        }
        

    }
    
    
    /**
     * TODO: This is the wrong place for this, I think? Hard to say where it should go...
     * @param d
     * @param sortFieldId
     * @return
     */
    public static String getCacheKey(String detailId, String mFieldId) {
        return detailId + "_" + mFieldId;
    }
    
    public static int getSortFieldIdFromCacheKey(String detailId, String cacheKey) {
        String intId = cacheKey.substring(detailId.length() + 1);
        try {
            return Integer.parseInt(intId);
        } catch(NumberFormatException nfe) {
            //TODO: Kill this cache key if this didn't work
            return -1;
        }
    }
    
}
