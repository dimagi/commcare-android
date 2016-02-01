package org.commcare.suite.model.graph;

import org.commcare.suite.model.Text;
import org.javarosa.core.model.condition.EvaluationContext;
import org.javarosa.core.model.instance.TreeReference;
import org.javarosa.core.util.externalizable.DeserializationException;
import org.javarosa.core.util.externalizable.ExtUtil;
import org.javarosa.core.util.externalizable.ExtWrapList;
import org.javarosa.core.util.externalizable.ExtWrapMap;
import org.javarosa.core.util.externalizable.Externalizable;
import org.javarosa.core.util.externalizable.PrototypeFactory;
import org.javarosa.model.xform.XPathReference;
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
    private TreeReference mNodeSet;
    private Hashtable<String, Text> mConfiguration;

    // List of keys that configure individual points. For these keys, the Text stored in
    // mConfiguration is an XPath expression, which during evaluation will be applied to
    // each point in turn to produce a list of one value for each point. As the "expanded",
    // point-level values are set, keys will be removed from this list.
    private Vector<String> mPointConfiguration;

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
        mNodeSet = XPathReference.getPathExpr(nodeSet).getReference();
        mConfiguration = new Hashtable<String, Text>();
        mPointConfiguration = new Vector<String>();
        mPointConfiguration.addElement("bar-color");
    }

    public TreeReference getNodeSet() {
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

    public void setConfiguration(String key, Text value) {
        mConfiguration.put(key, value);
    }

    public void setExpandedConfiguration(String key, Text value) {
        mPointConfiguration.removeElement(key);
        setConfiguration(key, value);
    }

    public Enumeration getPointConfigurationKeys() {
        return mPointConfiguration.elements();
    }

    public Text getConfiguration(String key) {
        return mConfiguration.get(key);
    }

    public Enumeration getConfigurationKeys() {
        return mConfiguration.keys();
    }

    /*
     * (non-Javadoc)
     * @see org.javarosa.core.util.externalizable.Externalizable#readExternal(java.io.DataInputStream, org.javarosa.core.util.externalizable.PrototypeFactory)
     */
    public void readExternal(DataInputStream in, PrototypeFactory pf)
            throws IOException, DeserializationException {
        mX = ExtUtil.readString(in);
        mY = ExtUtil.readString(in);
        mNodeSet = (TreeReference)ExtUtil.read(in, TreeReference.class, pf);
        mConfiguration = (Hashtable<String, Text>)ExtUtil.read(in, new ExtWrapMap(String.class, Text.class), pf);
        mPointConfiguration =  (Vector<String>)ExtUtil.read(in, new ExtWrapList(String.class), pf);
    }

    /*
     * (non-Javadoc)
     * @see org.javarosa.core.util.externalizable.Externalizable#writeExternal(java.io.DataOutputStream)
     */
    public void writeExternal(DataOutputStream out) throws IOException {
        ExtUtil.writeString(out, mX);
        ExtUtil.writeString(out, mY);
        ExtUtil.write(out, mNodeSet);
        ExtUtil.write(out, new ExtWrapMap(mConfiguration));
        ExtUtil.write(out, new ExtWrapList(mPointConfiguration));
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
