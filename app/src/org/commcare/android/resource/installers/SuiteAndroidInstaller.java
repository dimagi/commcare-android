package org.commcare.android.resource.installers;

import android.util.Log;

import org.commcare.resources.model.MissingMediaException;
import org.commcare.resources.model.Resource;
import org.commcare.resources.model.ResourceInitializationException;
import org.commcare.resources.model.ResourceLocation;
import org.commcare.resources.model.ResourceTable;
import org.commcare.resources.model.UnresolvedResourceException;
import org.commcare.suite.model.Entry;
import org.commcare.suite.model.Menu;
import org.commcare.suite.model.Suite;
import org.commcare.utils.AndroidCommCarePlatform;
import org.commcare.utils.DummyResourceTable;
import org.commcare.utils.FileUtil;
import org.commcare.xml.AndroidSuiteParser;
import org.commcare.xml.SuiteParser;
import org.javarosa.core.reference.InvalidReferenceException;
import org.javarosa.core.reference.Reference;
import org.javarosa.core.reference.ReferenceManager;
import org.javarosa.core.services.Logger;
import org.javarosa.xml.util.InvalidStructureException;
import org.javarosa.xml.util.UnfullfilledRequirementsException;
import org.javarosa.xpath.XPathException;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Vector;

/**
 * @author ctsims
 */
public class SuiteAndroidInstaller extends FileSystemInstaller {
    private static final String TAG = SuiteAndroidInstaller.class.getSimpleName();

    @SuppressWarnings("unused")
    public SuiteAndroidInstaller() {
        // for externalization
    }

    public SuiteAndroidInstaller(String localDestination, String upgradeDestination) {
        super(localDestination, upgradeDestination);
    }

    @Override
    public boolean initialize(final AndroidCommCarePlatform instance, boolean isUpgrade) throws ResourceInitializationException {
        try {
            if (localLocation == null) {
                throw new ResourceInitializationException("The suite file's location is null!");
            }
            Reference local = ReferenceManager._().DeriveReference(localLocation);

            SuiteParser parser;
            if (isUpgrade) {
                parser = AndroidSuiteParser.buildUpgradeParser(local.getStream(), instance.getGlobalResourceTable(), instance.getFixtureStorage());
            } else {
                parser = AndroidSuiteParser.buildInitParser(local.getStream(), instance.getGlobalResourceTable(), instance.getFixtureStorage());
            }

            Suite s = parser.parse();

            instance.registerSuite(s);

            return true;
        } catch (InvalidStructureException | InvalidReferenceException
                | IOException | XmlPullParserException
                | UnfullfilledRequirementsException e) {
            e.printStackTrace();
        }

        return false;
    }

    @Override
    public boolean install(Resource r, ResourceLocation location, Reference ref, ResourceTable table, final AndroidCommCarePlatform instance, boolean upgrade) throws UnresolvedResourceException, UnfullfilledRequirementsException {
        //First, make sure all the file stuff is managed.
        super.install(r, location, ref, table, instance, upgrade);

        try {
            Reference local = ReferenceManager._().DeriveReference(localLocation);

            AndroidSuiteParser.buildInstallParser(local.getStream(), table, r.getRecordGuid(), instance.getFixtureStorage()).parse();

            table.commitCompoundResource(r, upgrade ? Resource.RESOURCE_STATUS_UPGRADE : Resource.RESOURCE_STATUS_INSTALLED);
            return true;
        } catch (XmlPullParserException | InvalidStructureException
                | InvalidReferenceException | IOException
                | XPathException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        return false;
    }

    @Override
    protected int customInstall(Resource r, Reference local, boolean upgrade) throws IOException, UnresolvedResourceException {
        return Resource.RESOURCE_STATUS_LOCAL;
    }

    @Override
    public boolean requiresRuntimeInitialization() {
        return true;
    }

    @Override
    public boolean verifyInstallation(Resource r, Vector<MissingMediaException> problems) {
        try {
            Reference local = ReferenceManager._().DeriveReference(localLocation);
            Suite mSuite = AndroidSuiteParser.buildVerifyParser(local.getStream(), new DummyResourceTable()).parse();
            Hashtable<String, Entry> mHashtable = mSuite.getEntries();
            for (Enumeration en = mHashtable.keys(); en.hasMoreElements(); ) {
                String key = (String)en.nextElement();
                Entry mEntry = mHashtable.get(key);

                FileUtil.checkReferenceURI(r, mEntry.getAudioURI(), problems);
                FileUtil.checkReferenceURI(r, mEntry.getImageURI(), problems);
            }
            Vector<Menu> menus = mSuite.getMenus();
            Enumeration e = menus.elements();

            while (e.hasMoreElements()) {
                Menu mMenu = (Menu)e.nextElement();

                FileUtil.checkReferenceURI(r, mMenu.getAudioURI(), problems);
                FileUtil.checkReferenceURI(r, mMenu.getImageURI(), problems);
            }
        } catch (Exception e) {
            Logger.log("e", "suite validation failed with: " + e.getMessage());
            Log.d(TAG, "Suite validation failed");
            e.printStackTrace();
        }

        return problems.size() != 0;
    }
}
