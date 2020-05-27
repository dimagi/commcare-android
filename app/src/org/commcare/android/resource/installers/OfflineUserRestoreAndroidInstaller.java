package org.commcare.android.resource.installers;

import org.commcare.AppUtils;
import org.commcare.CommCareApplication;
import org.commcare.resources.model.InvalidResourceException;
import org.commcare.resources.model.Resource;
import org.commcare.resources.model.UnresolvedResourceException;
import org.commcare.suite.model.OfflineUserRestore;
import org.commcare.utils.AndroidCommCarePlatform;
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
    public boolean initialize(AndroidCommCarePlatform platform, boolean isUpgrade) throws
            IOException, InvalidReferenceException, InvalidStructureException,
            XmlPullParserException, UnfullfilledRequirementsException {
        platform.registerDemoUserRestore(initDemoUserRestore());
        return true;
    }

    private OfflineUserRestore initDemoUserRestore() throws UnfullfilledRequirementsException, InvalidStructureException,
            XmlPullParserException, IOException, InvalidReferenceException {
        return new OfflineUserRestore(localLocation);
    }

    @Override
    protected int customInstall(Resource r, Reference local, boolean upgrade, AndroidCommCarePlatform platform)
            throws UnresolvedResourceException {

        // To make sure that we won't fail on this later, after we have already committed to
        // the upgrade being good to go
        try {
            initDemoUserRestore();
        } catch (XmlPullParserException | InvalidStructureException e) {
            throw new InvalidResourceException(r.getDescriptor(), e.getMessage());
        } catch (RuntimeException | UnfullfilledRequirementsException | IOException | InvalidReferenceException e) {
            throw new UnresolvedResourceException(r, e, e.getMessage(), true);
        }

        if (upgrade) {
            OfflineUserRestore currentOfflineUserRestore =
                    CommCareApplication.instance().getCommCarePlatform().getDemoUserRestore();
            if (currentOfflineUserRestore != null) {
                AppUtils.wipeSandboxForUser(currentOfflineUserRestore.getUsername());
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
