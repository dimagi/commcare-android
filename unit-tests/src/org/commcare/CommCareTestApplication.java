package org.commcare;

import android.content.Context;
import android.util.Log;

import org.commcare.android.util.TestUtils;
import org.commcare.models.AndroidPrototypeFactory;
import org.commcare.models.database.DbUtil;
import org.commcare.models.database.HybridFileBackedSqlStorage;
import org.commcare.models.database.HybridFileBackedSqlStorageMock;
import org.commcare.models.database.SqlStorage;
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
    private static PrototypeFactory testPrototypeFactor;

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

        if (testPrototypeFactor != null) {
            return testPrototypeFactor;
        }

        ArrayList<String> classNames = new ArrayList<>();
        String baseODK = "/home/mates/dimagi/commcare-odk/build/intermediates/classes/commcare/debug/";
        String baseJR = "/home/mates/dimagi/javarosa/build/classes/main/";
        String baseCC = "/home/mates/dimagi/commcare/build/classes/main/";
        processTest(baseODK, classNames);
        processTest(baseCC, classNames);
        processTest(baseJR, classNames);
        PrefixTree tree = new PrefixTree();

        try {
            for (String cl : classNames) {
                tree.addString(cl);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        testPrototypeFactor = new AndroidPrototypeFactory(tree);
        return testPrototypeFactor;
    }


    private static void processTest(String baseClassPath, List<String> list) {
        ArrayList<String> classNames = new ArrayList<>();
        try {
            File f = new File(baseClassPath);
            ArrayList<File> files = new ArrayList<>();
            getFilesInDir(f, files);
            for (File file : files) {
                String fullName = file.getAbsolutePath();
                String className1 = fullName.replace(baseClassPath, "");
                String className2 = className1.replace("/", ".").replace(".class", "");
                String className3 = className2.replace(".class", "");
                classNames.add(className3);
            }
        } catch (Exception e) {
            Log.w(TAG, e.getMessage());
        }

        for (String className : classNames) {
            DbUtil.loadClass(className, list);
        }
        Log.w(TAG, classNames.get(0));
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

}
