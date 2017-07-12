package org.commcare.android.resource.installers;

import org.commcare.AppUtils;
import org.commcare.CommCareApplication;
import org.commcare.resources.model.Resource;
import org.commcare.resources.model.UnresolvedResourceException;
import org.commcare.suite.model.OfflineUserRestore;
import org.commcare.utils.AndroidCommCarePlatform;
import org.commcare.utils.SessionUnavailableException;
import org.javarosa.core.reference.InvalidReferenceException;
import org.javarosa.core.reference.Reference;
import org.javarosa.xml.util.InvalidStructureException;
import org.javarosa.xml.util.UnfullfilledRequirementsException;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;

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
    public boolean initialize(AndroidCommCarePlatform instance, boolean isUpgrade) {
        instance.registerDemoUserRestore(initDemoUserRestore());
        return true;
    }

    private OfflineUserRestore initDemoUserRestore() {
        try {
            return new OfflineUserRestore(localLocation);
        } catch (UnfullfilledRequirementsException e) {
            throw new RuntimeException(e.getMessage(), e);
        } catch (IOException | InvalidStructureException | XmlPullParserException e) {
            throw new RuntimeException("Demo user restore file was malformed, " +
                    "the following error occurred during parsing: " + e.getMessage(), e);
        } catch (InvalidReferenceException e) {
            throw new RuntimeException(
                    "Reference to demo user restore file was invalid: " + e.getMessage(), e);
        }
    }

    @Override
    protected int customInstall(Resource r, Reference local, boolean upgrade)
            throws IOException, UnresolvedResourceException {

        // To make sure that we won't fail on this later, after we have already committed to
        // the upgrade being good to go
        try {
            initDemoUserRestore();
        } catch (RuntimeException e) {
            throw new UnresolvedResourceException(r, e, e.getMessage(), true);
        }

        if (upgrade) {
            OfflineUserRestore currentOfflineUserRestore =
                    CommCareApplication.instance().getCommCarePlatform().getDemoUserRestore();
            if (currentOfflineUserRestore != null) {
                try {
                    AppUtils.wipeSandboxForUser(currentOfflineUserRestore.getUsername());
                } catch (SessionUnavailableException e) {
                    // Means we are updating from app manager so there's no session, and the sandbox
                    // has already been wiped
                }
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
