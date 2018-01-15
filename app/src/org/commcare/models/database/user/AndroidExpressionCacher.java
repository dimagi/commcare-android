package org.commcare.models.database.user;

import org.javarosa.core.services.storage.ExpressionCacher;
import org.javarosa.xpath.CachedExpression;
import org.javarosa.xpath.InFormCacheableExpr;

/**
 * Created by amstone326 on 1/10/18.
 */

public class AndroidExpressionCacher extends ExpressionCacher {

    public AndroidExpressionCacher() {
    }

    @Override
    public void cache(CachedExpression value) {

    }

    @Override
    public Object getCachedValue(InFormCacheableExpr key) {
        //return getCacheStorage().read(recordIdOfCachedExpression).getEvalResult();
        return null;
    }

}
