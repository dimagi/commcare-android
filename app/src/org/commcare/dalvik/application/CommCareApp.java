package org.commcare.dalvik.application;

import java.io.File;

import net.sqlcipher.database.SQLiteDatabase;

import org.commcare.android.database.DbHelper;
import org.commcare.android.database.SqlStorage;
import org.commcare.android.database.app.DatabaseAppOpenHelper;
import org.commcare.android.database.global.models.ApplicationRecord;
import org.commcare.android.javarosa.AndroidLogger;
import org.commcare.android.logic.GlobalConstants;
import org.commcare.android.references.JavaFileRoot;
import org.commcare.android.storage.framework.Table;
import org.commcare.android.util.AndroidCommCarePlatform;
import org.commcare.android.util.SessionUnavailableException;
import org.commcare.android.util.Stylizer;
import org.commcare.dalvik.preferences.CommCarePreferences;
import org.commcare.resources.model.Resource;
import org.commcare.resources.model.ResourceTable;
import org.javarosa.core.reference.InvalidReferenceException;
import org.javarosa.core.reference.ReferenceManager;
import org.javarosa.core.services.Logger;
import org.javarosa.core.services.locale.Localization;
import org.javarosa.core.services.storage.Persistable;
import org.javarosa.core.services.storage.StorageFullException;
import org.javarosa.core.util.UnregisteredLocaleException;

import android.content.SharedPreferences;
import android.util.Log;

/**
 * This (awkwardly named!) container is responsible for keeping track of a single
 * CommCare "App". It should be able to set up an App, break it back down, and
 * maintain all of the code needed to sandbox applicaitons
 *
 * @author ctsims
 */
public class CommCareApp {
    ApplicationRecord record;

    JavaFileRoot fileRoot;
    AndroidCommCarePlatform platform;

    private static final String TAG = CommCareApp.class.getSimpleName();

    public static Object lock = new Object();

    // This unfortunately can't be managed entirely by the application object,
    // so we have to do some here
    public static CommCareApp currentSandbox;

    private Object appDbHandleLock = new Object();
    private SQLiteDatabase appDatabase; 
    
    public static Stylizer mStylizer;
    
    public CommCareApp(ApplicationRecord record) {
        this.record = record;

        // Now, we need to identify the state of the application resources
        int[] version = CommCareApplication._().getCommCareVersion();

        // TODO: Badly coupled
        platform = new AndroidCommCarePlatform(version[0], version[1], CommCareApplication._(), this);
    }
    
    public Stylizer getStylizer(){
        return mStylizer;
    }
    
    public String storageRoot() {
        // This External Storage Directory will always destroy your data when you upgrade, which is stupid. Unfortunately
        // it's also largely unavoidable until Froyo's fix for this problem makes it to the phones. For now we're going
        // to rely on the fact that the phone knows how to fix missing/corrupt directories every time it upgrades.
        return CommCareApplication._().getAndroidFsRoot() + "app/" + record.getApplicationId() + "/";
    }

    public void createPaths() {
        String[] paths = new String[]{"", GlobalConstants.FILE_CC_INSTALL,
            GlobalConstants.FILE_CC_UPGRADE, GlobalConstants.FILE_CC_CACHE,
            GlobalConstants.FILE_CC_FORMS, GlobalConstants.FILE_CC_MEDIA,
            GlobalConstants.FILE_CC_LOGS, GlobalConstants.FILE_CC_ATTACHMENTS};

        for (String path : paths) {
            File f = new File(fsPath(path));
            if (!f.exists()) {
                f.mkdirs();
            }
        }
    }

    public String fsPath(String relative) {
        return storageRoot() + relative;
    }


    private void initializeFileRoots() {
        synchronized (lock) {
            String root = storageRoot();
            fileRoot = new JavaFileRoot(root);

            String testFileRoot = "jr://file/mytest.file";
            // Assertion: There should be _no_ other file roots when we initialize
            try {
                String testFilePath = ReferenceManager._().DeriveReference(testFileRoot).getLocalURI();
                String message = "Cannot setup sandbox. An Existing file root is set up, which directs to: " + testFilePath;
                Logger.log(AndroidLogger.TYPE_ERROR_DESIGN, message);
                throw new IllegalStateException(message);
            } catch (InvalidReferenceException ire) {
                // Expected.
            }


            ReferenceManager._().addReferenceFactory(fileRoot);

            // Double check that things point to the right place?
        }
    }

    public SharedPreferences getAppPreferences() {
        return CommCareApplication._().getSharedPreferences(getPreferencesFilename(), 0);
    }

    public void setupSandbox() {
        setupSandbox(true);
    }
    
    public void initializeStylizer() {
        mStylizer = new Stylizer(CommCareApplication._().getApplicationContext());
    }
    
