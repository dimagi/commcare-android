package org.commcare.android.tests.queries;

import org.commcare.CommCareApplication;
import org.commcare.android.CommCareTestRunner;
import org.commcare.android.util.TestUtils;
import org.javarosa.core.model.condition.EvaluationContext;
import org.javarosa.core.model.instance.FormInstance;
import org.javarosa.core.model.instance.TreeElement;
import org.javarosa.xpath.XPathParseTool;
import org.javarosa.xpath.expr.XPathExpression;
import org.javarosa.xpath.parser.XPathSyntaxException;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;

/**
 * Created by amstone326 on 2/2/18.
 */

@Config(application = CommCareApplication.class)
@RunWith(CommCareTestRunner.class)
public class ExpressionCacheTest {

    private FormInstance mainFormInstance;

    @Before
    public void setup() {
        mainFormInstance = new FormInstance(new TreeElement("data"));

        TestUtils.initializeStaticTestStorage();
        TestUtils.processResourceTransaction("/inputs/case_test_model_query_lookups.xml");
    }

    @Test
    public void testFasterWithCaching() {
        long timeWithoutCaching = time100Evaluations(false);
        long timeWithCaching = time100Evaluations(true);
        Assert.assertTrue("Evaluation time with caching should be shorter", timeWithCaching < timeWithoutCaching);
    }

    private long time100Evaluations(boolean enableCaching) {
        EvaluationContext ec = TestUtils.getEvaluationContextWithoutSession(mainFormInstance);
        if (enableCaching) {
            ec.enableCaching();
        }

        long start = System.currentTimeMillis();
        for (int i = 0; i < 100; i++) {
            evaluate("join(',',instance('casedb')/casedb/case[@case_type='unit_test_child_child']" +
                    "[@status='open'][true() and instance('casedb')/casedb/case[@case_id = " +
                    "instance('casedb')/casedb/case[@case_id=current()/index/parent]" +
                    "/index/parent]/test = 'true']/@case_id)",
                    ec);
        }
        return System.currentTimeMillis() - start;
    }

    private static void evaluate(String xpath, EvaluationContext ec) {
        XPathExpression expr;
        try {
            expr = XPathParseTool.parseXPath(xpath);
            expr.eval(ec);
        } catch (XPathSyntaxException e) {
            TestUtils.wrapError(e, "XPath: " + xpath);
        }
    }

    @Test
    public void testAccuracyWithCaching() {
        EvaluationContext ec = TestUtils.getEvaluationContextWithoutSession(mainFormInstance);
        ec.enableCaching();
        for (int i = 0; i < 3; i++) {
            CaseDbQueryTest.evaluate("join(',',instance('casedb')/casedb/case[@case_type='unit_test_child_child']" +
                            "[@status='open'][true() and instance('casedb')/casedb/case[@case_id = " +
                            "instance('casedb')/casedb/case[@case_id=current()/index/parent]/index/parent]/test = 'true']/@case_id)",
                    "child_ptwo_one_one,child_one_one",
                    ec);
        }
    }

}
