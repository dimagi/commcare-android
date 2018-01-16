package org.commcare.models;

import org.javarosa.core.services.ExpressionCacher;
import org.javarosa.xpath.expr.CachedExpression;
import org.javarosa.xpath.expr.InFormCacheableExpr;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by amstone326 on 1/16/18.
 */

public class AndroidInMemoryExpressionCacher extends ExpressionCacher {

    private Map<InFormCacheableExpr, Object> cache;
    private Map<InFormCacheableExpr, Integer> cacheRetrievalCounts;

    public AndroidInMemoryExpressionCacher() {
        cache = new HashMap<>();
    }

    @Override
    public int cache(InFormCacheableExpr expression, Object value) {
        cache.put(expression, value);
        return -1;
    }

    @Override
    public Object getCachedValue(InFormCacheableExpr expression) {
        cacheRetrievalCounts.put(expression, cacheRetrievalCounts.get(expression) + 1);
        return cache.get(expression);
    }

    @Override
    public boolean environmentValidForCaching() {
        return true;
    }

    @Override
    public void wipeCache() {
        cache.clear();
    }


}
