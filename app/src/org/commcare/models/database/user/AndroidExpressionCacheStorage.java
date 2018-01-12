package org.commcare.models.database.user;

import android.content.ContentValues;

import net.sqlcipher.database.SQLiteDatabase;

import org.commcare.models.database.SqlStorage;
import org.commcare.modern.database.TableBuilder;
import org.javarosa.core.services.storage.ExpressionCacheStorage;
import org.javarosa.xpath.CachedExpression;
import org.javarosa.xpath.InFormCacheableExpr;

/**
 * Created by amstone326 on 1/10/18.
 */

public class AndroidExpressionCacheStorage implements ExpressionCacheStorage {

    public AndroidExpressionCacheStorage() {
    }

    @Override
    public void cache(CachedExpression value) {

    }

    @Override
    public Object getCachedValue(InFormCacheableExpr key) {
        return null;
    }

}
