package org.commcare.android.tests.queries;

import org.commcare.CommCareApplication;
import org.commcare.CommCareTestApplication;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.commcare.android.util.TestUtils;
import org.commcare.cases.query.QueryContext;
import org.commcare.cases.query.queryset.CurrentModelQuerySet;
import org.javarosa.core.model.condition.EvaluationContext;
import org.javarosa.core.model.instance.TreeReference;
import org.javarosa.core.model.trace.EvaluationTraceReporter;
import org.javarosa.core.model.trace.ReducingTraceReporter;
import org.javarosa.core.model.trace.AccumulatingReporter;
import org.javarosa.core.model.trace.TraceSerialization;
import org.javarosa.core.model.utils.InstrumentationUtils;
import org.javarosa.model.xform.XPathReference;
import org.javarosa.xpath.XPathParseTool;
import org.javarosa.xpath.expr.FunctionUtils;
import org.javarosa.xpath.expr.XPathExpression;
import org.javarosa.xpath.parser.XPathSyntaxException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.robolectric.annotation.Config;

import java.util.Collection;

import static junit.framework.Assert.assertEquals;
import static junit.framework.TestCase.assertTrue;

/**
 * General case query tests
 *
 * Note that this should be following the same @Parameterized testing patter we are using in
 * CommCare core, but can't because the ParameterizedRobolectricTestRunner can't be used with a
 * custom test runner (we need the custom runer to shadow sqlcipher and the encrpytion libs).
 *
 * If/when https://github.com/robolectric/robolectric/issues/2910 ever gets fixed, we should expand
 * many tests to be run with the parameterized runner.
 *
 * @author ctsims
 */
