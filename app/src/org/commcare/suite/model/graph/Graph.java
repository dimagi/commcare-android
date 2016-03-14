package org.commcare.suite.model.graph;

import org.commcare.graph.model.AnnotationData;
import org.commcare.graph.model.BubblePointData;
import org.commcare.graph.model.ConfigurableData;
import org.commcare.graph.model.GraphData;
import org.commcare.graph.model.SeriesData;
import org.commcare.graph.model.XYPointData;
import org.commcare.graph.util.GraphUtil;
import org.commcare.suite.model.DetailTemplate;
import org.commcare.suite.model.Text;
import org.javarosa.core.model.condition.EvaluationContext;
import org.javarosa.core.model.instance.TreeReference;
import org.javarosa.core.util.externalizable.DeserializationException;
import org.javarosa.core.util.externalizable.ExtUtil;
import org.javarosa.core.util.externalizable.ExtWrapList;
import org.javarosa.core.util.externalizable.ExtWrapListPoly;
import org.javarosa.core.util.externalizable.ExtWrapMap;
import org.javarosa.core.util.externalizable.Externalizable;
import org.javarosa.core.util.externalizable.PrototypeFactory;
import org.javarosa.model.xform.XPathReference;
import org.javarosa.xpath.XPathParseTool;
import org.javarosa.xpath.XPathTypeMismatchException;
import org.javarosa.xpath.expr.XPathExpression;
import org.javarosa.xpath.parser.XPathSyntaxException;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Vector;

/**
 * Defines a graph: type, set of series, set of text annotations, and key-value-based configuration.
 *
 * @author jschweers
 */
public class Graph implements Externalizable, DetailTemplate, Configurable {
    private String mType;
    private Vector<XYSeries> mSeries;
    private Hashtable<String, Text> mConfiguration;
    private Vector<Annotation> mAnnotations;

    public Graph() {
        mSeries = new Vector<>();
        mConfiguration = new Hashtable<>();
        mAnnotations = new Vector<>();
    }

    public String getType() {
        return mType;
    }

    public void setType(String type) {
        mType = type;
    }

    public void addSeries(XYSeries s) {
        mSeries.addElement(s);
    }

    public void addAnnotation(Annotation a) {
        mAnnotations.addElement(a);
    }

    public Text getConfiguration(String key) {
        return mConfiguration.get(key);
    }

    public void setConfiguration(String key, Text value) {
        mConfiguration.put(key, value);
    }

    public Enumeration getConfigurationKeys() {
        return mConfiguration.keys();
    }

    /*
     * (non-Javadoc)
     * @see org.javarosa.core.util.externalizable.Externalizable#readExternal(java.io.DataInputStream, org.javarosa.core.util.externalizable.PrototypeFactory)
     */
    public void readExternal(DataInputStream in, PrototypeFactory pf) throws IOException, DeserializationException {
        mType = ExtUtil.readString(in);
        mConfiguration = (Hashtable<String, Text>)ExtUtil.read(in, new ExtWrapMap(String.class, Text.class), pf);
        mSeries = (Vector<XYSeries>)ExtUtil.read(in, new ExtWrapListPoly(), pf);
        mAnnotations = (Vector<Annotation>)ExtUtil.read(in, new ExtWrapList(Annotation.class), pf);
    }

    /*
     * (non-Javadoc)
     * @see org.javarosa.core.util.externalizable.Externalizable#writeExternal(java.io.DataOutputStream)
     */
    public void writeExternal(DataOutputStream out) throws IOException {
        ExtUtil.writeString(out, mType);
        ExtUtil.write(out, new ExtWrapMap(mConfiguration));
        ExtUtil.write(out, new ExtWrapListPoly(mSeries));
        ExtUtil.write(out, new ExtWrapList(mAnnotations));
    }

    /*
     * (non-Javadoc)
     * @see org.commcare.suite.model.DetailTemplate#evaluate(org.javarosa.core.model.condition.EvaluationContext)
     */
    public GraphData evaluate(EvaluationContext context) {
        GraphData data = new GraphData();
        data.setType(mType);
        evaluateConfiguration(this, data, context);
        evaluateSeries(data, context);
        evaluateAnnotations(data, context);
        return data;
    }

    /*
     * Helper for evaluate. Looks at annotations only.
     */
    private void evaluateAnnotations(GraphData graphData, EvaluationContext context) {
        for (Annotation a : mAnnotations) {
            graphData.addAnnotation(new AnnotationData(
                    a.getX().evaluate(context),
                    a.getY().evaluate(context),
                    a.getAnnotation().evaluate(context)
            ));
        }
    }

