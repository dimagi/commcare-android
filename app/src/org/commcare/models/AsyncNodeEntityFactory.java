package org.commcare.models;

import android.util.Log;

import org.commcare.cases.entity.Entity;
import org.commcare.cases.entity.NodeEntityFactory;
import org.commcare.models.database.user.models.EntityStorageCache;
import org.commcare.suite.model.Detail;
import org.javarosa.core.model.condition.EvaluationContext;
import org.javarosa.core.model.instance.TreeReference;
import org.javarosa.core.model.utils.CacheHost;
import org.javarosa.core.util.OrderedHashtable;
import org.javarosa.xpath.expr.XPathExpression;

import java.util.Hashtable;
import java.util.List;

/**
 * @author ctsims
 */
public class AsyncNodeEntityFactory extends NodeEntityFactory {
    private static final String TAG = AsyncNodeEntityFactory.class.getSimpleName();
    private final OrderedHashtable<String, XPathExpression> mVariableDeclarations;

    private final Hashtable<String, AsyncEntity> mEntitySet = new Hashtable<>();
    private final EntityStorageCache mEntityCache;

    private CacheHost mCacheHost = null;
    private Boolean mTemplateIsCachable = null;
    private static final Object mAsyncLock = new Object();
    private Thread mAsyncPrimingThread;

    // Don't show entity list until we primeCache and caches all fields
    private final boolean isBlockingAsyncMode;

    public AsyncNodeEntityFactory(Detail d, EvaluationContext ec) {
        super(d, ec);

        mVariableDeclarations = detail.getVariableDeclarations();
        mEntityCache = new EntityStorageCache("case");
        isBlockingAsyncMode = detail.hasSortField();
    }

    @Override
    public Entity<TreeReference> getEntity(TreeReference data) {
        EvaluationContext nodeContext = new EvaluationContext(ec, data);

        mCacheHost = nodeContext.getCacheHost(data);

        String mCacheIndex = null;
        if (mTemplateIsCachable == null) {
            mTemplateIsCachable = mCacheHost != null && mCacheHost.isReferencePatternCachable(data);
        }
        if (mTemplateIsCachable) {
            if (mCacheHost == null) {
                Log.d(TAG, "Template is cachable, but there's no cache host for this instance?");
            } else {
                mCacheIndex = mCacheHost.getCacheIndex(data);
            }
        }

        String entityKey = loadCalloutDataMapKey(nodeContext);
        AsyncEntity entity =
                new AsyncEntity(detail.getFields(), nodeContext, data, mVariableDeclarations,
                        mEntityCache, mCacheIndex, detail.getId(), entityKey);

        if (mCacheIndex != null) {
            mEntitySet.put(mCacheIndex, entity);
        }
        return entity;
    }

    @Override
    protected void setEvaluationContextDefaultQuerySet(EvaluationContext ec,
                                                       List<TreeReference> result) {

        //Don't do anything for asynchronous lists. In theory the query set could help expand the
        //first cache more quickly, but otherwise it's just keeping around tons of cases in memory
        //that don't even need to be loaded.
    }


    /**
     * Bulk loads search field cache from db.
     * Note that the cache is lazily built upon first case list search.
     */
    private void primeCache() {
        if (mTemplateIsCachable == null || !mTemplateIsCachable || mCacheHost == null) {
            return;
        }

        String[][] cachePrimeKeys = mCacheHost.getCachePrimeGuess();
        if (cachePrimeKeys == null) {
            return;
        }
        EntityStorageCache.primeCache(mEntitySet,cachePrimeKeys, detail);
    }

    @Override
    protected void prepareEntitiesInternal(List<Entity<TreeReference>> entities) {
        // if blocking mode load cache on the same thread and set any data thats not cached
        if (isBlockingAsyncMode) {
            primeCache();
            setUnCachedData(entities);
        } else {
            // otherwise we want to show the entity list asap and hence want to offload the loading cache part to a separate
            // thread while caching any uncached data later on UI thread during Adapter's getView
            synchronized (mAsyncLock) {
                if (mAsyncPrimingThread == null) {
                    mAsyncPrimingThread = new Thread(this::primeCache);
                    mAsyncPrimingThread.start();
                }
            }
        }
    }

    private void setUnCachedData(List<Entity<TreeReference>> entities) {
        for (int i = 0; i < entities.size(); i++) {
            AsyncEntity e = (AsyncEntity)entities.get(i);
            for (int col = 0; col < e.getNumFields(); ++col) {
                e.getSortField(col);
            }
        }
    }

    @Override
    protected boolean isEntitySetReadyInternal() {
        synchronized (mAsyncLock) {
            return mAsyncPrimingThread == null || !mAsyncPrimingThread.isAlive();
        }
    }

    public boolean isBlockingAsyncMode() {
        return isBlockingAsyncMode;
    }
}
