package org.commcare.android.database;

import org.commcare.android.CommCareTestRunner;
import org.commcare.android.util.SessionUnavailableException;
import org.commcare.dalvik.BuildConfig;
import org.commcare.dalvik.application.CommCareApp;
import org.commcare.dalvik.application.CommCareApplication;
import org.javarosa.core.model.instance.FormInstance;
import org.javarosa.core.model.instance.FormInstanceWithFailures;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;

import java.io.File;

/**
 * Test hybrid storage update logic that moves object from db to fs, or vice-versa,
 * based on object update size
 *
 * @author Phillip Mates (pmates@dimagi.com).
 */
@Config(application = org.commcare.dalvik.application.CommCareTestApplication.class,
        constants = BuildConfig.class)
@RunWith(CommCareTestRunner.class)
public class HybridFileBackedSqlStorageTest {

    @Before
    public void setup() {
        UnencryptedHybridFileBackedSqlStorageMock.alwaysPutInFilesystem();
        HybridFileBackedSqlStorageMock.alwaysPutInFilesystem();

        StoreFixturesOnFilesystemTests.installAppWithFixtureData(this.getClass(), "odk_level_ipm_restore.xml");
    }

    /**
     * Write an object to the filesystem but fail before finializing the
     * transaction. Test that the file is marked as orphan
     */
    @Test
    public void atomicWriteTest() {
        HybridFileBackedSqlStorageMock.alwaysPutInFilesystem();

        HybridFileBackedSqlStorage<FormInstance> userFixtureStorage =
                CommCareApplication._().getFileBackedUserStorage("fixture", FormInstance.class);

        FormInstance form = userFixtureStorage.getRecordForValues(new String[]{FormInstance.META_ID},
                new String[]{"commtrack:programs"});
        form.setID(-1);

        // build a form instance that fails when setID is called, which happens
        // at the end of db write
        FormInstanceWithFailures failingForm = new FormInstanceWithFailures(form.getRoot());
        FormInstanceWithFailures.setFailOnIdSet(true);

        File dbDir = userFixtureStorage.getDbDirForTesting();
        int fileCountBefore = dbDir.listFiles().length;

        boolean didWriteFail = false;
        try {
            userFixtureStorage.write(failingForm);
        } catch (RuntimeException e) {
            didWriteFail = true;
        }
        Assert.assertTrue(didWriteFail);

        // check that the file from the failed write is around
        int fileCountAfter = dbDir.listFiles().length;
        Assert.assertTrue(fileCountAfter - fileCountBefore == 1);

        // check that the file is cleared with other orphan files
        clearOrphanedFiles();
        int fileCountAfterClear = dbDir.listFiles().length;
        Assert.assertTrue(fileCountBefore - fileCountAfterClear == 0);
    }

    @Test
    public void moveEncryptedFixtureFromFsToDbAndBack() {
        HybridFileBackedSqlStorageMock.alwaysPutInDatabase();

        HybridFileBackedSqlStorage<FormInstance> userFixtureStorage =
                CommCareApplication._().getFileBackedUserStorage("fixture", FormInstance.class);
        FormInstance form = userFixtureStorage.getRecordForValues(new String[]{FormInstance.META_ID},
                new String[]{"commtrack:programs"});

        File dbDir = userFixtureStorage.getDbDirForTesting();
        int fileCountBefore = dbDir.listFiles().length;

        // move fixture from filesystem to database
        String newName = "some_fixture_in_db";
        form.setName(newName);
        userFixtureStorage.update(form.getID(), form);

        // ensure the data can still be read
        form = userFixtureStorage.getRecordForValues(new String[]{FormInstance.META_ID},
                new String[]{"commtrack:programs"});
        Assert.assertEquals(newName, form.getName());

        // quick test coverage for reading multiple records that have serialized objects stored in db
        FormInstance form2 = userFixtureStorage.getRecordsForValues(new String[]{FormInstance.META_ID},
                new String[]{"commtrack:programs"}).firstElement();
        Assert.assertEquals(form2.getName(), form.getName());

        // ensure the old file was removed
        clearOrphanedFiles();
        int fileCountAfter = dbDir.listFiles().length;
        Assert.assertTrue(fileCountBefore - fileCountAfter == 1);

        // move fixture back into filesystem
        HybridFileBackedSqlStorageMock.alwaysPutInFilesystem();
        newName = "some_fixture_in_fs";
        form.setName(newName);
        userFixtureStorage.update(form.getID(), form);

        // ensure the data can still be read
        form = userFixtureStorage.getRecordForValues(new String[]{FormInstance.META_ID},
                new String[]{"commtrack:programs"});
        Assert.assertEquals(newName, form.getName());

        fileCountAfter = dbDir.listFiles().length;
        Assert.assertTrue(fileCountBefore == fileCountAfter);

        userFixtureStorage.remove(form.getID());
        fileCountAfter = dbDir.listFiles().length;
        Assert.assertTrue(fileCountBefore - fileCountAfter == 1);
    }

