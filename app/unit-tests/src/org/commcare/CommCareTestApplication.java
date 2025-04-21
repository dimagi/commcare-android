package org.commcare;

import android.content.Context;
import android.content.Intent;
import android.os.Environment;
import android.os.StrictMode;
import android.util.Log;

import org.commcare.android.database.app.models.UserKeyRecord;
import org.commcare.android.mocks.ModernHttpRequesterMock;
import org.commcare.android.util.TestUtils;
import org.commcare.core.encryption.CryptUtil;
import org.commcare.core.interfaces.HttpResponseProcessor;
import org.commcare.core.network.AuthInfo;
import org.commcare.core.network.HTTPMethod;
import org.commcare.core.network.ModernHttpRequester;
import org.commcare.dalvik.BuildConfig;
import org.commcare.heartbeat.HeartbeatRequester;
import org.commcare.heartbeat.TestHeartbeatRequester;
import org.commcare.logging.DataChangeLogger;
import org.commcare.models.AndroidPrototypeFactory;
import org.commcare.models.database.AndroidPrototypeFactorySetup;
import org.commcare.models.database.HybridFileBackedSqlStorage;
import org.commcare.models.database.HybridFileBackedSqlStorageMock;
import org.commcare.models.encryption.ByteEncrypter;
import org.commcare.network.DataPullRequester;
import org.commcare.network.LocalReferencePullResponseFactory;
import org.commcare.services.CommCareSessionService;
import org.commcare.utils.AndroidCacheDirSetup;
import org.commcare.utils.MockEncryptionKeyProvider;
import org.javarosa.core.model.User;
import org.javarosa.core.reference.ReferenceManager;
import org.javarosa.core.reference.ResourceReferenceFactory;
import org.javarosa.core.services.storage.Persistable;
import org.javarosa.core.util.externalizable.PrototypeFactory;
import org.junit.Assert;
import org.robolectric.Robolectric;
import org.robolectric.TestLifecycleApplication;
import org.robolectric.android.controller.ServiceController;

import java.io.File;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import androidx.annotation.NonNull;
import androidx.test.core.app.ApplicationProvider;
import androidx.work.Configuration;
import androidx.work.WorkManager;

import okhttp3.MultipartBody;
import okhttp3.RequestBody;

import static junit.framework.Assert.fail;
import static org.robolectric.shadows.ShadowEnvironment.setExternalStorageState;

import com.google.common.collect.Multimap;

/**
 * @author Phillip Mates (pmates@dimagi.com).
 */
public class CommCareTestApplication extends CommCareApplication implements TestLifecycleApplication {
    private static final String TAG = CommCareTestApplication.class.getSimpleName();
    private static PrototypeFactory testPrototypeFactory;
    private static final ArrayList<String> factoryClassNames = new ArrayList<>();
    private String cachedUserPassword;

    private final ArrayList<Throwable> asyncExceptions = new ArrayList<>();
    private boolean skipWorkManager = false;

    @Override
    public void onCreate() {
        // set if before calling super to initialte the dataChangeLogger correctly
        setExternalStorageState(Environment.MEDIA_MOUNTED);

        super.onCreate();

        setEncryptionKeyProvider(new MockEncryptionKeyProvider());

        // allow "jr://resource" references
        ReferenceManager.instance().addReferenceFactory(new ResourceReferenceFactory());
        Thread.setDefaultUncaughtExceptionHandler((thread, ex) -> {
            asyncExceptions.add(ex);
            Assert.fail(ex.getMessage());
        });
    }

