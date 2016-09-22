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
    @SuppressWarnings("unused")
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

    @Override
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

    @Override
    public void readExternal(DataInputStream in, PrototypeFactory pf)
            throws IOException, DeserializationException {
        super.readExternal(in, pf);
        mRadius = ExtUtil.readString(in);
    }

    @Override
    public void writeExternal(DataOutputStream out) throws IOException {
        super.writeExternal(out);
        ExtUtil.writeString(out, mRadius);
    }
}