    @Test
    public void moveUnencryptedFixtureFromFsToDbAndBack() {
        UnencryptedHybridFileBackedSqlStorageMock.alwaysPutInDatabase();

        UnencryptedHybridFileBackedSqlStorage<FormInstance> appFixtureStorage =
                CommCareApplication._().getCurrentApp().getFileBackedStorage("fixture", FormInstance.class);
        FormInstance form = appFixtureStorage.getRecordForValues(new String[]{FormInstance.META_ID},
                new String[]{"user-groups"});

        File dbDir = appFixtureStorage.getDbDirForTesting();
        int fileCountBefore = dbDir.listFiles().length;

        // move fixture from filesystem to database
        String newName = "some_fixture_in_db";
        form.setName(newName);
        appFixtureStorage.update(form.getID(), form);

        // ensure the data can still be read
        form = appFixtureStorage.getRecordForValues(new String[]{FormInstance.META_ID},
                new String[]{"user-groups"});
        Assert.assertEquals(newName, form.getName());

        // ensure the old file was removed
        clearOrphanedUnencryptedFiles();
        int fileCountAfter = dbDir.listFiles().length;
        Assert.assertTrue(fileCountBefore - fileCountAfter == 1);

        // move fixture back into filesystem
        UnencryptedHybridFileBackedSqlStorageMock.alwaysPutInFilesystem();
        newName = "some_fixture_in_fs";
        form.setName(newName);
        appFixtureStorage.update(form.getID(), form);

        // ensure the data can still be read
        form = appFixtureStorage.getRecordForValues(new String[]{FormInstance.META_ID},
                new String[]{"user-groups"});
        Assert.assertEquals(newName, form.getName());

        fileCountAfter = dbDir.listFiles().length;
        Assert.assertTrue(fileCountBefore == fileCountAfter);

        appFixtureStorage.remove(form.getID());
        fileCountAfter = dbDir.listFiles().length;
        Assert.assertTrue(fileCountBefore - fileCountAfter == 1);
    }

    @Test
    public void testDbWriteAndUpdate() {
        HybridFileBackedSqlStorageMock.alwaysPutInDatabase();
        UnencryptedHybridFileBackedSqlStorageMock.alwaysPutInDatabase();

        // unencrypted write / update test
        UnencryptedHybridFileBackedSqlStorage<FormInstance> appFixtureStorage =
                CommCareApplication._().getCurrentApp().getFileBackedStorage("fixture", FormInstance.class);
        FormInstance appLevelFixture =
                appFixtureStorage.getRecordForValues(new String[]{FormInstance.META_ID},
                        new String[]{"user-groups"});

        // test write
        appLevelFixture.setID(-1);
        appLevelFixture.initialize(null, "new-user-groups");
        appFixtureStorage.write(appLevelFixture);

        // test read
        appFixtureStorage.read(appLevelFixture.getID());

        // test update
        String newName = "some_fixture";
        appLevelFixture.setName(newName);
        appFixtureStorage.update(appLevelFixture.getID(), appLevelFixture);

        // ensure the data can still be read
        appLevelFixture = appFixtureStorage.getRecordForValues(new String[]{FormInstance.META_ID},
                new String[]{"new-user-groups"});
        Assert.assertEquals(newName, appLevelFixture.getName());

        // encrypted write / update test
        HybridFileBackedSqlStorage<FormInstance> userFixtureStorage =
                CommCareApplication._().getFileBackedUserStorage("fixture", FormInstance.class);
        FormInstance userLevelFixture =
                userFixtureStorage.getRecordForValues(new String[]{FormInstance.META_ID},
                        new String[]{"commtrack:programs"});

        // test write
        userLevelFixture.setID(-1);
        userLevelFixture.initialize(null, "new-commtrack");
        userFixtureStorage.write(userLevelFixture);

        // test read
        userFixtureStorage.read(userLevelFixture.getID());

        // test update
        userLevelFixture.setName(newName);
        userFixtureStorage.update(userLevelFixture.getID(), userLevelFixture);

        // ensure the data can still be read
        userLevelFixture =
                userFixtureStorage.getRecordForValues(new String[]{FormInstance.META_ID},
                        new String[]{"new-commtrack"});
        Assert.assertEquals(newName, userLevelFixture.getName());
    }

    private static void clearOrphanedFiles() {
        try {
            HybridFileBackedSqlHelpers.removeOrphanedFiles(CommCareApplication._().getUserDbHandle());
        } catch (SessionUnavailableException e) {
            Assert.fail("unable to clear orphaned files");
        }
    }

    private static void clearOrphanedUnencryptedFiles() {
        try {
            HybridFileBackedSqlHelpers.removeOrphanedFiles(CommCareApp.getAppDatabaseForTesting());
        } catch (SessionUnavailableException e) {
            Assert.fail("unable to clear orphaned files");
        }
    }
}
