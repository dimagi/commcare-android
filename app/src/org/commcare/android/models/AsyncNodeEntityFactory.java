package org.commcare.android.models;

import android.util.Log;

import net.sqlcipher.Cursor;
import net.sqlcipher.database.SQLiteDatabase;

import org.commcare.android.database.DbUtil;
import org.commcare.android.database.SqlStorage;
import org.commcare.android.database.AndroidTableBuilder;
import org.commcare.android.database.UserStorageClosedException;
import org.commcare.android.database.user.models.EntityStorageCache;
import org.javarosa.core.model.User;
import org.commcare.android.util.SessionUnavailableException;
import org.commcare.dalvik.application.CommCareApplication;
import org.commcare.suite.model.Detail;
import org.commcare.suite.model.DetailField;
import org.javarosa.core.model.condition.EvaluationContext;
import org.javarosa.core.model.instance.TreeReference;
import org.javarosa.core.model.utils.CacheHost;
import org.javarosa.core.util.OrderedHashtable;
import org.javarosa.xpath.expr.XPathExpression;

import java.util.Hashtable;
import java.util.List;
import java.util.Vector;

/**
 * @author ctsims
 */
public class AsyncNodeEntityFactory extends NodeEntityFactory {
    private static final String TAG = AsyncNodeEntityFactory.class.getSimpleName();

    User current; 
    
    OrderedHashtable<String, XPathExpression> mVariableDeclarations;
    
    Hashtable<String, AsyncEntity> mEntitySet = new Hashtable<String, AsyncEntity>();
    EntityStorageCache mEntityCache;
    
    private CacheHost mCacheHost = null;
    
    private Boolean mTemplateIsCachable = null;
    
    Object mAsyncLock = new Object();
    Thread mAsyncPrimingThread;
    
    public AsyncNodeEntityFactory(Detail d, EvaluationContext ec) {
        super(d, ec);
        
        mVariableDeclarations = getDetail().getVariableDeclarations();
        mEntityCache = new EntityStorageCache("case");
    }

    @Override
    public Entity<TreeReference> getEntity(TreeReference data) {
        EvaluationContext nodeContext = new EvaluationContext(ec, data);
        
        mCacheHost = nodeContext.getCacheHost(data);
        
        String mCacheIndex = null;
        if(mTemplateIsCachable == null) {
            if(mCacheHost == null) { mTemplateIsCachable = false; }
            else {
                mTemplateIsCachable = mCacheHost.isReferencePatternCachable(data);
            }
        } 
        if(mTemplateIsCachable) {
            if(mCacheHost == null) { Log.d(TAG, "Template is cachable, but there's no cache host for this instance?"); }
            else {
                mCacheIndex = mCacheHost.getCacheIndex(data);
            }
        }

        AsyncEntity entity = new AsyncEntity(detail.getFields(), nodeContext, data, mVariableDeclarations, mEntityCache, mCacheIndex, detail.getId());
        
        if(mCacheIndex != null) {
            mEntitySet.put(mCacheIndex , entity);
        }
        return entity;
    }
    
    private void primeCache() {
        if(mTemplateIsCachable == null || !mTemplateIsCachable || mCacheHost == null ) { return; }
        
        String[][] cachePrimeKeys = mCacheHost.getCachePrimeGuess();
        if(cachePrimeKeys == null) { return; }
        
        //Figure out sort keys
        Vector<Integer> sortKeys = new Vector<Integer>();
        DetailField[] fields = getDetail().getFields();
        
        String validKeys = "(";
        boolean added = false;
        for(int i =0 ; i < fields.length ; ++i) {
            //We're only gonna pull out the fields we can index/sort on
            if(fields[i].getSort() != null) {
                sortKeys.add(i);
                validKeys += "?, ";
                added = true;
            }
        }
        
        //If we didn't actually find any keys to cache, get outta here
        if(!added) { return; }
        validKeys = validKeys.substring(0, validKeys.length() - 2) + ")";

        
        //Create our full args tree. We need the elements from the cache primer
        //along with the specific keys we wanna pull out
        
        String[] args = new String[cachePrimeKeys[1].length + sortKeys.size()];
        System.arraycopy(cachePrimeKeys[1], 0, args, 0, cachePrimeKeys[1].length);
        
        for(int i = 0 ; i < sortKeys.size() ; ++i) {
            args[2 + i] = EntityStorageCache.getCacheKey(getDetail().getId(), String.valueOf(sortKeys.get(i)));
        }
        
        String[] names = cachePrimeKeys[0];
        
        //Build the where clause for the provided key names
        String whereClause = "";
        for(int i = 0 ; i < names.length; ++ i) {
            whereClause += AndroidTableBuilder.scrubName(names[i]) + " = ?";
            if(i + 1 < names.length) {
                whereClause += " AND ";
            }
        }
        
        long now = System.currentTimeMillis();

        SQLiteDatabase db;
        try {
            db = CommCareApplication._().getUserDbHandle();
        } catch (SessionUnavailableException e) {
            // TODO PLM: not sure how to fail elegantly here, so mimicking
            // current behaviour by raising a runtime error.
            throw new UserStorageClosedException(e.getMessage());
        }
        
        String sqlStatement = "SELECT entity_key, cache_key, value FROM entity_cache JOIN AndroidCase ON entity_cache.entity_key = AndroidCase.commcare_sql_id WHERE " + whereClause + " AND cache_key IN " + validKeys;
        if(SqlStorage.STORAGE_OUTPUT_DEBUG) {
            DbUtil.explainSql(db, sqlStatement, args);
        }
       
        //TODO: This will _only_ query up to about a meg of data, which is an un-great limitation. 
        //Should probably split this up SQL LIMIT based looped
        //For reference the current limitation is about 10k rows with 1 field each. 
        Cursor walker = db.rawQuery(sqlStatement, args);
        while(walker.moveToNext()) {
            String entityId = walker.getString(walker.getColumnIndex("entity_key"));
            String cacheId = walker.getString(walker.getColumnIndex("cache_key"));
            String val = walker.getString(walker.getColumnIndex("value"));
            AsyncEntity entity = this.mEntitySet.get(entityId);
            entity.setSortData(cacheId, val);
        }
        walker.close();
        
        if(SqlStorage.STORAGE_OUTPUT_DEBUG) {
            Log.d(TAG, "Sequential Cache Load: " + (System.currentTimeMillis() - now) + "ms");
        }
    }
    
    @Override
    public List<TreeReference> expandReferenceList(TreeReference parentRef) {
        List<TreeReference> references = super.expandReferenceList(parentRef);
        
        return references;
    }
    
    @Override
    public void prepareEntitiesInternal() {
        synchronized(mAsyncLock) {
            if(mAsyncPrimingThread == null) {
                mAsyncPrimingThread = new Thread(new Runnable() {

                    @Override
                    public void run() {
                        primeCache();
                    }
                    
                });
                mAsyncPrimingThread.start();
            }
        }
    }
    
    @Override
    public boolean isEntitySetReadyInternal() {
        synchronized(mAsyncLock) {
            if(mAsyncPrimingThread == null) {
                return true;
            } else {
                return !mAsyncPrimingThread.isAlive();
            }
        }
    }
}
