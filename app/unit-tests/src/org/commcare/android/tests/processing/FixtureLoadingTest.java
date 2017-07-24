package org.commcare.android.tests.processing;

import junit.framework.Assert;

import org.commcare.CommCareApplication;
import org.commcare.CommCareTestApplication;
import org.commcare.android.CommCareTestRunner;
import org.commcare.models.database.HybridFileBackedSqlStorage;
import org.commcare.models.database.StoreFixturesOnFilesystemTests;
import org.javarosa.core.model.instance.FormInstance;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;

import java.util.NoSuchElementException;

/**
 * @author Phillip Mates (pmates@dimagi.com).
 */
@Config(application = CommCareTestApplication.class)
@RunWith(CommCareTestRunner.class)
public class FixtureLoadingTest {

    @Before
    public void setup() {
        StoreFixturesOnFilesystemTests.installAppWithFixtureData(this.getClass(),
                "odk_level_ipm_restore_with_empty_fixture.xml");
    }

    /**
     * Ensure parsing doesn't stop at empty fixtures, but continues on,
     * committing subsequent fixtures
     */
    @Test
    public void testEmptyFixtureFollowedByNormalFixture() {
        HybridFileBackedSqlStorage<FormInstance> userFixtureStorage =
                CommCareApplication.instance().getFileBackedUserStorage(
                        HybridFileBackedSqlStorage.FIXTURE_STORAGE_TABLE_NAME,
                        FormInstance.class);
        boolean didntFindEmptyFixture = false;
        try {
            userFixtureStorage.getRecordForValues(new String[]{FormInstance.META_ID},
                    new String[]{"commtrack:locations"});
        } catch (NoSuchElementException e) {
            didntFindEmptyFixture = true;
        }
        Assert.assertTrue(didntFindEmptyFixture);

        userFixtureStorage.getRecordForValues(new String[]{FormInstance.META_ID},
                new String[]{"commtrack:programs"});
    }
}
