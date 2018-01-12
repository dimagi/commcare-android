package org.commcare.models.database;

import org.commcare.models.database.user.AndroidExpressionCacheStorage;
import org.commcare.modern.database.StorageProvider;
import org.javarosa.core.services.storage.ExpressionCacheStorage;

/**
 * Created by amstone326 on 1/12/18.
 */

public class AndroidStorageProvider extends StorageProvider {

    @Override
    public ExpressionCacheStorage getExpressionCacheStorage() {
        return new AndroidExpressionCacheStorage();
    }
}