    /*
     * Helper for evaluate. Looks at configuration only.
     */
    private void evaluateConfiguration(Configurable template, ConfigurableData data, EvaluationContext context) {
        Enumeration e = template.getConfigurationKeys();
        Vector<String> nonvariables = new Vector<>();
        String prefix = "var-";
        while (e.hasMoreElements()) {
            String key = (String)e.nextElement();
            if (key.startsWith(prefix)) {
                String value = template.getConfiguration(key).evaluate(context);
                context.setVariable(key.substring(prefix.length()), value);
            } else {
                nonvariables.addElement(key);
            }
        }
        for (String key : nonvariables) {
            String value = template.getConfiguration(key).evaluate(context);
            data.setConfiguration(key, value);
        }
    }

    /*
     * Helper for evaluate. Looks at all series.
     */
    private void evaluateSeries(GraphData graphData, EvaluationContext context) {
        try {
            for (XYSeries s : mSeries) {
                Hashtable<String, Text> pointConfiguration = new Hashtable<>();
                for (Enumeration e = s.getPointConfigurationKeys(); e.hasMoreElements();) {
                    String key = (String) e.nextElement();
                    Text value = s.getConfiguration(key);
                    if (value != null) {
                        pointConfiguration.put(key, value);
                    }
                }

                SeriesData seriesData = new SeriesData();
                EvaluationContext seriesContext = new EvaluationContext(context, context.getContextRef());

                Vector<TreeReference> refList = expandNodeSet(s, context);
                Hashtable<String, Vector<String>> expandedConfiguration = new Hashtable();
                for (Enumeration e = pointConfiguration.keys(); e.hasMoreElements();) {
                    expandedConfiguration.put((String) e.nextElement(), new Vector<String>());
                }

                for (TreeReference ref : refList) {
                    EvaluationContext refContext = new EvaluationContext(seriesContext, ref);
                    for (Enumeration e = pointConfiguration.keys(); e.hasMoreElements();) {
                        String key = (String) e.nextElement();
                        String value = pointConfiguration.get(key).evaluate(refContext);
                        expandedConfiguration.get(key).addElement(value);
                    }
                    String x = s.evaluateX(refContext);
                    String y = s.evaluateY(refContext);
                    if (x != null && y != null) {
                        if (graphData.getType().equals(GraphUtil.TYPE_BUBBLE)) {
                            String radius = ((BubbleSeries)s).evaluateRadius(refContext);
                            seriesData.addPoint(new BubblePointData(x, y, radius));
                        } else {
                            seriesData.addPoint(new XYPointData(x, y));
                        }
                    }
                }
                graphData.addSeries(seriesData);

                for (Enumeration e = expandedConfiguration.keys(); e.hasMoreElements();) {
                    String key = (String) e.nextElement();
                    StringBuffer json = new StringBuffer();
                    for (String pointValue : expandedConfiguration.get(key)) {
                        json.append(",'");
                        json.append(pointValue);
                        json.append("'");
                    }
                    if (json.length() > 0) {
                        json.deleteCharAt(0);
                    }
                    json.insert(0, "[");
                    json.append("]");
                    Text value = Text.PlainText(json.toString());
                    s.setExpandedConfiguration(key, value);
                }

                // Handle configuration after data, since data processing may update configuration
                evaluateConfiguration(s, seriesData, seriesContext);
                // Guess at names for series, if they weren't provided
                if (seriesData.getConfiguration("name") == null) {
                    seriesData.setConfiguration("name", s.getY());
                }
                if (seriesData.getConfiguration("x-name") == null) {
                    seriesData.setConfiguration("x-name", s.getX());
                }
            }
        } catch (XPathSyntaxException e) {
            e.printStackTrace();
        }
    }

    public static Vector<TreeReference> expandNodeSet(XYSeries series, EvaluationContext context) throws XPathSyntaxException {
        try {
            // Attempt to evaluate the nodeSet, which will succeed if this is just a path expression
            // (e.g., instance('casedb')/casedb/case[@case_type='point'][@status='open'][index/parent=current()/@case_id])
            return context.expandReference(XPathReference.getPathExpr(series.getNodeSet()).getReference());
        } catch (XPathTypeMismatchException e) {
            // If that fails, try treating the nodeSet as a more complex expression that itself returns a path
            // (e.g., if(true, "instance('item-list:rows1')/rows1/point", "instance('item-list:rows2')/rows2/point" ))
            XPathExpression xpe = XPathParseTool.parseXPath(series.getNodeSet());
            String nodeSet = (String) xpe.eval(context);
            return context.expandReference(XPathReference.getPathExpr(nodeSet).getReference());
        }

    }
}
