package org.commcare.android.tests.processing;

import org.commcare.CommCareApplication;
import org.commcare.CommCareTestApplication;
import org.commcare.android.CommCareTestRunner;
import org.commcare.cases.model.StorageBackedModel;
import org.commcare.models.database.AndroidSandbox;
import org.commcare.models.database.SqlStorage;
import org.commcare.models.database.StoreFixturesOnFilesystemTests;
import org.commcare.modern.database.TableBuilder;
import org.commcare.test.utilities.CaseTestUtils;
import org.commcare.util.mocks.MockDataUtils;
import org.javarosa.core.model.condition.EvaluationContext;
import org.javarosa.xpath.parser.XPathSyntaxException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertTrue;

/**
 * @author Phillip Mates (pmates@dimagi.com).
 */
@Config(application = CommCareTestApplication.class)
@RunWith(CommCareTestRunner.class)
public class FlatFixtureLoadingTest {
    private AndroidSandbox sandbox;
    @Before
    public void setup() {
        sandbox = StoreFixturesOnFilesystemTests.installAppWithFixtureData(this.getClass(), "flat_fixture_restore.xml");
    }

    @Test
    public void loadFlatFixtureTest() throws XPathSyntaxException {
        String tableName = StorageBackedModel.STORAGE_KEY + TableBuilder.cleanTableName("flat:commtrack:products");
        SqlStorage<StorageBackedModel> storage = CommCareApplication.instance().getUserStorage(tableName, StorageBackedModel.class);

        int count = storage.getNumRecords();
        System.out.println(count);

        EvaluationContext evalContext =
                MockDataUtils.buildContextWithInstance(sandbox, "flat:commtrack:products", "jr://fixture/flat:commtrack:products");

        String expr = "count(instance('flat:commtrack:products')/products/product)";
        assertTrue(CaseTestUtils.xpathEvalAndCompare(evalContext,
                        expr,
                        11.0));
    }
}
