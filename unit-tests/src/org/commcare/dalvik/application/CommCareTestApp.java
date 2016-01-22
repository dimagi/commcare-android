package org.commcare.dalvik.application;

import org.commcare.android.database.UnencryptedHybridFileBackedSqlStorage;
import org.commcare.android.database.UnencryptedHybridFileBackedSqlStorageMock;
import org.javarosa.core.services.storage.Persistable;

/**
 * Delegator around CommCareApp allowing the test suite to override logic.
 *
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
    public <T extends Persistable> UnencryptedHybridFileBackedSqlStorage<T> getFileBackedStorage(String name, Class<T> c) {
        return new UnencryptedHybridFileBackedSqlStorageMock<>(name, c, app.buildAndroidDbHelper(), app);
    }
}
