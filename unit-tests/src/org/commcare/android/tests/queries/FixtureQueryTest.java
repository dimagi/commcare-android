package org.commcare.android.tests.queries;

import org.commcare.CommCareTestApplication;
import org.commcare.android.CommCareTestRunner;
import org.commcare.android.util.TestUtils;
import org.commcare.dalvik.BuildConfig;
import org.commcare.models.database.StoreFixturesOnFilesystemTests;
import org.javarosa.core.model.condition.EvaluationContext;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;

/**
 * Created by ctsims on 8/31/2016.
 */
@Config(application = CommCareTestApplication.class,
        constants = BuildConfig.class)
@RunWith(CommCareTestRunner.class)
public class FixtureQueryTest {

    @Before
    public void setup() {
        StoreFixturesOnFilesystemTests.installAppWithFixtureData(this.getClass(),
                "odk_level_ipm_restore.xml");
    }

    @Test
    public void testEmptyFixtureFollowedByNormalFixture() {
        EvaluationContext ec = TestUtils.getProductFixtureEvaluationContext();

        CaseDbQueryTest.evaluate("count(instance('products')/products/product[@heterogenous_attribute = 'present'])",
                "2", ec);


    }


}
