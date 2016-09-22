package org.commcare.android.tests.queries;

import org.commcare.CommCareApplication;
import org.commcare.android.CommCareTestRunner;
import org.commcare.android.util.TestUtils;
import org.commcare.dalvik.BuildConfig;
import org.javarosa.core.model.condition.EvaluationContext;
import org.javarosa.xpath.XPathParseTool;
import org.javarosa.xpath.expr.XPathExpression;
import org.javarosa.xpath.expr.XPathFuncExpr;
import org.javarosa.xpath.parser.XPathSyntaxException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;

import static junit.framework.Assert.assertEquals;

/**
 * @author ctsims
 */
@Config(application = CommCareApplication.class,
        constants = BuildConfig.class)
@RunWith(CommCareTestRunner.class)
public class CaseDbQueryTest {

    @Before
    public void setupTests() {
        TestUtils.initializeStaticTestStorage();
    }

    /**
     * Tests for basic common case database queries
     */
    @Test
    public void testBasicCaseQueries() {
        TestUtils.processResourceTransaction("/inputs/case_create.xml");

        EvaluationContext ec = TestUtils.getInstanceBackedEvaluationContext();

        evaluate("count(instance('casedb')/casedb/case[@case_id = 'test_case_id'])", "1", ec);
        evaluate("instance('casedb')/casedb/case[@case_id = 'test_case_id']/case_name", "Test Case", ec);
        evaluate("instance('casedb')/casedb/case[@case_id = 'test_case_id']/case_name", "Test Case", ec);
        evaluate("instance('casedb')/casedb/case[@case_id = 'test_case_id']/test_value", "initial", ec);
        evaluate("instance('casedb')/casedb/case[@case_id = 'test_case_id']/missing_value", "", ec);
    }

    /**
     * Tests for basic common case index related queries
     */
    @Test
    public void testCaseIndexQueries() {
        TestUtils.processResourceTransaction("/inputs/case_create.xml");
        TestUtils.processResourceTransaction("/inputs/case_create_and_index.xml");

        EvaluationContext ec = TestUtils.getInstanceBackedEvaluationContext();

        evaluate("instance('casedb')/casedb/case[@case_id = 'test_case_id_child']/index/parent", "test_case_id", ec);
        evaluate("instance('casedb')/casedb/case[@case_id = 'test_case_id']/index/missing", "", ec);

        // TODO PLM: only tests how the case data instance is initialized using
        // the test framework (TestUtils.getInstanceBackedEvaluationContext).
        // We need to create robolectric tests that perform this evaluation
        // during form entry
        evaluate("instance('casedb')/casedb/case[@case_type = 'unit_test_child'][index/parent = 'test_case_id_2']/@case_id",
                "test_case_id_child_2", ec);
    }

    @Test
    public void testCaseOptimizationTriggers() {
        TestUtils.processResourceTransaction("/inputs/case_test_db_optimizations.xml");
        EvaluationContext ec = TestUtils.getInstanceBackedEvaluationContext();

        evaluate("join(',',instance('casedb')/casedb/case[index/parent = 'test_case_parent']/@case_id)", "child_one,child_two,child_three", ec);
        evaluate("join(',',instance('casedb')/casedb/case[index/parent = 'test_case_parent'][@case_id = 'child_two']/@case_id)", "child_two", ec);
        evaluate("join(',',instance('casedb')/casedb/case[index/parent = 'test_case_parent'][@case_id != 'child_two']/@case_id)", "child_one,child_three", ec);


    }

        public static void evaluate(String xpath, String expectedValue, EvaluationContext ec) {
        XPathExpression expr;
        try {
            expr = XPathParseTool.parseXPath(xpath);
            String result = XPathFuncExpr.toString(expr.eval(ec));
            assertEquals("XPath: " + xpath, expectedValue, result);
        } catch (XPathSyntaxException e) {
            TestUtils.wrapError(e, "XPath: " + xpath);
        }
    }

}
