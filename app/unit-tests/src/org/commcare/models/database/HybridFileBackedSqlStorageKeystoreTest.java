package org.commcare.models.database;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.commcare.CommCareApplication;
import org.commcare.CommCareTestApplication;
import org.commcare.utils.MockAndroidKeyStoreProvider;
import org.javarosa.core.model.instance.FormInstance;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;

import java.io.File;

/**
 * Tests for HybridFileBackedSqlStorage using Android Keystore encryption.
 * Registers a mock Keystore provider so that generateLegacyKeyOrEmpty()
 * returns an empty key, triggering the Keystore encryption path.
 */
@Config(application = CommCareTestApplication.class)
@RunWith(AndroidJUnit4.class)
public class HybridFileBackedSqlStorageKeystoreTest {

    @Before
    public void setup() {
        MockAndroidKeyStoreProvider.registerProvider();
        UnencryptedHybridFileBackedSqlStorageMock.alwaysPutInFilesystem();
        HybridFileBackedSqlStorageMock.alwaysPutInFilesystem();

        StoreFixturesOnFilesystemTests.installAppWithFixtureData(this.getClass(), "odk_level_ipm_restore.xml");
    }

    @After
    public void tearDown() {
        MockAndroidKeyStoreProvider.deregisterProvider();
    }

    @Test
    public void testKeystoreEncryptedWriteAndRead() {
        HybridFileBackedSqlStorage<FormInstance> userFixtureStorage =
                CommCareApplication.instance().getFileBackedUserStorage(
                        HybridFileBackedSqlStorage.FIXTURE_STORAGE_TABLE_NAME, FormInstance.class);

        FormInstance form = userFixtureStorage.getRecordForValues(
                new String[]{FormInstance.META_ID},
                new String[]{"commtrack:programs"});

        String updatedName = "keystore_encrypted_fixture";
        form.setName(updatedName);
        userFixtureStorage.update(form.getID(), form);

        FormInstance readBack = userFixtureStorage.getRecordForValues(
                new String[]{FormInstance.META_ID},
                new String[]{"commtrack:programs"});
        Assert.assertEquals("Fixture should be readable after Keystore-encrypted write",
                updatedName, readBack.getName());
    }

    @Test
    public void testKeystoreEncryptedNewWrite() {
        HybridFileBackedSqlStorage<FormInstance> userFixtureStorage =
                CommCareApplication.instance().getFileBackedUserStorage(
                        HybridFileBackedSqlStorage.FIXTURE_STORAGE_TABLE_NAME, FormInstance.class);

        FormInstance form = userFixtureStorage.getRecordForValues(
                new String[]{FormInstance.META_ID},
                new String[]{"commtrack:programs"});
        form.setID(-1);
        form.initialize(null, "keystore-new-fixture");
        userFixtureStorage.write(form);

        FormInstance readBack = userFixtureStorage.getRecordForValues(
                new String[]{FormInstance.META_ID},
                new String[]{"keystore-new-fixture"});
        Assert.assertNotNull("New Keystore-encrypted fixture should be readable", readBack);
    }

    @Test
    public void testMoveKeystoreEncryptedFixtureFromFsToDbAndBack() {
        HybridFileBackedSqlStorageMock.alwaysPutInDatabase();

        HybridFileBackedSqlStorage<FormInstance> userFixtureStorage =
                CommCareApplication.instance().getFileBackedUserStorage(
                        HybridFileBackedSqlStorage.FIXTURE_STORAGE_TABLE_NAME, FormInstance.class);
        FormInstance form = userFixtureStorage.getRecordForValues(
                new String[]{FormInstance.META_ID},
                new String[]{"commtrack:programs"});

        File dbDir = userFixtureStorage.getDbDirForTesting();
        int fileCountBefore = dbDir.listFiles().length;

        // move fixture from filesystem to database
        String newName = "keystore_fixture_in_db";
        form.setName(newName);
        userFixtureStorage.update(form.getID(), form);

        // ensure the data can still be read
        form = userFixtureStorage.getRecordForValues(
                new String[]{FormInstance.META_ID},
                new String[]{"commtrack:programs"});
        Assert.assertEquals(newName, form.getName());

        // ensure the old file was removed
        HybridFileBackedSqlHelpers.removeOrphanedFiles(
                CommCareApplication.instance().getUserDbHandle());
        int fileCountAfter = dbDir.listFiles().length;
        Assert.assertEquals(fileCountBefore - 1, fileCountAfter);

        // move fixture back into filesystem (now with Keystore encryption)
        HybridFileBackedSqlStorageMock.alwaysPutInFilesystem();
        newName = "keystore_fixture_back_in_fs";
        form.setName(newName);
        userFixtureStorage.update(form.getID(), form);

        // ensure the data can still be read
        form = userFixtureStorage.getRecordForValues(
                new String[]{FormInstance.META_ID},
                new String[]{"commtrack:programs"});
        Assert.assertEquals(newName, form.getName());

        fileCountAfter = dbDir.listFiles().length;
        Assert.assertEquals(fileCountBefore, fileCountAfter);
    }
}