    protected void attachISRGCert() {
        //overrule this custom loader due to issues with bootstrapping the library
    }
    @Override
    protected void turnOnStrictMode() {
        StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder()
                .detectLeakedSqlLiteObjects()
                .detectLeakedClosableObjects()
                .penaltyLog()
                .build());
    }


    public void initWorkManager() {
        Context context = ApplicationProvider.getApplicationContext();
        try {
            // first try to get instance to see if it's already initialised
            WorkManager.getInstance(context);
        } catch (IllegalStateException e) {
            Configuration config = new Configuration.Builder()
                    .setMinimumLoggingLevel(Log.DEBUG)
                    .build();
            WorkManager.initialize(context, config);
        }
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
        CommCareApp superApp = super.getCurrentApp();
        if (superApp == null) {
            return null;
        } else {
            return new CommCareTestApp(superApp);
        }
    }

    @Override
    public PrototypeFactory getPrototypeFactory(Context c) {
        TestUtils.disableSqlOptimizations();

        if (testPrototypeFactory != null) {
            return testPrototypeFactory;
        }

        // Sort of hack-y way to get the classfile dirs
        initFactoryClassList();

        try {
            testPrototypeFactory = new AndroidPrototypeFactory(new HashSet<>(factoryClassNames));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return testPrototypeFactory;
    }

    /**
     * Get externalizable classes from *.class files in build dirs. Used to
     * build PrototypeFactory that mirrors a prod environment
     */
    private static void initFactoryClassList() {
        if (factoryClassNames.isEmpty()) {
            String[] baseODK = new String[]{BuildConfig.BUILD_DIR + "/intermediates/javac/commcareDebug/compileCommcareDebugJavaWithJavac/classes/"
                        , BuildConfig.BUILD_DIR + "/intermediates/javac/commcareDebug/classes/"};
            String baseCC = BuildConfig.PROJECT_DIR + "/../../commcare-core/build/classes/java/main/";


            for(String variant : baseODK) {
                addExternalizableClassesFromDir(variant.replace("/", File.separator), factoryClassNames);
            }
            addExternalizableClassesFromDir(baseCC.replace("/", File.separator), factoryClassNames);
        }
    }

    private static void addExternalizableClassesFromDir(String baseClassPath,
                                                        List<String> externClasses) {
        try {
            File f = new File(baseClassPath);
            ArrayList<File> files = new ArrayList<>();
            getFilesInDir(f, files);
            for (File file : files) {
                String className = file.getAbsolutePath()
                        .replace(baseClassPath, "")
                        .replace(File.separator, ".")
                        .replace(".class", "")
                        .replace(".class", "");
                AndroidPrototypeFactorySetup.loadClass(className, externClasses);
            }
        } catch (Exception e) {
            Log.w(TAG, e.getMessage());
        }
    }

    @Override
    protected void loadSqliteLibs() {
        // do nothing
    }

    /**
     * @return Names of externalizable classes loaded from *.class files in the build dir
     */
    public static List<String> getTestPrototypeFactoryClasses() {
        initFactoryClassList();

        return factoryClassNames;
    }

    private static void getFilesInDir(File currentFile, ArrayList<File> acc) {
        for (File f : currentFile.listFiles()) {
            if (f.isFile()) {
                acc.add(f);
            } else {
                getFilesInDir(f, acc);
            }
        }
    }

    @Override
    public void startUserSession(byte[] symetricKey, UserKeyRecord record, boolean restoreSession) {
        // manually create/setup session service because robolectric doesn't
        // really support services
        CommCareSessionService ccService = startRoboCommCareService();
        ccService.prepareStorage(symetricKey, record);
        User user = getUserFromDb(ccService, record);
        if (user == null && cachedUserPassword != null) {
            Log.d(TAG, "No user instance found, creating one");
            user = new User(record.getUsername(), cachedUserPassword, "some_unique_id");
            CommCareApplication.instance().getRawStorage("USER", User.class, ccService.getUserDbHandle()).write(user);
        }
        if (user != null) {
            user.setCachedPwd(cachedUserPassword);
            user.setWrappedKey(ByteEncrypter.wrapByteArrayWithString(CryptUtil.generateSemiRandomKey().getEncoded(), cachedUserPassword));
        }
        ccService.startSession(user, record);
        CommCareApplication.instance().setTestingService(ccService);
    }

    private static CommCareSessionService startRoboCommCareService() {
        Intent startIntent =
                new Intent(ApplicationProvider.getApplicationContext(), CommCareSessionService.class);
        ServiceController<CommCareSessionService> serviceController =
                Robolectric.buildService(CommCareSessionService.class, startIntent);
        serviceController
                .create()
                .startCommand(0, 1);
        return serviceController.get();
    }

    private static User getUserFromDb(CommCareSessionService ccService, UserKeyRecord keyRecord) {
        for (User u : CommCareApplication.instance().getRawStorage("USER", User.class, ccService.getUserDbHandle())) {
            if (keyRecord.getUsername().equals(u.getUsername())) {
                return u;
            }
        }
        return null;
    }

    public void setCachedUserPassword(String password) {
        cachedUserPassword = password;
    }

    @Override
    public DataPullRequester getDataPullRequester() {
        return LocalReferencePullResponseFactory.INSTANCE;
    }

    @Override
    public HeartbeatRequester getHeartbeatRequester() {
        return new TestHeartbeatRequester();
    }

    @Override
    public void afterTest(Method method) {
        if (!asyncExceptions.isEmpty()) {
            for (Throwable throwable : asyncExceptions) {
                throwable.printStackTrace();
                fail("Test failed due to " + asyncExceptions.size() +
                        " threads crashing off the main thread. See error log for more details");
            }
        }
    }

    @Override
    protected void cancelWorkManagerTasks() {
        // do nothing
    }

    @Override
    public void beforeTest(Method method) {

    }

    @Override
    public void prepareTest(Object test) {

    }

    @Override
    public ModernHttpRequester buildHttpRequester(Context context, String url, Multimap<String, String> params,
                                                  HashMap headers, RequestBody requestBody, List<MultipartBody.Part> parts,
                                                  HTTPMethod method, AuthInfo authInfo, HttpResponseProcessor responseProcessor, boolean b) {
        return new ModernHttpRequesterMock(new AndroidCacheDirSetup(context),
                url,
                params,
                headers,
                requestBody,
                parts,
                null,
                method,
                responseProcessor);
    }

    @NonNull
    @Override
    public String getPhoneId() {
        return "000000000000000";
    }

    @Override
    public boolean isNsdServicesEnabled() {
        return false;
    }

    public void setSkipWorkManager() {
        skipWorkManager = true;
    }
}
