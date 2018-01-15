package org.commcare.models.database.user;

import org.commcare.CommCareApplication;
import org.commcare.models.database.SqlStorage;
import org.javarosa.core.services.ExpressionCacher;
import org.javarosa.xpath.expr.CachedExpression;
import org.javarosa.xpath.expr.InFormCacheableExpr;

/**
 * Created by amstone326 on 1/10/18.
 */

public class AndroidInFormExpressionCacher extends ExpressionCacher {

    public AndroidInFormExpressionCacher() {
    }

    @Override
    public int cache(InFormCacheableExpr expression, Object value) {
        CachedExpression cached = new CachedExpression(expression, value);
        getCacheStorage().write(cached);
        return cached.getID();
    }

    @Override
    public Object getCachedValue(int idOfStoredCache) {
        CachedExpression cached = getCacheStorage().read(idOfStoredCache);
        if (cached == null) {
            return null;
        } else {
            return cached.getEvalResult();
        }
    }

    @Override
    public boolean environmentValidForCaching() {
        return true;
    }

    @Override
    public void wipeCacheStorage() {
        getCacheStorage().removeAll();
    }

    private SqlStorage<CachedExpression> getCacheStorage() {
        return CommCareApplication.instance()
                .getUserStorage(CachedExpression.STORAGE_KEY, CachedExpression.class);
    }

}
