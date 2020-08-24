package org.commcare.android.tests.queries;

import org.commcare.CommCareApplication;
import org.commcare.CommCareTestApplication;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.commcare.android.util.TestUtils;
import org.commcare.test.utilities.CaseTestUtils;
import org.javarosa.core.model.condition.EvaluationContext;
import org.javarosa.xpath.parser.XPathSyntaxException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertTrue;

/**
 * Ledger queries at the Android level. Pretty much identical to
 * 'LedgerAndCaseQueryTest' in commcare-core; duplicated here to
 * up code coverage of Android specific logic.
 *
 * @author Phillip Mates (pmates@dimagi.com)
 */
@Config(application = CommCareTestApplication.class)
@RunWith(AndroidJUnit4.class)
public class LedgerDbQueryTest {

    @Before
    public void setUp() throws Exception {
        TestUtils.initializeStaticTestStorage();

        TestUtils.processResourceTransaction("/create_case_for_ledger.xml");
        TestUtils.processResourceTransaction("/ledger_create_basic.xml");
    }

    @Test
    public void ledgerQueriesWithLedgerData() throws XPathSyntaxException {
        EvaluationContext evalContext = TestUtils.getEvaluationContextWithoutSession();

        // case id 'market_basket' exists, and ledger data has been attached it
        assertTrue(
                CaseTestUtils.xpathEvalAndCompare(evalContext,
                        "instance('ledger')/ledgerdb/ledger[@entity-id='market_basket']/section[@section-id='edible_stock']/entry[@id='rice']",
                        10.0));
        // Reference valid case but invalid section id
        assertTrue(
                CaseTestUtils.xpathEvalAndCompare(evalContext,
                        "instance('ledger')/ledgerdb/ledger[@entity-id='market_basket']/section[@section-id='non-existent-section']",
                        ""));
        // case id 'ocean_state_job_lot' doesn't exists, but the ledger data
        // corresponding to it does
        assertTrue(
                CaseTestUtils.xpathEvalAndCompare(evalContext,
                        "instance('ledger')/ledgerdb/ledger[@entity-id='ocean_state_job_lot']/section[@section-id='cleaning_stock']/entry[@id='soap']",
                        9.0));

        // checking a non-existent entity
        assertTrue(
                CaseTestUtils.xpathEvalAndCompare(evalContext,
                        "count(instance('ledger')/ledgerdb/ledger[@entity-id='doesnt_exist']/section[@section-id='cleaning_stock']/entry[@id='soap'])",
                        0.0));

    }
}
