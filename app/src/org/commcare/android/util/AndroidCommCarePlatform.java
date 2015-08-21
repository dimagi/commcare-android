package org.commcare.android.util;

import android.net.Uri;

import org.commcare.dalvik.application.CommCareApp;
import org.commcare.dalvik.application.CommCareApplication;
import org.commcare.resources.model.Resource;
import org.commcare.resources.model.ResourceTable;
import org.commcare.suite.model.Profile;
import org.commcare.suite.model.Suite;
import org.commcare.util.CommCarePlatform;
import org.javarosa.core.model.instance.FormInstance;
import org.javarosa.core.services.storage.IStorageUtilityIndexed;

import java.util.Hashtable;
import java.util.Set;
import java.util.Vector;

/**
 * @author ctsims
 */
public class AndroidCommCarePlatform extends CommCarePlatform {

    private final Hashtable<String, String> xmlnstable;
    private ResourceTable global;
    private ResourceTable upgrade;
    private ResourceTable recovery;

    private Profile profile;
    private final Vector<Suite> installedSuites;
    private final CommCareApp app;

    public AndroidCommCarePlatform(int majorVersion, int minorVersion, CommCareApp app) {
        super(majorVersion, minorVersion);
        xmlnstable = new Hashtable<String, String>();
        installedSuites = new Vector<Suite>();
        this.app = app;
    }

    public void registerXmlns(String xmlns, String filepath) {
        xmlnstable.put(xmlns, filepath);
    }

    public Set<String> getInstalledForms() {
        return xmlnstable.keySet();
    }

    public Uri getFormContentUri(String xFormNamespace) {
        if (xmlnstable.containsKey(xFormNamespace)) {
            return Uri.parse(xmlnstable.get(xFormNamespace));
        }

        // Search through manually?
        return null;
    }

    public ResourceTable getGlobalResourceTable() {
        if (global == null) {
            IStorageUtilityIndexed globalStorage =
                    app.getStorage(CommCareApp.GLOBAL_STORAGE_TABLE, Resource.class);
            global = ResourceTable.RetrieveTable(globalStorage,
                    new AndroidResourceInstallerFactory(app));
        }
        return global;
    }

    public ResourceTable getUpgradeResourceTable() {
        if (upgrade == null) {
            IStorageUtilityIndexed upgradeStorage =
                    app.getStorage(CommCareApp.UPGRADE_STORAGE_TABLE, Resource.class);
            upgrade = ResourceTable.RetrieveTable(upgradeStorage,
                    new AndroidResourceInstallerFactory(app));
        }
        return upgrade;
    }

    public ResourceTable getRecoveryTable() {
        if (recovery == null) {
            IStorageUtilityIndexed recoveryStorage =
                    app.getStorage(CommCareApp.RECOVERY_STORAGE_TABLE, Resource.class);
            recovery = ResourceTable.RetrieveTable(recoveryStorage,
                    new AndroidResourceInstallerFactory(app));
        }
        return recovery;
    }

    public Profile getCurrentProfile() {
        return profile;
    }

    public Vector<Suite> getInstalledSuites() {
        return installedSuites;
    }

    @Override
    public void setProfile(Profile p) {
        this.profile = p;
    }

    @Override
    public void registerSuite(Suite s) {
        this.installedSuites.add(s);
    }

    @Override
    public void initialize(ResourceTable global) {
        this.profile = null;
        this.installedSuites.clear();
        // We also need to clear any _resource table_ linked localization files which may have
        // been registered from another app, or from a pre-install location.
        CommCareApplication._().intializeDefaultLocalizerData();

        super.initialize(global);
    }

    public IStorageUtilityIndexed<FormInstance> getFixtureStorage() {
        return app.getStorage("fixture", FormInstance.class);
    }
}
