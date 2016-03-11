package org.commcare.android.tests;

import org.commcare.suite.model.graph.Graph;
import org.commcare.suite.model.graph.XYSeries;
import org.javarosa.core.model.instance.TreeReference;

import org.javarosa.core.model.data.IntegerData;
import org.javarosa.core.model.data.StringData;
import org.javarosa.core.model.condition.EvaluationContext;
import org.javarosa.core.model.instance.FormInstance;

import org.commcare.CommCareApplication;
import org.commcare.android.CommCareTestRunner;
import org.commcare.dalvik.BuildConfig;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;

import java.util.Vector;


@Config(application = CommCareApplication.class,
        constants = BuildConfig.class)
@RunWith(CommCareTestRunner.class)
public class GraphTest extends XPathEvalTest {

    @Test
    private void testSeriesNodeSetExpansion() {
        EvaluationContext ec = getFunctionHandlers();
        FormInstance instance = createTestInstance();

        addDataRef(instance, "/data/flag", new IntegerData(2));
        addDataRef(instance, "/data/three[1]", new StringData("alpha"));
        addDataRef(instance, "/data/three[2]", new StringData("beta"));
        addDataRef(instance, "/data/three[3]", new StringData("delta"));
        addDataRef(instance, "/data/two[0]", new StringData("omega"));
        addDataRef(instance, "/data/two[1]", new StringData("psi"));

        XYSeries pathSeries = new XYSeries("/data/two");
        Vector<TreeReference> pathNodes = Graph.expandNodeSet(pathSeries, ec);
        Assert.assertEquals(pathNodes.length, 2);

        XYSeries expressionSeries = new XYSeries("if(/data/flag mod 2 = 1, /data/three, /data/two)");
        Vector<TreeReference> expressionNodes = Graph.expandNodeSet(expressionSeries, ec);
        Assert.assertEquals(expressionNodes.length(), 3);
    }

}