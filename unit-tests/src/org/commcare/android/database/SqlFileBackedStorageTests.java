package org.commcare.android.database;

import org.commcare.android.CommCareTestRunner;
import org.commcare.android.util.TestAppInstaller;
import org.commcare.android.util.TestUtils;
import org.commcare.dalvik.BuildConfig;
import org.commcare.dalvik.application.CommCareApplication;
import org.commcare.data.xml.DataModelPullParser;
import org.commcare.modern.database.DatabaseHelper;
import org.commcare.xml.AndroidTransactionParserFactory;
import org.javarosa.core.model.instance.FormInstance;
import org.javarosa.core.reference.ReferenceManager;
import org.javarosa.core.reference.ResourceReferenceFactory;
import org.javarosa.core.services.storage.IStorageIterator;
import org.javarosa.core.services.storage.IStorageUtilityIndexed;
import org.javarosa.core.util.externalizable.Externalizable;
import org.javarosa.xml.util.InvalidStructureException;
import org.javarosa.xml.util.UnfullfilledRequirementsException;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;
import org.xmlpull.v1.XmlPullParserException;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

/**
 * @author Phillip Mates (pmates@dimagi.com).
 */
@Config(application = org.commcare.dalvik.application.CommCareApplication.class,
        constants = BuildConfig.class)
@RunWith(CommCareTestRunner.class)
public class SqlFileBackedStorageTests {
    private AndroidSandbox sandbox;

    @Before
    public void setup() {
        // needed to resolve "jr://resource" type references
        ReferenceManager._().addReferenceFactory(new ResourceReferenceFactory());

        TestUtils.initializeStaticTestStorage();
        TestAppInstaller.setupPrototypeFactory();

        TestAppInstaller appTestInstaller =
                new TestAppInstaller("jr://resource/commcare-apps/archive_form_tests/profile.ccpr",
                        "test", "123");
        appTestInstaller.installAppAndLogin();

        sandbox = new AndroidSandbox(CommCareApplication._());

        try {
            parseIntoSandbox(this.getClass().getClassLoader().getResourceAsStream("ipm_restore.xml"), false);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * User level fixtures are encrypted. To do so, they are stored in encrypted files and the key is stored in the encrypted databse.
     * This test ensures that the file is actually encrypted by trying to deserialize the contents of a fixture file w/o decrypting the file first.
     */
    @Test
    public void testStoredEncrypted() {
        IStorageUtilityIndexed<FormInstance> userFixtureStorage = sandbox.getUserFixtureStorage();
        File dbDir = ((SqlFileBackedStorage<FormInstance>)userFixtureStorage).getDbDir();
        File[] serializedFixtureFiles = dbDir.listFiles();
        Assert.assertTrue(serializedFixtureFiles.length > 0);
        try {
            ((SqlFileBackedStorage<FormInstance>)userFixtureStorage).newObject(new FileInputStream(serializedFixtureFiles[0]));
        } catch (FileNotFoundException e) {
            Assert.fail("Unable to find db storage file that should exist");
        } catch (RuntimeException e) {
            // we expect to fail here because the stream wasn't decrypted
        } catch (Exception e) {
            Assert.fail("Should have failed with a runtime exception when trying to deserialize an encrypted object");
        }
    }

    /**
     * App level fixtures are stored un-encrypted. To do so, they are stored in plain-text files.
     * This test ensures that by trying to deserialize the contents of one of those files.
     */
    @Test
    public void testStoredUnencrypted() {
        IStorageUtilityIndexed<FormInstance> appFixtureStorage = sandbox.getAppFixtureStorage();
        File dbDir = ((SqlFileBackedStorage<FormInstance>)appFixtureStorage).getDbDir();
        File[] serializedFixtureFiles = dbDir.listFiles();
        Assert.assertTrue(serializedFixtureFiles.length > 0);
        try {
            ((SqlFileBackedStorage<FormInstance>)appFixtureStorage).newObject(new FileInputStream(serializedFixtureFiles[0]));
        } catch (Exception e) {
            Assert.fail("Should be able to deserialize an unencrypted object");
        }
    }

    @Test
    public void testRemoveDeletesFiles() {
    }

    @Test
    public void testAdd() {
        IStorageUtilityIndexed<FormInstance> userFixtureStorage = sandbox.getUserFixtureStorage();

        int entryCount = 0;
        for (IStorageIterator i = userFixtureStorage.iterate(); i.hasMore(); ) {
            int id = i.nextID();
            FormInstance fixture = (FormInstance)i.nextRecord();
            System.out.println(fixture.getName());
            entryCount++;
        }
        System.out.println(entryCount+ "");
    }

    private static void parseIntoSandbox(InputStream stream, boolean failfast)
            throws InvalidStructureException, IOException, UnfullfilledRequirementsException, XmlPullParserException {
        AndroidTransactionParserFactory factory = new AndroidTransactionParserFactory(CommCareApplication._().getApplicationContext(), null);
        DataModelPullParser parser = new DataModelPullParser(stream, factory, failfast, true);
        parser.parse();
    }
}
