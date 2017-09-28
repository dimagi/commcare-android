package org.commcare.android.tests.processing;

import net.sqlcipher.database.SQLiteDatabase;

import org.commcare.CommCareTestApplication;
import org.commcare.android.CommCareTestRunner;
import org.commcare.models.database.AndroidSandbox;
import org.commcare.models.database.IndexedFixturePathUtils;
import org.commcare.models.database.StoreFixturesOnFilesystemTests;
import org.commcare.test.utilities.CaseTestUtils;
import org.commcare.util.mocks.MockDataUtils;
import org.javarosa.core.model.condition.EvaluationContext;
import org.javarosa.xpath.parser.XPathSyntaxException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.List;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.fail;
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

    /**
     * Test adding an index over the '@id' attribute, which isn't present in
     * all fixture entries (i.e. entries are non-homogenous)
     */
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

    @Test
    public void testIndexedFixturePathsTableSoundness() {
        AndroidSandbox sandbox = StoreFixturesOnFilesystemTests.installAppWithFixtureData(
                this.getClass(),
                "indexed_fixture_restore.xml");

        // Parse the same fixture a 2nd time so that IndexedFixturePathUtils.insertIndexedFixturePathBases()
        // has to handle receiving a row with a duplicate name
        try {
            StoreFixturesOnFilesystemTests.parseIntoSandbox(
                    this.getClass().getClassLoader().getResourceAsStream("indexed_fixture_restore.xml"),
                    false);
        } catch (Exception e) {
            e.printStackTrace();
        }

        SQLiteDatabase db = sandbox.getUserDb();
        List<String> allIndexedFixtures =
                IndexedFixturePathUtils.getAllIndexedFixtureNamesAsList(db);

        List<String> comparisonList = new ArrayList<>();
        for (String fixtureName : allIndexedFixtures) {
            if (comparisonList.contains(fixtureName)) {
                fail("INDEXED_FIXTURE_PATHS_TABLE contains duplicate entries");
            }
            comparisonList.add(fixtureName);
        }

        assertEquals(
                "An incorrect number of entries was parsed into the INDEXED_FIXTURE_PATHS_TABLE",
                1,
                allIndexedFixtures.size());
    }
}
