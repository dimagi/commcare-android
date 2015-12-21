package org.commcare.dalvik.application;

import android.content.SharedPreferences;
import android.util.Log;

import net.sqlcipher.database.SQLiteDatabase;

import org.commcare.android.database.AndroidDbHelper;
import org.commcare.android.database.SqlFileBackedStorage;
import org.commcare.android.database.SqlStorage;
import org.commcare.android.database.app.DatabaseAppOpenHelper;
import org.commcare.android.database.global.models.ApplicationRecord;
import org.commcare.android.javarosa.AndroidLogger;
import org.commcare.android.logic.GlobalConstants;
import org.commcare.android.references.JavaFileRoot;
import org.commcare.android.storage.framework.Table;
import org.commcare.android.util.AndroidCommCarePlatform;
import org.commcare.android.util.Stylizer;
import org.commcare.dalvik.odk.provider.ProviderUtils;
import org.commcare.dalvik.preferences.CommCarePreferences;
import org.commcare.resources.model.Resource;
import org.commcare.resources.model.ResourceTable;
import org.commcare.util.CommCarePlatform;
import org.javarosa.core.reference.InvalidReferenceException;
import org.javarosa.core.reference.ReferenceManager;
import org.javarosa.core.services.Logger;
import org.javarosa.core.services.locale.Localization;
import org.javarosa.core.services.storage.Persistable;
import org.javarosa.core.util.UnregisteredLocaleException;

import java.io.File;

/**
 * This (awkwardly named!) container is responsible for keeping track of a single
 * CommCare "App". It should be able to set up an App, break it back down, and
 * maintain all of the code needed to sandbox applicaitons
 *
 * @author ctsims
 */
public class CommCareApp {
    private ApplicationRecord record;

    private JavaFileRoot fileRoot;
    private final AndroidCommCarePlatform platform;

    private static final String TAG = CommCareApp.class.getSimpleName();

    private static final Object lock = new Object();

    // This unfortunately can't be managed entirely by the application object,
    // so we have to do some here
    public static CommCareApp currentSandbox;

    private final Object appDbHandleLock = new Object();
    private SQLiteDatabase appDatabase; 
    
    private static Stylizer mStylizer;

    private int resourceState;
    
    public CommCareApp(ApplicationRecord record) {
        this.record = record;

        // Now, we need to identify the state of the application resources
        int[] version = CommCareApplication._().getCommCareVersion();

        // TODO: Badly coupled
        platform = new AndroidCommCarePlatform(version[0], version[1], this);
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

    private void createPaths() {
        String[] paths = new String[]{"", GlobalConstants.FILE_CC_INSTALL,
                GlobalConstants.FILE_CC_UPGRADE, GlobalConstants.FILE_CC_CACHE,
                GlobalConstants.FILE_CC_FORMS, GlobalConstants.FILE_CC_MEDIA,
                GlobalConstants.FILE_CC_LOGS, GlobalConstants.FILE_CC_ATTACHMENTS,
                GlobalConstants.FILE_CC_DB};

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
            ProviderUtils.setCurrentSandbox(currentSandbox);
        }
    }

    /**
     * If the CommCare app being initialized was first installed on this device with pre-Multiple
     * Apps build of CommCare, then its ApplicationRecord will have been generated from an
     * older format with missing fields. This method serves to fill in those missing fields,
     * and is called after initializeApplication, if and only if the ApplicationRecord has just
     * been generated from the old format. Once the update for an AppRecord performs once, it will
     * not be performed again.
     */
    private void updateAppRecord() {
        // Set all of the properties of this record that come from the profile
        record.setPropertiesFromProfile(getCommCarePlatform().getCurrentProfile());

        // The default value this field was set to may be incorrect, so check it
        record.setResourcesStatus(areMMResourcesValidated());

        // Set this to false so we don't try to update this app record every time we seat it
        record.setConvertedByDbUpgrader(false);

        // Commit changes
        CommCareApplication._().getGlobalStorage(ApplicationRecord.class).write(record);
    }

    public boolean initializeApplication() {
        boolean appReady = initializeApplicationHelper();
        if (appReady) {
            if (record.wasConvertedByDbUpgrader()) {
                updateAppRecord();
            }
        }
        return appReady;
    }
    
    private boolean initializeApplicationHelper() {
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
        if(global.getTableReadiness() == ResourceTable.RESOURCE_TABLE_UNSTAGED) {
            // If so, repair the global table. (Always takes priority over maintaining the update)
            global.repairTable(upgrade);
        }

        Resource profile = global.getResourceWithId(CommCarePlatform.APP_PROFILE_RESOURCE_ID);
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

    private void initializeStylizer() {
        mStylizer = new Stylizer(CommCareApplication._().getApplicationContext());
    }


    public boolean areMMResourcesValidated() {
        SharedPreferences appPreferences = getAppPreferences();
        return (appPreferences.getBoolean("isValidated", false) ||
                appPreferences.getString(CommCarePreferences.CONTENT_VALIDATED, "no").equals(CommCarePreferences.YES));
    }

    public void setMMResourcesValidated() {
        SharedPreferences.Editor editor = getAppPreferences().edit();
        editor.putBoolean("isValidated", true);
        editor.commit();
        record.setResourcesStatus(true);
        CommCareApplication._().getGlobalStorage(ApplicationRecord.class).write(record);
    }

    public int getAppResourceState() {
        return resourceState;
    }

    public void setAppResourceState(int resourceState) {
        this.resourceState = resourceState;
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
                ProviderUtils.setCurrentSandbox(null);
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
        return new SqlStorage<>(name, c, buildAndroidDbHelper());
    }

    public <T extends Persistable> SqlFileBackedStorage<T> getFileBackedStorage(String name, Class<T> c) {
        return new SqlFileBackedStorage<>(name, c, buildAndroidDbHelper(), storageRoot(), false);
    }

    private AndroidDbHelper buildAndroidDbHelper() {
        return new AndroidDbHelper(CommCareApplication._().getApplicationContext()) {
            @Override
            public SQLiteDatabase getHandle() {
                synchronized (appDbHandleLock) {
                    if (appDatabase == null || !appDatabase.isOpen()) {
                        appDatabase = new DatabaseAppOpenHelper(this.c, record.getApplicationId()).getWritableDatabase("null");
                    }
                    return appDatabase;
                }
            }
        };
    }

    /**
     * Initialize all of the properties that an app record should have and update it to the
     * installed state.
     */
    public void writeInstalled() {
        record.setStatus(ApplicationRecord.STATUS_INSTALLED);
        record.setResourcesStatus(areMMResourcesValidated());
        record.setPropertiesFromProfile(getCommCarePlatform().getCurrentProfile());
        CommCareApplication._().getGlobalStorage(ApplicationRecord.class).write(record);
    }

    public String getUniqueId() {
        return this.record.getUniqueId();
    }
    
    public String getPreferencesFilename() {
        return record.getApplicationId();
    }

    public ApplicationRecord getAppRecord() {
        return this.record;
    }

    /**
     * Refreshes this CommCareApp's ApplicationRecord pointer to be to whatever version is
     * currently sitting in the db -- should be called whenever an ApplicationRecord is updated
     * while its associated app is seated, so that the 2 are not out of sync
     */
    public void refreshAppRecord() {
        this.record = CommCareApplication._().getAppById(this.record.getUniqueId());
    }
}
