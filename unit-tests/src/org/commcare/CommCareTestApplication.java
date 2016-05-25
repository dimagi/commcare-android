package org.commcare;

import android.content.Context;
import android.util.Log;

import org.commcare.android.util.TestUtils;
import org.commcare.dalvik.BuildConfig;
import org.commcare.models.AndroidPrototypeFactory;
import org.commcare.models.database.HybridFileBackedSqlStorage;
import org.commcare.models.database.HybridFileBackedSqlStorageMock;
import org.commcare.android.database.app.models.UserKeyRecord;
import org.commcare.models.database.AndroidPrototypeFactorySetup;
import org.commcare.services.CommCareSessionService;
import org.javarosa.core.model.User;
import org.javarosa.core.services.storage.Persistable;
import org.javarosa.core.util.PrefixTree;
import org.javarosa.core.util.externalizable.PrototypeFactory;
import org.junit.Assert;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Phillip Mates (pmates@dimagi.com).
 */
public class CommCareTestApplication extends CommCareApplication {
    private static final String TAG = CommCareTestApplication.class.getSimpleName();
    private static PrototypeFactory testPrototypeFactory;
    private static final ArrayList<String> factoryClassNames = new ArrayList<>();

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
    public PrototypeFactory getPrototypeFactory(Context c) {
        TestUtils.disableSqlOptimizations();

        if (testPrototypeFactory != null) {
            return testPrototypeFactory;
        }

        // Sort of hack-y way to get the classfile dirs
        PrefixTree tree = new PrefixTree();
        initFactoryClassList();

        try {
            for (String cl : factoryClassNames) {
                tree.addString(cl);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        testPrototypeFactory = new AndroidPrototypeFactory(tree);
        return testPrototypeFactory;
    }

    /**
     * Get externalizable classes from *.class files in build dirs. Used to
     * build PrototypeFactory that mirrors a prod environment
     */
    private static void initFactoryClassList() {
        if (factoryClassNames.isEmpty()) {
            String baseODK = BuildConfig.BUILD_DIR + "/intermediates/classes/commcare/debug/";
            String baseJR = BuildConfig.PROJECT_DIR + "/../javarosa/build/classes/main/";
            String baseCC = BuildConfig.PROJECT_DIR + "/../commcare/build/classes/main/";
            addExternalizableClassesFromDir(baseODK, factoryClassNames);
            addExternalizableClassesFromDir(baseCC, factoryClassNames);
            addExternalizableClassesFromDir(baseJR, factoryClassNames);
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
                        .replace("/", ".")
                        .replace(".class", "")
                        .replace(".class", "");
                AndroidPrototypeFactorySetup.loadClass(className, externClasses);
            }
        } catch (Exception e) {
            Log.w(TAG, e.getMessage());
        }
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
        CommCareSessionService ccService = new CommCareSessionService();
        ccService.createCipherPool();
        ccService.prepareStorage(symetricKey, record);
        ccService.startSession(getUserFromDb(ccService, record), record);

        CommCareApplication._().setTestingService(ccService);
    }

    private static User getUserFromDb(CommCareSessionService ccService, UserKeyRecord keyRecord) {
        for (User u : CommCareApplication._().getRawStorage("USER", User.class, ccService.getUserDbHandle())) {
            if (keyRecord.getUsername().equals(u.getUsername())) {
                return u;
            }
        }
        return null;
    }
}
