package org.commcare.android.tests.database;

import org.commcare.CommCareTestApplication;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.commcare.android.util.TestUtils;
import org.commcare.models.database.IDatabase;
import org.javarosa.core.services.storage.IStorageUtilityIndexed;
import org.javarosa.core.storage.IndexedStorageUtilityTests;
import org.javarosa.core.storage.Shoe;
import org.junit.After;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;

/**
 * SqlStorage implementations for the base Storage Utility tests
 *
 * Created by ctsims on 9/22/2017.
 */

@Config(application = CommCareTestApplication.class)
@RunWith(AndroidJUnit4.class)
public class SqlStorageBaseTests extends IndexedStorageUtilityTests {

    private IDatabase db;

    @Override
    protected IStorageUtilityIndexed<Shoe> createStorageUtility() {
        TestUtils.initializeStaticTestStorage();
        db = TestUtils.getTestDb();
        return TestUtils.getStorage(db, "ShoeStorage", Shoe.class);
    }

    @After
    public void closeDb() {
        if (db != null) {
            db.close();
        }
    }

}
