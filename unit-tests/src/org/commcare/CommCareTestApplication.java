package org.commcare;

import android.content.Context;

import org.commcare.android.database.app.models.UserKeyRecord;
import org.commcare.android.mocks.ModernHttpRequesterMock;
import org.commcare.models.database.HybridFileBackedSqlStorage;
import org.commcare.models.database.HybridFileBackedSqlStorageMock;
import org.commcare.network.DataPullRequester;
import org.commcare.network.ModernHttpRequester;
import org.commcare.services.CommCareSessionService;
import org.commcare.tasks.network.DebugDataPullResponseFactory;
import org.javarosa.core.model.User;
import org.javarosa.core.services.storage.Persistable;
import org.junit.Assert;

import java.net.URL;
import java.util.Hashtable;

/**
 * @author Phillip Mates (pmates@dimagi.com).
 */
public class CommCareTestApplication extends CommCareApplication {

    private String cachedUserPassword;

    @Override
    public void onCreate() {
        super.onCreate();

        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread thread, Throwable ex) {
                Assert.fail(ex.getMessage());
            }
        });
    }

    @Override
    public <T extends Persistable> HybridFileBackedSqlStorage<T> getFileBackedAppStorage(String name, Class<T> c) {
        return getCurrentApp().getFileBackedStorage(name, c);
    }

    @Override
    public <T extends Persistable> HybridFileBackedSqlStorage<T> getFileBackedUserStorage(String storage, Class<T> c) {
        return new HybridFileBackedSqlStorageMock<>(storage, c, buildUserDbHandle(), getUserKeyRecordId());
    }

    @Override
    public CommCareApp getCurrentApp() {
        return new CommCareTestApp(super.getCurrentApp());
    }

    @Override
    public void startUserSession(byte[] symetricKey, UserKeyRecord record, boolean restoreSession) {
        // manually create/setup session service because robolectric doesn't
        // really support services
        CommCareSessionService ccService = new CommCareSessionService();
        ccService.createCipherPool();
        ccService.prepareStorage(symetricKey, record);
        User user = getUserFromDb(ccService, record);
        user.setCachedPwd(cachedUserPassword);
        ccService.startSession(user, record);

        CommCareApplication._().setTestingService(ccService);
    }

    private static User getUserFromDb(CommCareSessionService ccService, UserKeyRecord keyRecord) {
        for (User u : CommCareApplication._().getRawStorage("USER", User.class, ccService.getUserDbHandle())) {
            if (keyRecord.getUsername().equals(u.getUsername())) {
                return u;
            }
        }
        throw new RuntimeException("Couldn't find '"
                + keyRecord.getUsername()
                + "' user in test database");
    }

    public void setCachedUserPassword(String password) {
        cachedUserPassword = password;
    }

    @Override
    public ModernHttpRequester buildModernHttpRequester(Context context, URL url,
                                                        Hashtable<String, String> params,
                                                        boolean isAuthenticatedRequest,
                                                        boolean isPostRequest) {
        return new ModernHttpRequesterMock(context, url, params, isAuthenticatedRequest, isPostRequest);
    }

    @Override
    public DataPullRequester getDataPullRequester(){
        return DebugDataPullResponseFactory.INSTANCE;
    }
}
