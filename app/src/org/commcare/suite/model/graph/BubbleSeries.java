package org.commcare.suite.model.graph;

import org.javarosa.core.model.condition.EvaluationContext;
import org.javarosa.core.util.externalizable.DeserializationException;
import org.javarosa.core.util.externalizable.ExtUtil;
import org.javarosa.core.util.externalizable.PrototypeFactory;
import org.javarosa.xpath.expr.XPathExpression;
import org.javarosa.xpath.parser.XPathSyntaxException;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Single series ("line") on a bubble chart.
 *
 * @author jschweers
 */
public class BubbleSeries extends XYSeries {
    private String mRadius;
    private XPathExpression mRadiusParse;

    /*
     * Deserialization Only!
     */
    public BubbleSeries() {

    }

    public BubbleSeries(String nodeSet) {
        super(nodeSet);
    }

    public String getRadius() {
        return mRadius;
    }

    public void setRadius(String radius) {
        mRadius = radius;
        mRadiusParse = null;
    }

    /*
     * (non-Javadoc)
     * @see org.commcare.suite.model.graph.XYSeries#parse()
     */
    protected void parse() throws XPathSyntaxException {
        super.parse();
        if (mRadiusParse == null) {
            mRadiusParse = parse(mRadius);
        }
    }

    /*
     * Get actual value for radius in a given EvaluationContext.
     */
    public String evaluateRadius(EvaluationContext context) throws XPathSyntaxException {
        parse();
        return evaluateExpression(mRadiusParse, context);
    }

    /*
     * (non-Javadoc)
     * @see org.commcare.suite.model.graph.XYSeries#readExternal(java.io.DataInputStream, org.javarosa.core.util.externalizable.PrototypeFactory)
     */
    public void readExternal(DataInputStream in, PrototypeFactory pf)
            throws IOException, DeserializationException {
        super.readExternal(in, pf);
        mRadius = ExtUtil.readString(in);
    }

    /*
     * (non-Javadoc)
     * @see org.commcare.suite.model.graph.XYSeries#writeExternal(java.io.DataOutputStream)
     */
    public void writeExternal(DataOutputStream out) throws IOException {
        super.writeExternal(out);
        ExtUtil.writeString(out, mRadius);
    }
}
