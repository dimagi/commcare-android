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

    @Test
    public void loadFlatFixtureTest() throws XPathSyntaxException {
        AndroidSandbox sandbox = StoreFixturesOnFilesystemTests.installAppWithFixtureData(this.getClass(), "flat_fixture_restore.xml");

        EvaluationContext evalContext =
                MockDataUtils.buildContextWithInstance(sandbox, "flat:commtrack:products", "jr://fixture/flat:commtrack:products");

        assertTrue(CaseTestUtils.xpathEvalAndCompare(evalContext,
                        "count(instance('flat:commtrack:products')/products/product)",
                        11.0));

        // check that the '@id' attribute and the 'id' element are treated differently
        assertTrue(CaseTestUtils.xpathEvalAndCompare(evalContext,
                "count(instance('flat:commtrack:products')/products/product[@id = 'f895be4959f9a8a66f57c340aac461b4']/name)",
                "Collier"));

        assertTrue(CaseTestUtils.xpathEvalAndCompare(evalContext,
                "count(instance('flat:commtrack:products')/products/product[id = '31ab899368d38c2d0207fe80c00fc3f3']/name)",
                "Collier"));
    }

    @Test
    public void loadInvalidFlatFixtureTest() throws XPathSyntaxException {
        AndroidSandbox sandbox = StoreFixturesOnFilesystemTests.installAppWithFixtureData(this.getClass(), "invalid_flat_fixture_restore.xml");

        String tableName = StorageBackedModel.STORAGE_KEY_PREFIX + TableBuilder.cleanTableName("flat:commtrack:products");
        SqlStorage<StorageBackedModel> storage = CommCareApplication.instance().getUserStorage(tableName, StorageBackedModel.class);

        EvaluationContext evalContext =
                MockDataUtils.buildContextWithInstance(sandbox, "flat:commtrack:products", "jr://fixture/flat:commtrack:products");

        String expr = "count(instance('flat:commtrack:products')/products/product)";
        assertTrue(CaseTestUtils.xpathEvalAndCompare(evalContext,
                expr,
                11.0));
    }
}
