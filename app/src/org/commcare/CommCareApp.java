package org.commcare;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import net.sqlcipher.database.SQLiteDatabase;

import org.commcare.android.database.global.models.ApplicationRecord;
import org.commcare.dalvik.BuildConfig;
import org.commcare.engine.references.JavaFileRoot;
import org.commcare.interfaces.AppFilePathBuilder;
import org.commcare.models.database.AndroidDbHelper;
import org.commcare.models.database.HybridFileBackedSqlHelpers;
import org.commcare.models.database.SqlStorage;
import org.commcare.models.database.UnencryptedHybridFileBackedSqlStorage;
import org.commcare.models.database.app.DatabaseAppOpenHelper;
import org.commcare.modern.database.Table;
import org.commcare.preferences.HiddenPreferences;
import org.commcare.preferences.MainConfigurablePreferences;
import org.commcare.preferences.PrefValues;
import org.commcare.provider.ProviderUtils;
import org.commcare.resources.model.Resource;
import org.commcare.resources.model.ResourceInitializationException;
import org.commcare.resources.model.ResourceTable;
import org.commcare.suite.model.Menu;
import org.commcare.suite.model.Suite;
import org.commcare.tasks.PrimeEntityCacheHelper;
import org.commcare.util.CommCarePlatform;
import org.commcare.util.LogTypes;
import org.commcare.utils.AndroidCommCarePlatform;
import org.commcare.utils.GlobalConstants;
import org.commcare.utils.MultipleAppsUtil;
import org.commcare.utils.SessionUnavailableException;
import org.commcare.utils.Stylizer;
import org.javarosa.core.model.condition.EvaluationContext;
import org.javarosa.core.reference.InvalidReferenceException;
import org.javarosa.core.reference.ReferenceManager;
import org.javarosa.core.services.Logger;
import org.javarosa.core.services.locale.Localization;
import org.javarosa.core.services.storage.Persistable;
import org.javarosa.core.util.SizeBoundUniqueVector;
import org.javarosa.core.util.UnregisteredLocaleException;
import org.javarosa.xpath.XPathException;
import org.javarosa.xpath.expr.FunctionUtils;
import org.javarosa.xpath.expr.XPathExpression;
import org.javarosa.xpath.parser.XPathSyntaxException;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.List;

/**
 * This (awkwardly named!) container is responsible for keeping track of a single
 * CommCare "App". It should be able to set up an App, break it back down, and
 * maintain all of the code needed to sandbox applications
 *
 * @author ctsims
 */
public class CommCareApp implements AppFilePathBuilder {

    private ApplicationRecord record;

    protected JavaFileRoot fileRoot;
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
    private volatile PrimeEntityCacheHelper primeEntityCacheHelper;

    public CommCareApp(ApplicationRecord record) {
        this.record = record;
        // Now, we need to identify the state of the application resources
        int[] version = CommCareApplication.instance().getCommCareVersion();

        // TODO: Badly coupled
        platform = new AndroidCommCarePlatform(version[0], version[1], version[2], this);
    }

    public Stylizer getStylizer() {
        return mStylizer;
    }