    /**
     * @param createFilePaths True if file paths should be created as usual. False otherwise
     */
    public void setupSandbox(boolean createFilePaths) {
        synchronized (lock) {
            Logger.log(AndroidLogger.TYPE_RESOURCES, "Staging Sandbox: " + record.getApplicationId());
            if (currentSandbox != null) {
                currentSandbox.teardownSandbox();
            }
            // general setup
            if (createFilePaths) {
                createPaths();
            }
            initializeFileRoots();
            currentSandbox = this;
        }
    }


    public boolean initializeApplication() {
        setupSandbox();

        ResourceTable global = platform.getGlobalResourceTable();
        ResourceTable upgrade = platform.getUpgradeResourceTable();
        ResourceTable recovery = platform.getRecoveryTable();

        Log.d(TAG, "Global\n" + global.toString());

        Log.d(TAG, "Upgrade\n" + upgrade.toString());

        Log.d(TAG, "Recovery\n" + recovery.toString());


        // See if any of our tables got left in a weird state
        if (global.getTableReadiness() == ResourceTable.RESOURCE_TABLE_UNCOMMITED) {
            global.rollbackCommits();
            Log.d(TAG, "Global after rollback\n" + global.toString());
        }
        if (upgrade.getTableReadiness() == ResourceTable.RESOURCE_TABLE_UNCOMMITED) {
            upgrade.rollbackCommits();
            Log.d(TAG, "upgrade after rollback\n" + upgrade.toString());
        }

        // See if we got left in the middle of an update
        if (global.getTableReadiness() == ResourceTable.RESOURCE_TABLE_UNSTAGED) {
            // If so, repair the global table. (Always takes priority over
            // maintaining the update)
            global.repairTable(upgrade);
        }

        // TODO: This, but better.
        Resource profile = global.getResourceWithId("commcare-application-profile");
        if (profile != null && profile.getStatus() == Resource.RESOURCE_STATUS_INSTALLED) {
            platform.initialize(global);
            try {
                Localization.setLocale(getAppPreferences().getString("cur_locale", "default"));
            } catch (UnregisteredLocaleException urle) {
                Localization.setLocale(Localization.getGlobalLocalizerAdvanced().getAvailableLocales()[0]);
            }
            
            initializeStylizer();
            
            return true;
        }
        
       
        return false;
    }

    public boolean areResourcesValidated() {
        SharedPreferences appPreferences = getAppPreferences();
        return (appPreferences.getBoolean("isValidated", false) ||
                appPreferences.getString(CommCarePreferences.CONTENT_VALIDATED, "no").equals(CommCarePreferences.YES));
    }

    public void setResourcesValidated(boolean isValidated) {
        SharedPreferences.Editor editor = getAppPreferences().edit();
        editor.putBoolean("isValidated", isValidated);
        editor.commit();
    }

    public void teardownSandbox() {
        synchronized (lock) {
            Logger.log(AndroidLogger.TYPE_RESOURCES, "Tearing down sandbox: " + record.getApplicationId());
            ReferenceManager._().removeReferenceFactory(fileRoot);

            synchronized (appDbHandleLock) {
                if (appDatabase != null) {
                    appDatabase.close();
                }
                appDatabase = null;
            }
        }
    }

    public AndroidCommCarePlatform getCommCarePlatform() {
        return platform;
    }

    public <T extends Persistable> SqlStorage<T> getStorage(Class<T> c) {
        return getStorage(c.getAnnotation(Table.class).value(), c);
    }

    public <T extends Persistable> SqlStorage<T> getStorage(String name, Class<T> c) {
        return new SqlStorage<T>(name, c, new DbHelper(CommCareApplication._().getApplicationContext()) {
            /*
             * (non-Javadoc)
             * @see org.commcare.android.database.DbHelper#getHandle()
             */
            @Override
            public SQLiteDatabase getHandle() {
                synchronized (appDbHandleLock) {
                    if (appDatabase == null || !appDatabase.isOpen()) {
                        appDatabase = new DatabaseAppOpenHelper(this.c, record.getApplicationId()).getWritableDatabase("null");
                    }
                    return appDatabase;
                }
            }
        });
    }

    public void clearInstallData() {
        ResourceTable global = platform.getGlobalResourceTable();

        // Install was botched, clear anything left lying around....
        global.clear();
    }

    public void writeInstalled() {
        record.setStatus(ApplicationRecord.STATUS_INSTALLED);
        try {
            CommCareApplication._().getGlobalStorage(ApplicationRecord.class).write(record);
        } catch (StorageFullException e) {
            throw new RuntimeException(e);
        }
    }
    
    public String getPreferencesFilename(){
        return record.getApplicationId();
    }
}
