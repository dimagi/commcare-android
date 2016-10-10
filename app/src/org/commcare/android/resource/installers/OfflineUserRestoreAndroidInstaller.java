package org.commcare.android.resource.installers;

import org.commcare.CommCareApplication;
import org.commcare.android.database.app.models.UserKeyRecord;
import org.commcare.models.database.user.DatabaseUserOpenHelper;
import org.commcare.resources.model.Resource;
import org.commcare.resources.model.ResourceInitializationException;
import org.commcare.resources.model.UnresolvedResourceException;
import org.commcare.suite.model.OfflineUserRestore;
import org.commcare.utils.AndroidCommCarePlatform;
import org.javarosa.core.reference.InvalidReferenceException;
import org.javarosa.core.reference.Reference;
import org.javarosa.core.services.storage.EntityFilter;
import org.javarosa.core.util.externalizable.DeserializationException;
import org.javarosa.core.util.externalizable.PrototypeFactory;
import org.javarosa.xml.util.InvalidStructureException;
import org.javarosa.xml.util.UnfullfilledRequirementsException;
import org.xmlpull.v1.XmlPullParserException;

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
        try {
            OfflineUserRestore offlineUserRestore = new OfflineUserRestore(localLocation);
            instance.registerDemoUserRestore(offlineUserRestore);
            return true;
        } catch (UnfullfilledRequirementsException e) {
            throw new ResourceInitializationException(e.getMessage());
        } catch (IOException | InvalidStructureException | XmlPullParserException e) {
            throw new ResourceInitializationException("Demo user restore file was malformed, " +
                    "the following error occurred during parsing: " + e.getMessage());
        } catch (InvalidReferenceException e) {
            throw new ResourceInitializationException(
                    "Reference to demo user restore file was invalid: " + e.getMessage());
        }
    }

    @Override
    protected int customInstall(Resource r, Reference local, boolean upgrade) throws IOException, UnresolvedResourceException {
        if (upgrade) {
            OfflineUserRestore currentOfflineUserRestore =
                    CommCareApplication._().getCommCarePlatform().getDemoUserRestore();
            if (currentOfflineUserRestore != null) {
                CommCareApplication._().wipeSandboxForUser(currentOfflineUserRestore.getUsername());
            }
            return Resource.RESOURCE_STATUS_UPGRADE;
        } else {
            return Resource.RESOURCE_STATUS_INSTALLED;
        }
    }

    @Override
    public boolean requiresRuntimeInitialization() {
        return true;
    }

}