    public String storageRoot() {
        return CommCareApplication.instance().getAndroidFsRoot() + "app/" + record.getApplicationId() + "/";
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

    @Override
    public String fsPath(String relativeSubDir) {
        return storageRoot() + relativeSubDir;
    }

    private void initializeFileRoots() {
        synchronized (lock) {
            String root = storageRoot();
            fileRoot = new JavaFileRoot(root);

            String testFileRoot = "jr://file/mytest.file";
            // Assertion: There should be _no_ other file roots when we initialize
            try {
                String testFilePath = ReferenceManager.instance().DeriveReference(testFileRoot).getLocalURI();
                String message = "Cannot setup sandbox. An Existing file root is set up, which directs to: " + testFilePath;
                Logger.log(LogTypes.TYPE_ERROR_DESIGN, message);
                throw new IllegalStateException(message);
            } catch (InvalidReferenceException ire) {
                // Expected.
            }


            ReferenceManager.instance().addReferenceFactory(fileRoot);

            // Double check that things point to the right place?
        }
    }

    public SharedPreferences getAppPreferences() {
        return CommCareApplication.instance().getSharedPreferences(getPreferencesFilename(), Context.MODE_PRIVATE);
    }

    public void setupSandbox() {
        setupSandbox(true);
    }

    /**
     * @param createFilePaths True if file paths should be created as usual. False otherwise
     */
    public void setupSandbox(boolean createFilePaths) {
        synchronized (lock) {
            Logger.log(LogTypes.TYPE_RESOURCES, "Staging Sandbox: " + record.getApplicationId());
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
        CommCareApplication.instance().getGlobalStorage(ApplicationRecord.class).write(record);
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

        logTable("Global", global);
        logTable("Upgrade", upgrade);
        logTable("Recovery", recovery);

        // See if any of our tables got left in a weird state
        if (global.getTableReadiness() == ResourceTable.RESOURCE_TABLE_UNCOMMITED) {
            global.rollbackCommits(platform);
            logTable("Global after rollback", global);
        }
        if (upgrade.getTableReadiness() == ResourceTable.RESOURCE_TABLE_UNCOMMITED) {
            upgrade.rollbackCommits(platform);
            logTable("Upgrade after rollback", upgrade);
        }

        // See if we got left in the middle of an update
        if (global.getTableReadiness() == ResourceTable.RESOURCE_TABLE_UNSTAGED) {
            // If so, repair the global table. (Always takes priority over maintaining the update)
            global.repairTable(upgrade, platform);
        }

        Resource profile = global.getResourceWithId(CommCarePlatform.APP_PROFILE_RESOURCE_ID);
        if (profile != null && profile.getStatus() == Resource.RESOURCE_STATUS_INSTALLED) {
            try {
                platform.initialize(global, false);

                // Return false if resources are missing
                if (!global.getMissingResources().isEmpty()) {
                    return false;
                }

                Localization.setLocale(
                        getAppPreferences().getString(MainConfigurablePreferences.PREFS_LOCALE_KEY, "default"));
            } catch (UnregisteredLocaleException urle) {
                Localization.setLocale(Localization.getGlobalLocalizerAdvanced().getAvailableLocales()[0]);
            } catch (ResourceInitializationException e) {
                Logger.exception("Initialization of platform failed due to resource initialization failure", e);
                Logger.log(LogTypes.TYPE_RESOURCES,
                        "Initialization of platform failed due to resource initialization failure");
                return false;
            }

            initializeStylizer();

            try {
                HybridFileBackedSqlHelpers.removeOrphanedFiles(buildAndroidDbHelper().getHandle());
            } catch (SessionUnavailableException e) {
                Logger.log(LogTypes.SOFT_ASSERT,
                        "Unable to get app db handle to clear orphaned files");
            }
            return true;
        } else {
            SizeBoundUniqueVector<Resource> missingResources = new SizeBoundUniqueVector<>(1);
            missingResources.add(profile);
            global.setMissingResources(missingResources);
        }

        String failureReason = profile == null ? "profle being null" : "profile status value " + profile.getStatus();
        Logger.log(LogTypes.TYPE_RESOURCES, "Initializing application failed because of " + failureReason);
        return false;
    }

    private static void logTable(String name, ResourceTable table) {
        if (BuildConfig.DEBUG) {
            // Avoid printing resource tables in production; it's expensive
            Log.d(TAG, name + "\n" + table.toString());
        }
    }

    private void initializeStylizer() {
        mStylizer = new Stylizer(CommCareApplication.instance().getApplicationContext());
    }


    public boolean areMMResourcesValidated() {
        SharedPreferences appPreferences = getAppPreferences();
        return (appPreferences.getBoolean("isValidated", false) ||
                appPreferences.getString(HiddenPreferences.MM_VALIDATED_FROM_HQ, PrefValues.NO)
                        .equals(PrefValues.YES));
    }

    public void setMMResourcesValidated() {
        SharedPreferences.Editor editor = getAppPreferences().edit();
        editor.putBoolean("isValidated", true);
        editor.apply();
        record.setResourcesStatus(true);
        CommCareApplication.instance().getGlobalStorage(ApplicationRecord.class).write(record);
    }

    public int getAppResourceState() {
        return resourceState;
    }

    public void setAppResourceState(int resourceState) {
        this.resourceState = resourceState;
    }

    public void teardownSandbox() {
        synchronized (lock) {
            Logger.log(LogTypes.TYPE_RESOURCES, "Tearing down sandbox: " + record.getApplicationId());
            ReferenceManager.instance().removeReferenceFactory(fileRoot);

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

    public boolean hasVisibleTrainingContent() {
        // This is the same eval context that would be used to evaluate the relevancy conditions of
        // these menus when they're actually loaded
        EvaluationContext ec =
                CommCareApplication.instance().getCurrentSessionWrapper().getEvaluationContext(Menu.TRAINING_MENU_ROOT);
        for (Suite s : platform.getInstalledSuites()) {
            if (visibleMenusInTrainingRoot(s) || visibleEntriesInTrainingRoot(s, ec)) {
                return true;
            }
        }
        return false;
    }

    private static boolean visibleMenusInTrainingRoot(Suite s) {
        List<Menu> trainingMenus = s.getMenusWithRoot(Menu.TRAINING_MENU_ROOT);
        if (trainingMenus != null) {
            for (Menu m : trainingMenus) {
                try {
                    if (m.getMenuRelevance() == null){
                        return true;
                    }

                    EvaluationContext menuEvalContext = CommCareApplication.instance()
                            .getCurrentSessionWrapper()
                            .getEvaluationContext(m.getCommandID());

                    if (FunctionUtils.toBoolean(m.getMenuRelevance().eval(menuEvalContext))) {
                        return true;
                    }
                } catch (XPathSyntaxException | XPathException e) {
                    // Now is the wrong time to show the user an error about this since they
                    // haven't actually navigated to the menu. To be safe, just assume that this
                    // menu is visible, and then if they navigate to it they'll see the XPath error there
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean visibleEntriesInTrainingRoot(Suite s, EvaluationContext ec) {
        List<Menu> trainingRoots = s.getMenusWithId(Menu.TRAINING_MENU_ROOT);
        if (trainingRoots == null || trainingRoots.isEmpty()) {
            return false;
        } else if (trainingRoots.size() > 1) {
            throw new RuntimeException("An app is allowed to contain at most 1 menu with ID " +
                    "'training-root', but this app has more than one.");
        }

        Menu trainingRoot = trainingRoots.get(0);
        for (String command : trainingRoot.getCommandIds()) {
            try {
                XPathExpression relevancyCondition =
                        trainingRoot.getCommandRelevance(trainingRoot.indexOfCommand(command));
                if (relevancyCondition == null || FunctionUtils.toBoolean(relevancyCondition.eval(ec))) {
                    return true;
                }
            } catch (XPathSyntaxException | XPathException e) {
                // Now is the wrong time to show the user an error about this since they
                // haven't actually navigated to the menu. To be safe, just assume that this
                // entry is visible, and then if they navigate to it they'll see the XPath error there
                return true;
            }
        }
        return false;
    }

    public <T extends Persistable> SqlStorage<T> getStorage(Class<T> c) {
        return getStorage(c.getAnnotation(Table.class).value(), c);
    }

    public <T extends Persistable> SqlStorage<T> getStorage(String name, Class<T> c) {
        return new SqlStorage<>(name, c, buildAndroidDbHelper());
    }

    public <T extends Persistable> UnencryptedHybridFileBackedSqlStorage<T> getFileBackedStorage(String name, Class<T> c) {
        return new UnencryptedHybridFileBackedSqlStorage<>(name, c, buildAndroidDbHelper(), this);
    }

    protected AndroidDbHelper buildAndroidDbHelper() {
        return new AndroidDbHelper(CommCareApplication.instance().getApplicationContext()) {
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

    public PrimeEntityCacheHelper getPrimeEntityCacheHelper() {
        if (primeEntityCacheHelper == null) {
            synchronized (this) {
                if (primeEntityCacheHelper == null) {
                    primeEntityCacheHelper = new PrimeEntityCacheHelper();
                }
            }
        }
        return primeEntityCacheHelper;
    }

    /**
     * Initialize all of the properties that an app record should have and update it to the
     * installed state.
     */
    public void writeInstalled() {
        record.setStatus(ApplicationRecord.STATUS_INSTALLED);
        record.setResourcesStatus(areMMResourcesValidated());
        record.setPropertiesFromProfile(getCommCarePlatform().getCurrentProfile());
        CommCareApplication.instance().getGlobalStorage(ApplicationRecord.class).write(record);
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
        this.record = MultipleAppsUtil.getAppById(this.record.getUniqueId());
    }

    /**
     * For testing purposes only
     */
    public static SQLiteDatabase getAppDatabaseForTesting() {
        if (BuildConfig.DEBUG) {
            return CommCareApplication.instance().getCurrentApp().buildAndroidDbHelper().getHandle();
        } else {
            throw new RuntimeException("For testing purposes only!");
        }
    }
}
