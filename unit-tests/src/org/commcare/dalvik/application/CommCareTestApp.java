package org.commcare.dalvik.application;

import org.commcare.android.database.UnencryptedFileBackedSqlStorage;
import org.commcare.android.database.UnencryptedFileBackedSqlStorageMock;
import org.javarosa.core.services.storage.Persistable;

/**
 * @author Phillip Mates (pmates@dimagi.com).
 */
public class CommCareTestApp extends CommCareApp {
    private final CommCareApp app;

    public CommCareTestApp(CommCareApp app) {
        super(app.getAppRecord());
        fileRoot = app.fileRoot;
        setAppResourceState(app.getAppResourceState());
        this.app = app;
    }

    @Override
    public <T extends Persistable> UnencryptedFileBackedSqlStorage<T> getFileBackedStorage(String name, Class<T> c) {
        return new UnencryptedFileBackedSqlStorageMock<>(name, c, app.buildAndroidDbHelper(), storageRoot());
    }
}
