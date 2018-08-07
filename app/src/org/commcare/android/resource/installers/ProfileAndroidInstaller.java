package org.commcare.android.resource.installers;

import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;

import org.apache.commons.lang3.StringUtils;
import org.commcare.AppUtils;
import org.commcare.CommCareApp;
import org.commcare.CommCareApplication;
import org.commcare.android.database.global.models.ApplicationRecord;
import org.commcare.resources.model.Resource;
import org.commcare.resources.model.ResourceLocation;
import org.commcare.resources.model.ResourceTable;
import org.commcare.resources.model.UnresolvedResourceException;
import org.commcare.suite.model.Profile;
import org.commcare.suite.model.PropertySetter;
import org.commcare.util.LogTypes;
import org.commcare.utils.AndroidCommCarePlatform;
import org.commcare.utils.DummyResourceTable;
import org.commcare.xml.ProfileParser;
import org.javarosa.core.io.StreamsUtil;
import org.javarosa.core.reference.InvalidReferenceException;
import org.javarosa.core.reference.Reference;
import org.javarosa.core.reference.ReferenceManager;
import org.javarosa.core.services.Logger;
import org.javarosa.xml.util.InvalidStructureException;
import org.javarosa.xml.util.UnfullfilledRequirementsException;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

/**
 * @author ctsims
 */
public class ProfileAndroidInstaller extends FileSystemInstaller {

    private static final String KEY_TARGET_PACKAGE_ID = "target-package-id";

    @SuppressWarnings("unused")
    public ProfileAndroidInstaller() {
        // For externalization
    }

    public ProfileAndroidInstaller(String localDestination, String upgradeDestination) {
        super(localDestination, upgradeDestination);
    }


    @Override
    public boolean initialize(AndroidCommCarePlatform platform, boolean isUpgrade) throws
            IOException, InvalidReferenceException, InvalidStructureException,
            XmlPullParserException, UnfullfilledRequirementsException {
        InputStream inputStream = null;
        try {
            Reference local = ReferenceManager.instance().DeriveReference(localLocation);
            inputStream = local.getStream();
            ProfileParser parser =
                    new ProfileParser(inputStream, platform, platform.getGlobalResourceTable(),
                            null, Resource.RESOURCE_STATUS_INSTALLED, false);
            Profile p = parser.parse();
            platform.setProfile(p);
            return true;
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    @Override
    public boolean install(Resource r, ResourceLocation location, Reference ref,
                           ResourceTable table, AndroidCommCarePlatform platform, boolean upgrade, boolean recovery)
            throws UnresolvedResourceException, UnfullfilledRequirementsException {
        //First, make sure all the file stuff is managed.
        super.install(r, location, ref, table, platform, upgrade, recovery);
        InputStream inputStream = null;
        try {
            Reference local = ReferenceManager.instance().DeriveReference(localLocation);
            inputStream = local.getStream();
            ProfileParser parser = new ProfileParser(inputStream, platform, table, r.getRecordGuid(),
                    Resource.RESOURCE_STATUS_UNINITIALIZED, false);

            Profile p = parser.parse();

            if (!upgrade) {
                initProperties(p);
                if (!recovery) {
                    checkDuplicate(p);
                    checkAppTarget();
                }
            }

            table.commitCompoundResource(r, upgrade ? Resource.RESOURCE_STATUS_UPGRADE : Resource.RESOURCE_STATUS_INSTALLED, p.getVersion());
            return true;
        } catch (XmlPullParserException | InvalidReferenceException | IOException e) {
            e.printStackTrace();
        } catch (InvalidStructureException e) {
            throw new UnresolvedResourceException(r, "Invalid content in the Profile Definition: " + e.getMessage(), true);
        } finally {
            try {
                if (inputStream != null) {
                    inputStream.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        return false;
    }

    private void checkAppTarget() throws UnfullfilledRequirementsException {
        SharedPreferences prefs = CommCareApp.currentSandbox.getAppPreferences();
        if (prefs.contains(KEY_TARGET_PACKAGE_ID)) {
            String targetPackage = prefs.getString(KEY_TARGET_PACKAGE_ID, "");
            if (!StringUtils.isEmpty(targetPackage)) {
                String myAppPackage = CommCareApplication.instance().getPackageName();
                if (!myAppPackage.contentEquals(targetPackage)) {
                    String error = "This app requires " +
                            (targetPackage.contentEquals("org.commcare.lts") ? "Commcare LTS" : "Regular Commcare (Non LTS)") +
                            " to be installed";
                    throw new UnfullfilledRequirementsException(
                            error,
                            UnfullfilledRequirementsException.RequirementType.INCORRECT_TARGET_PACKAGE);
                }
            }
        }
    }

    // Check that this app is not already installed on the phone
    private void checkDuplicate(Profile p) throws UnfullfilledRequirementsException {
        String newAppId = p.getUniqueId();
        ArrayList<ApplicationRecord> installedApps = AppUtils.
                getInstalledAppRecords();
        for (ApplicationRecord record : installedApps) {
            if (record.getUniqueId().equals(newAppId)) {
                throw new UnfullfilledRequirementsException(
                        "The app you are trying to install already exists on this device",
                        UnfullfilledRequirementsException.RequirementType.DUPLICATE_APP);
            }
        }
    }

    private void initProperties(Profile profile) {
        // TODO Baaaaaad. Encapsulate this better!!!
        SharedPreferences prefs = CommCareApp.currentSandbox.getAppPreferences();
        Editor editor = prefs.edit();
        for (PropertySetter p : profile.getPropertySetters()) {
            editor.putString(p.getKey(), p.isForce() ? p.getValue() : prefs.getString(p.getKey(), p.getValue()));
        }
        editor.commit();
    }

    @Override
    public boolean upgrade(Resource r, AndroidCommCarePlatform platform) {
        if (!super.upgrade(r, platform)) {
            return false;
        }

        InputStream profileStream = null;
        try {
            Reference local = ReferenceManager.instance().DeriveReference(localLocation);
            profileStream = local.getStream();
            //Create a parser with no side effects
            ProfileParser parser = new ProfileParser(profileStream, null, new DummyResourceTable(), null, Resource.RESOURCE_STATUS_INSTALLED, false);

            //Parse just the file (for the properties)
            Profile p = parser.parse();

            initProperties(p);
        } catch (InvalidReferenceException | IOException | InvalidStructureException |
                UnfullfilledRequirementsException | XmlPullParserException e) {
            e.printStackTrace();
            Logger.log(LogTypes.TYPE_RESOURCES, "Profile not available after upgrade: " + e.getMessage());
            return false;
        } finally {
            StreamsUtil.closeStream(profileStream);
        }

        return true;
    }

    @Override
    protected int customInstall(Resource r, Reference local, boolean upgrade, AndroidCommCarePlatform platform) throws IOException, UnresolvedResourceException {
        return Resource.RESOURCE_STATUS_LOCAL;
    }

    @Override
    public boolean requiresRuntimeInitialization() {
        return true;
    }
}
