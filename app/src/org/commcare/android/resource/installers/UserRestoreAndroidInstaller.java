package org.commcare.android.resource.installers;

import org.commcare.resources.model.Resource;
import org.commcare.resources.model.ResourceInitializationException;
import org.commcare.resources.model.UnresolvedResourceException;
import org.commcare.suite.model.OfflineUserRestore;
import org.commcare.utils.AndroidCommCarePlatform;
import org.javarosa.core.reference.Reference;
import org.javarosa.core.util.externalizable.DeserializationException;
import org.javarosa.core.util.externalizable.ExtUtil;
import org.javarosa.core.util.externalizable.PrototypeFactory;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * @author Phillip Mates (pmates@dimagi.com)
 */
public class UserRestoreAndroidInstaller extends FileSystemInstaller {
    private String username;
    private String password;

    @SuppressWarnings("unused")
    public UserRestoreAndroidInstaller() {
        // for externalization
    }

    public UserRestoreAndroidInstaller(String localDestination, String upgradeDestination,
                                       String username, String password) {
        super(localDestination, upgradeDestination);

        this.username = username;
        this.password = password;
    }

    @Override
    public boolean initialize(AndroidCommCarePlatform instance, boolean isUpgrade) throws ResourceInitializationException {
        if (localLocation == null) {
            throw new ResourceInitializationException("The user restore file location is null!");
        }
        OfflineUserRestore offlineUserRestore = new OfflineUserRestore(localLocation, username, password);
        instance.registerDemoUserRestore(offlineUserRestore);
        return true;
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

        this.username = ExtUtil.nullIfEmpty(ExtUtil.readString(in));
        this.password = ExtUtil.nullIfEmpty(ExtUtil.readString(in));
    }

    @Override
    public void writeExternal(DataOutputStream out) throws IOException {
        super.writeExternal(out);

        ExtUtil.writeString(out, ExtUtil.emptyIfNull(username));
        ExtUtil.writeString(out, ExtUtil.emptyIfNull(password));
    }
}
