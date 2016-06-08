package org.commcare.android.resource.installers;

import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;

import org.commcare.CommCareApp;
import org.commcare.CommCareApplication;
import org.commcare.logging.AndroidLogger;
import org.commcare.android.database.global.models.ApplicationRecord;
import org.commcare.resources.model.Resource;
import org.commcare.resources.model.ResourceInitializationException;
import org.commcare.resources.model.ResourceLocation;
import org.commcare.resources.model.ResourceTable;
import org.commcare.resources.model.UnresolvedResourceException;
import org.commcare.suite.model.Profile;
import org.commcare.suite.model.PropertySetter;
import org.commcare.utils.AndroidCommCarePlatform;
import org.commcare.utils.DummyResourceTable;
import org.commcare.xml.CommCareElementParser;
import org.commcare.xml.ProfileParser;
import org.javarosa.core.reference.InvalidReferenceException;
import org.javarosa.core.reference.Reference;
import org.javarosa.core.reference.ReferenceManager;
import org.javarosa.core.services.Logger;
import org.javarosa.xml.util.InvalidStructureException;
import org.javarosa.xml.util.UnfullfilledRequirementsException;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.ArrayList;

/**
 * @author ctsims
 */
public class ProfileAndroidInstaller extends FileSystemInstaller {

    @SuppressWarnings("unused")
    public ProfileAndroidInstaller() {
        // For externalization
    }

    public ProfileAndroidInstaller(String localDestination, String upgradeDestination) {
        super(localDestination, upgradeDestination);
    }


    @Override
    public boolean initialize(AndroidCommCarePlatform instance) throws ResourceInitializationException {
        try {

            Reference local = ReferenceManager._().DeriveReference(localLocation);

            ProfileParser parser = new ProfileParser(local.getStream(), instance, instance.getGlobalResourceTable(), null,
                    Resource.RESOURCE_STATUS_INSTALLED, false);

            Profile p = parser.parse();
            instance.setProfile(p);

            return true;
        } catch (UnfullfilledRequirementsException | XmlPullParserException
                | InvalidStructureException | IOException
                | InvalidReferenceException e) {
            e.printStackTrace();
        }

        return false;
    }

    public boolean install(Resource r, ResourceLocation location, Reference ref,
                           ResourceTable table, AndroidCommCarePlatform instance, boolean upgrade)
            throws UnresolvedResourceException, UnfullfilledRequirementsException {
        //First, make sure all the file stuff is managed.
        super.install(r, location, ref, table, instance, upgrade);
        try {
            Reference local = ReferenceManager._().DeriveReference(localLocation);


            ProfileParser parser = new ProfileParser(local.getStream(), instance, table, r.getRecordGuid(),
                    Resource.RESOURCE_STATUS_UNINITIALIZED, false);

            Profile p = parser.parse();

            if (!upgrade) {
                initProperties(p);
                checkDuplicate(p);
            }

            table.commitProfileResource(r, upgrade ? Resource.RESOURCE_STATUS_UPGRADE : Resource.RESOURCE_STATUS_INSTALLED, p.getVersion());
            return true;
        } catch (XmlPullParserException | InvalidReferenceException | IOException e) {
            e.printStackTrace();
        } catch (InvalidStructureException e) {
            throw new UnresolvedResourceException(r, "Invalid content in the Profile Definition: " + e.getMessage(), true);
        }

        return false;
    }

    // Check that this app is not already installed on the phone
    private void checkDuplicate(Profile p) throws UnfullfilledRequirementsException {
        String newAppId = p.getUniqueId();
        ArrayList<ApplicationRecord> installedApps = CommCareApplication._().
                getInstalledAppRecords();
        for (ApplicationRecord record : installedApps) {
            if (record.getUniqueId().equals(newAppId)) {
                throw new UnfullfilledRequirementsException(
                        "The app you are trying to install already exists on this device",
                        CommCareElementParser.SEVERITY_PROMPT, true);
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
    public boolean upgrade(Resource r) {
        if (!super.upgrade(r)) {
            return false;
        }

        try {
            Reference local = ReferenceManager._().DeriveReference(localLocation);

            //Create a parser with no side effects
            ProfileParser parser = new ProfileParser(local.getStream(), null, new DummyResourceTable(), null, Resource.RESOURCE_STATUS_INSTALLED, false);

            //Parse just the file (for the properties)
            Profile p = parser.parse();

            initProperties(p);
        } catch (InvalidReferenceException e) {
            e.printStackTrace();
            Logger.log(AndroidLogger.TYPE_RESOURCES, "Profile not available after upgrade: " + e.getMessage());
            return false;
        } catch (IOException e) {
            Logger.log(AndroidLogger.TYPE_RESOURCES, "Profile not available after upgrade: " + e.getMessage());
            return false;
        } catch (InvalidStructureException e) {
            Logger.log(AndroidLogger.TYPE_RESOURCES, "Profile not available after upgrade: " + e.getMessage());
            return false;
        } catch (UnfullfilledRequirementsException e) {
            Logger.log(AndroidLogger.TYPE_RESOURCES, "Profile not available after upgrade: " + e.getMessage());
            return false;
        } catch (XmlPullParserException e) {
            Logger.log(AndroidLogger.TYPE_RESOURCES, "Profile not available after upgrade: " + e.getMessage());
            return false;
        }

        return true;
    }

    protected int customInstall(Resource r, Reference local, boolean upgrade) throws IOException, UnresolvedResourceException {
        return Resource.RESOURCE_STATUS_LOCAL;
    }

    @Override
    public boolean requiresRuntimeInitialization() {
        return true;
    }
}
