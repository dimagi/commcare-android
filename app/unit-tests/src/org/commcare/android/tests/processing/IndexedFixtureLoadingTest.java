package org.commcare.android.tests.processing;

import org.commcare.CommCareTestApplication;
import org.commcare.android.CommCareTestRunner;
import org.commcare.models.database.AndroidSandbox;
import org.commcare.models.database.StoreFixturesOnFilesystemTests;
import org.commcare.test.utilities.CaseTestUtils;
import org.commcare.util.mocks.MockDataUtils;
import org.javarosa.core.model.condition.EvaluationContext;
import org.javarosa.xpath.parser.XPathSyntaxException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertTrue;

/**
 * Processes and queries fixtures whose entries are broken up and stored in a
 * dedicated DB table that indexes certain entry elements and attributes
 *
 * @author Phillip Mates (pmates@dimagi.com).
 */
@Config(application = CommCareTestApplication.class)
@RunWith(CommCareTestRunner.class)
public class IndexedFixtureLoadingTest {

    @Test
    public void loadIndexedFixtureTest() throws XPathSyntaxException {
        AndroidSandbox sandbox =
                StoreFixturesOnFilesystemTests.installAppWithFixtureData(
                        this.getClass(),
                        "indexed_fixture_restore.xml");

        EvaluationContext evalContext =
                MockDataUtils.buildContextWithInstance(sandbox, "commtrack:products",
                        "jr://fixture/commtrack:products");

        assertTrue(CaseTestUtils.xpathEvalAndCompare(evalContext,
                        "count(instance('commtrack:products')/products/product)",
                        11.0));

        // check that the '@id' attribute and the 'id' element are treated differently
        assertTrue(CaseTestUtils.xpathEvalAndCompare(evalContext,
                "instance('commtrack:products')/products/product[@id = 'f895be4959f9a8a66f57c340aac461b4']/name",
                "Collier"));

        assertTrue(CaseTestUtils.xpathEvalAndCompare(evalContext,
                "instance('commtrack:products')/products/product[id = '12345']/name",
                "Collier"));
    }

    @Test
    public void loadIndexedFixtureWithNestedChildrenTest() throws XPathSyntaxException {
        AndroidSandbox sandbox =
                StoreFixturesOnFilesystemTests.installAppWithFixtureData(getClass(),
                        "indexed_fixture_with_nested_children_restore.xml");

        EvaluationContext evalContext =
                MockDataUtils.buildContextWithInstance(sandbox, "commtrack:products",
                        "jr://fixture/commtrack:products");

        assertTrue(CaseTestUtils.xpathEvalAndCompare(evalContext,
                "count(instance('commtrack:products')/products/product)",
                3.0));

        assertTrue(CaseTestUtils.xpathEvalAndCompare(evalContext,
                "instance('commtrack:products')/products/product[@id = 'f895be4959f9a8a66f57c340aac461b4']/extra_data/color",
                "vestigial sunrise"));
        assertTrue(CaseTestUtils.xpathEvalAndCompare(evalContext,
                "instance('commtrack:products')/products/product[@id = '31ab899368d38c2d0207fe80c00fa96c']/extra_data",
                ""));
    }

    @Test
    public void indexOverNonHomogeneousElementTest() throws XPathSyntaxException {
        AndroidSandbox sandbox =
                StoreFixturesOnFilesystemTests.installAppWithFixtureData(getClass(),
                        "indexed_fixture_with_index_over_nonhomo_entry.xml");

        EvaluationContext evalContext =
                MockDataUtils.buildContextWithInstance(sandbox, "commtrack:products",
                        "jr://fixture/commtrack:products");

        assertTrue(CaseTestUtils.xpathEvalAndCompare(evalContext,
                "count(instance('commtrack:products')/products/product)",
                3.0));

        assertTrue(CaseTestUtils.xpathEvalAndCompare(evalContext,
                "instance('commtrack:products')/products/product[@id = 'has-id']/name",
                "Normal ID"));
        assertTrue(CaseTestUtils.xpathEvalAndCompare(evalContext,
                "instance('commtrack:products')/products/product[@id = '']/name",
                "Empty ID"));
    }
}
