package org.commcare.android.resource.installers;

import org.commcare.CommCareApplication;
import org.commcare.android.database.app.models.UserKeyRecord;
import org.commcare.android.logging.ReportingUtils;
import org.commcare.models.encryption.ByteEncrypter;
import org.commcare.resources.model.Resource;
import org.commcare.resources.model.ResourceInitializationException;
import org.commcare.resources.model.UnresolvedResourceException;
import org.commcare.suite.model.OfflineUserRestore;
import org.commcare.utils.AndroidCommCarePlatform;
import org.javarosa.core.reference.Reference;
import org.javarosa.core.util.externalizable.DeserializationException;
import org.javarosa.core.util.externalizable.PrototypeFactory;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * @author Phillip Mates (pmates@dimagi.com)
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
            clearDataForCurrentDemoUser(instance);
        }
        OfflineUserRestore offlineUserRestore = new OfflineUserRestore(localLocation);
        instance.registerDemoUserRestore(offlineUserRestore);
        return true;
    }

    private void clearDataForCurrentDemoUser(AndroidCommCarePlatform instance) {
        OfflineUserRestore current = instance.getDemoUserRestore();

        if (!current.getUsername().equals(ReportingUtils.getUser())) {
            UserKeyRecord ukr = UserKeyRecord.getCurrentValidRecordByPassword(
                    CommCareApplication._().getCurrentApp(), current.getUsername(),
                    current.getPassword(), true);
            if (ukr == null) {
                // means we never logged in with the old restore
                return;
            }
            CommCareApplication._().startUserSession(
                    ByteEncrypter.unwrapByteArrayWithString(ukr.getEncryptedKey(), current.getPassword()),
                    ukr, false);
        }

        CommCareApplication._().clearUserData();
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
