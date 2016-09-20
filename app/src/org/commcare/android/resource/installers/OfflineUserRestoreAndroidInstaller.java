package org.commcare.android.resource.installers;

import org.commcare.CommCareApplication;
import org.commcare.android.database.app.models.UserKeyRecord;
import org.commcare.models.database.user.DatabaseUserOpenHelper;
import org.commcare.resources.model.Resource;
import org.commcare.resources.model.ResourceInitializationException;
import org.commcare.resources.model.UnresolvedResourceException;
import org.commcare.suite.model.OfflineUserRestore;
import org.commcare.utils.AndroidCommCarePlatform;
import org.javarosa.core.reference.Reference;
import org.javarosa.core.services.storage.EntityFilter;
import org.javarosa.core.util.externalizable.DeserializationException;
import org.javarosa.core.util.externalizable.PrototypeFactory;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

/**
 * @author Phillip Mates (pmates@dimagi.com)
 * @author Aliza Stone (astone@dimagi.com)
 */
public class OfflineUserRestoreAndroidInstaller extends FileSystemInstaller {

    @SuppressWarnings("unused")
    public OfflineUserRestoreAndroidInstaller() {
        // for externalization
    }

    public OfflineUserRestoreAndroidInstaller(String localDestination, String upgradeDestination) {
        super(localDestination, upgradeDestination);
    }

    @Override
    public boolean initialize(AndroidCommCarePlatform instance, boolean isUpgrade) throws ResourceInitializationException {
        if (localLocation == null) {
            throw new ResourceInitializationException("The user restore file location is null!");
        }
        if (isUpgrade) {
            wipeSandboxForCurrentDemoUser(instance);
        }
        OfflineUserRestore offlineUserRestore = new OfflineUserRestore(localLocation);
        instance.registerDemoUserRestore(offlineUserRestore);
        return true;
    }

    private void wipeSandboxForCurrentDemoUser(AndroidCommCarePlatform instance) {
        final OfflineUserRestore current = instance.getDemoUserRestore();

        UserKeyRecord ukr = UserKeyRecord.getCurrentValidRecordByPassword(
                CommCareApplication._().getCurrentApp(), current.getUsername(),
                current.getPassword(), true);
        if (ukr == null) {
            // means we never logged in with the old demo user
            return;
        }

        final Set<String> dbIdsToRemove = new HashSet<>();
        CommCareApplication._().getAppStorage(UserKeyRecord.class).removeAll(new EntityFilter<UserKeyRecord>() {
            @Override
            public boolean matches(UserKeyRecord ukr) {
                if (ukr.getUsername().equalsIgnoreCase(current.getUsername().toLowerCase())) {
                    dbIdsToRemove.add(ukr.getUuid());
                    return true;
                }
                return false;
            }
        });
        for (String id : dbIdsToRemove) {
            CommCareApplication._().getDatabasePath(DatabaseUserOpenHelper.getDbName(id)).delete();
        }
    }

    @Override
    protected int customInstall(Resource r, Reference local, boolean upgrade) throws IOException, UnresolvedResourceException {
        return upgrade ? Resource.RESOURCE_STATUS_UPGRADE : Resource.RESOURCE_STATUS_INSTALLED;
    }

    @Override
    public boolean requiresRuntimeInitialization() {
        return true;
    }

    @Override
    public void readExternal(DataInputStream in, PrototypeFactory pf)
            throws IOException, DeserializationException {
        super.readExternal(in, pf);
    }

    @Override
    public void writeExternal(DataOutputStream out) throws IOException {
        super.writeExternal(out);
    }
}