@Config(application = CommCareTestApplication.class)
@RunWith(AndroidJUnit4.class)
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

        EvaluationContext ec = TestUtils.getEvaluationContextWithoutSession();

        evaluate("count(instance('casedb')/casedb/case[@case_id = 'test_case_id'])", "1", ec);
        evaluate("instance('casedb')/casedb/case[@case_id = 'test_case_id']/case_name", "Test Case", ec);
        evaluate("instance('casedb')/casedb/case[@case_id = 'test_case_id']/case_name", "Test Case", ec);
        evaluate("instance('casedb')/casedb/case[@case_id = 'test_case_id']/test_value", "initial", ec);
        evaluate("instance('casedb')/casedb/case[@case_id = 'test_case_id']/missing_value", "", ec);

        evaluate("instance('casedb')/casedb/case[@state = 'state2']/case_name", "Test Case 2", ec);
        evaluate("instance('casedb')/casedb/case[@category = 'category1']/case_name", "Test Case", ec);
        evaluate("instance('casedb')/casedb/case[@state != 'state2']/case_name", "Test Case", ec);
        evaluate("instance('casedb')/casedb/case[@category != 'category1']/case_name", "Test Case 2", ec);
    }

    /**
     * Tests for basic common case index related queries
     */
    @Test
    public void testCaseIndexQueries() {
        TestUtils.processResourceTransaction("/inputs/case_create.xml");
        TestUtils.processResourceTransaction("/inputs/case_create_and_index.xml");

        EvaluationContext ec = TestUtils.getEvaluationContextWithoutSession();

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
        EvaluationContext ec = TestUtils.getEvaluationContextWithoutSession();

        evaluate("join(',',instance('casedb')/casedb/case[index/parent = 'test_case_parent']/@case_id)", "child_one,child_two,child_three", ec);
        evaluate("join(',',instance('casedb')/casedb/case[index/parent = 'test_case_parent'][@case_id = 'child_two']/@case_id)", "child_two", ec);
        evaluate("join(',',instance('casedb')/casedb/case[index/parent = 'test_case_parent'][@case_id != 'child_two']/@case_id)", "child_one,child_three", ec);
    }


    @Test
    public void testIndexSetMemberOptimizations() {
        TestUtils.processResourceTransaction("/inputs/case_test_db_optimizations.xml");
        EvaluationContext ec = TestUtils.getEvaluationContextWithoutSession();

        evaluate("join(',',instance('casedb')/casedb/case[selected('test_case_parent', index/parent)]/@case_id)", "child_one,child_two,child_three", ec);
        evaluate("join(',',instance('casedb')/casedb/case[selected('test_case_parent test_case_parent_2', index/parent)]/@case_id)", "child_one,child_two,child_three", ec);
        evaluate("join(',',instance('casedb')/casedb/case[selected('test_case_parent_2 test_case_parent', index/parent)]/@case_id)", "child_one,child_two,child_three", ec);
        evaluate("join(',',instance('casedb')/casedb/case[selected('test_case_parent_2 test_case_parent_3', index/parent)]/@case_id)", "", ec);
        evaluate("join(',',instance('casedb')/casedb/case[selected('', index/parent)]/@case_id)", "", ec);
    }

    @Test
    public void testModelQueryLookupDerivations() {
        TestUtils.processResourceTransaction("/inputs/case_test_model_query_lookups.xml");
        EvaluationContext ec = TestUtils.getEvaluationContextWithoutSession();

        evaluate("join(',',instance('casedb')/casedb/case[@case_type='unit_test_child_child'][@status='open'][true() and " +
                "instance('casedb')/casedb/case[@case_id = instance('casedb')/casedb/case[@case_id=current()/index/parent]/index/parent]/test = 'true']/@case_id)", "child_ptwo_one_one,child_one_one", ec);

    }

    @Test
    public void testModelSelfReference() {
        TestUtils.processResourceTransaction("/inputs/case_parent_child_index_bulk.xml");
        EvaluationContext ec = TestUtils.getEvaluationContextWithoutSession();

        ReducingTraceReporter traceReporter = new ReducingTraceReporter(false);
        ec.setDebugModeOn(traceReporter);

        evaluate("join(',',instance('casedb')/casedb/case[@case_type='unit_test_child'][@status='open'][true() and " +
                "count(instance('casedb')/casedb/case[index/parent = instance('casedb')/casedb/case[@case_id=current()/@case_id]/index/parent][false = 'true']) > 0]/@case_id)", "", ec);

        String trace = InstrumentationUtils.collectAndClearTraces(
                traceReporter, "Child Lookups", TraceSerialization.TraceInfoType.FULL_PROFILE);
        assertTrue("Nested index query should trigger a reverse index optimization", trace.contains("reverse index"));
    }

    @Test
    public void testBulkQueryProcessingOutcomes() {
        TestUtils.processResourceTransaction("/inputs/case_test_db_optimizations.xml", true);
        EvaluationContext ec = TestUtils.getEvaluationContextWithoutSession();

        evaluate("join(',',instance('casedb')/casedb/case[selected('test_case_parent', index/parent)]/@case_id)", "child_one,child_two,child_three", ec);
        evaluate("join(',',instance('casedb')/casedb/case[selected('test_case_parent test_case_parent_2', index/parent)]/@case_id)", "child_one,child_two,child_three", ec);
        evaluate("join(',',instance('casedb')/casedb/case[selected('test_case_parent_2 test_case_parent', index/parent)]/@case_id)", "child_one,child_two,child_three", ec);
        evaluate("join(',',instance('casedb')/casedb/case[selected('test_case_parent_2 test_case_parent_3', index/parent)]/@case_id)", "", ec);
        evaluate("join(',',instance('casedb')/casedb/case[selected('', index/parent)]/@case_id)", "", ec);
    }


    @Test
    public void testModelQueryTransformFallback() throws XPathSyntaxException {
        TestUtils.processResourceTransaction("/inputs/case_test_model_query_fallbacks.xml");
        EvaluationContext ec = TestUtils.getEvaluationContextWithoutSession();

        evaluate("join(',',instance('casedb')/casedb/case[@case_type='unit_test_child_child'][@status='open'][" +
                "string(instance('casedb')/casedb/case[@case_id = instance('casedb')/casedb/case[@case_id=current()/index/parent]/index/parent]/case_name) = 'Valid']/@case_id)", "child_ptwo_one_one", ec);


        TreeReference unexpanded =
                XPathReference.getPathExpr("instance('casedb')/casedb/case[@case_type='unit_test_child_child'][@status='open']").getReference();


        Collection<TreeReference> references = ec.expandReference(unexpanded);

        QueryContext qc = ec.getCurrentQueryContext();
        qc.setHackyOriginalContextBody(new CurrentModelQuerySet(references));
        ec.setQueryContext(qc);

        //TODO: Set up a trace reporter which tracks events specifically, and test that this
        //full loop doesn't do model loads of any of the "Irrelevant" type cases
        XPathExpression name = XPathParseTool.parseXPath("string(instance('casedb')/casedb/case[@case_id = instance('casedb')/casedb/case[@case_id=current()/index/parent]/index/parent]/case_name)");
        for (TreeReference currentRef : references) {
            EvaluationContext subEc = new EvaluationContext(ec, currentRef);
            FunctionUtils.toString(name.eval(subEc));
        }
    }

    @Test
    public void testParentIndexTransformOptimization() throws XPathSyntaxException {
        TestUtils.processResourceTransaction("/inputs/case_parent_child_index_bulk.xml");
        EvaluationContext ec = TestUtils.getEvaluationContextWithoutSession();

        TreeReference unexpanded =
                XPathReference.getPathExpr("instance('casedb')/casedb/case[@case_type='unit_test_parent'][@status='open']").getReference();

        Collection<TreeReference> references = ec.expandReference(unexpanded);

        QueryContext qc = ec.getCurrentQueryContext().checkForDerivativeContextAndReturn(references.size());
        qc.setHackyOriginalContextBody(new CurrentModelQuerySet(references));
        ec.setQueryContext(qc);

        AccumulatingReporter traceReporter = new AccumulatingReporter();
        ec.setDebugModeOn(traceReporter);

        //TODO: Set up a trace reporter which tracks events specifically, and test that this
        //full loop doesn't do model loads of any of the "Irrelevant" type cases
        XPathExpression name = XPathParseTool.parseXPath("count(instance('casedb')/casedb/case[index/parent = current()/@case_id]/case_name)");
        for (TreeReference currentRef : references) {
            EvaluationContext subEc = new EvaluationContext(ec, currentRef);
            assertEquals(2.0, FunctionUtils.toInt(name.eval(subEc)));

        }
        String trace = InstrumentationUtils.collectAndClearTraces(
                traceReporter, "Child Lookups", TraceSerialization.TraceInfoType.FULL_PROFILE);

        assertEquals("Query performed incorrect single record reads", -1, trace.indexOf("Model [casedb]: Singular Load"));
        assertTrue("Query should trigger a reverse index transform", trace.indexOf("reverse index") != -1);
        System.out.println(trace);
    }

    public static void evaluate(String xpath, String expectedValue, EvaluationContext ec) {
        XPathExpression expr;
        try {
            expr = XPathParseTool.parseXPath(xpath);
            String result = FunctionUtils.toString(expr.eval(ec));
            assertEquals("XPath: " + xpath, expectedValue, result);
        } catch (XPathSyntaxException e) {
            TestUtils.wrapError(e, "XPath: " + xpath);
        }
    }

}
