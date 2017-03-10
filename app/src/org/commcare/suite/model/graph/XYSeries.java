package org.commcare.suite.model.graph;

import org.commcare.suite.model.Text;
import org.javarosa.core.model.condition.EvaluationContext;
import org.javarosa.core.util.externalizable.DeserializationException;
import org.javarosa.core.util.externalizable.ExtUtil;
import org.javarosa.core.util.externalizable.ExtWrapList;
import org.javarosa.core.util.externalizable.ExtWrapMap;
import org.javarosa.core.util.externalizable.Externalizable;
import org.javarosa.core.util.externalizable.PrototypeFactory;
import org.javarosa.xpath.XPathParseTool;
import org.javarosa.xpath.expr.XPathExpression;
import org.javarosa.xpath.parser.XPathSyntaxException;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Vector;

/**
 * Single series (line) on an xy graph.
 *
 * @author jschweers
 */
public class XYSeries implements Externalizable, Configurable {
    private String mNodeSet;
    private Hashtable<String, Text> mConfiguration;

    // Some configurations need to be evaluated for each point (at the moment, only "bar color"
    // We store the Text paths here and they will be evaluated each time a Graph is instantiated
    // Once instantiated, the evaluated values are stored in mConfiguration for usage.
    // When a graph is displayed again, the mConfiguration values will be wiped and replaced.
    private Hashtable<String, Text> mPointConfiguration;

    private String mX;
    private String mY;

    private XPathExpression mXParse;
    private XPathExpression mYParse;

    /*
     * Deserialization only!
     */
    public XYSeries() {

    }

    public XYSeries(String nodeSet) {
        mNodeSet = nodeSet;
        mConfiguration = new Hashtable<>();
        mPointConfiguration = new Hashtable<>();
        mPointConfiguration.put("bar-color", new Text());
    }

    public String getNodeSet() {
        return mNodeSet;
    }

    public String getX() {
        return mX;
    }

    public void setX(String x) {
        mX = x;
        mXParse = null;
    }

    public String getY() {
        return mY;
    }

    public void setY(String y) {
        mY = y;
        mYParse = null;
    }

    public void removeConfiguration(String key) {
        mConfiguration.remove(key);
    }

    @Override
    public void setConfiguration(String key, Text value) {
        if (mPointConfiguration.keySet().contains(key)) {
            mPointConfiguration.remove(key);
            mPointConfiguration.put(key, value);
        } else {
            mConfiguration.put(key, value);
        }
    }

    @Override
    public Text getConfiguration(String key) {
        return mConfiguration.get(key);
    }

    public void setExpandedConfiguration(String key, Text value) {
        mConfiguration.put(key, value);
    }

    public Enumeration getPointConfigurationKeys() {
        return mPointConfiguration.keys();
    }

    public Text getPointConfiguration(String key) {
        return mPointConfiguration.get(key);
    }

    @Override
    public Enumeration getConfigurationKeys() {
        return mConfiguration.keys();
    }

    @Override
    public void readExternal(DataInputStream in, PrototypeFactory pf)
            throws IOException, DeserializationException {
        mX = ExtUtil.readString(in);
        mY = ExtUtil.readString(in);
        mNodeSet = ExtUtil.readString(in);
        mConfiguration = (Hashtable<String, Text>)ExtUtil.read(in, new ExtWrapMap(String.class, Text.class), pf);
        mPointConfiguration =  (Hashtable<String, Text>)ExtUtil.read(in, new ExtWrapMap(String.class, Text.class), pf);
    }

    @Override
    public void writeExternal(DataOutputStream out) throws IOException {
        ExtUtil.writeString(out, mX);
        ExtUtil.writeString(out, mY);
        ExtUtil.writeString(out, mNodeSet);
        ExtUtil.write(out, new ExtWrapMap(mConfiguration));
        ExtUtil.write(out, new ExtWrapMap(mPointConfiguration));
    }

    /*
     * Parse all not-yet-parsed functions in this object.
     */
    protected void parse() throws XPathSyntaxException {
        if (mXParse == null) {
            mXParse = parse(mX);
        }
        if (mYParse == null) {
            mYParse = parse(mY);
        }
    }

    /*
     * Helper function to parse a single piece of XPath.
     */
    protected XPathExpression parse(String function) throws XPathSyntaxException {
        if (function == null) {
            return null;
        }
        return XPathParseTool.parseXPath("string(" + function + ")");
    }

    /*
     * Get the actual x value within a given EvaluationContext.
     */
    public String evaluateX(EvaluationContext context) throws XPathSyntaxException {
        parse();
        return evaluateExpression(mXParse, context);
    }

    /*
     * Get the actual y value within a given EvaluationContext.
     */
    public String evaluateY(EvaluationContext context) throws XPathSyntaxException {
        parse();
        return evaluateExpression(mYParse, context);
    }

    /*
     * Helper for evaluateX and evaluateY.
     */
    protected String evaluateExpression(XPathExpression expression, EvaluationContext context) {
        if (expression != null) {
            String value = (String)expression.eval(context.getMainInstance(), context);
            if (value.length() > 0) {
                return value;
            }
        }
        return null;
    }
}
