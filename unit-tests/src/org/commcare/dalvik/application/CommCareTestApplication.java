package org.commcare.dalvik.application;

import org.commcare.android.database.FileBackedSqlStorage;
import org.commcare.android.database.FileBackedSqlStorageMock;
import org.javarosa.core.services.storage.Persistable;

/**
 * @author Phillip Mates (pmates@dimagi.com).
 */
public class CommCareTestApplication extends CommCareApplication {
    @Override
    public <T extends Persistable> FileBackedSqlStorage<T> getFileBackedAppStorage(String name, Class<T> c) {
        return getCurrentApp().getFileBackedStorage(name, c);
    }

    @Override
    public <T extends Persistable> FileBackedSqlStorage<T> getFileBackedUserStorage(String storage, Class<T> c) {
        return new FileBackedSqlStorageMock<>(storage, c, buildUserDbHandle(), getUserDbDir());
    }

    @Override
    public CommCareApp getCurrentApp() {
        return new CommCareTestApp(super.getCurrentApp());
    }
}
