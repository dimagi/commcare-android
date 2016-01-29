package org.commcare.graph.util;

import org.commcare.graph.model.AnnotationData;
import org.commcare.graph.model.BubblePointData;
import org.commcare.graph.model.ConfigurableData;
import org.commcare.graph.model.GraphData;
import org.commcare.graph.model.SeriesData;
import org.commcare.graph.model.XYPointData;
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
import org.javarosa.xpath.parser.XPathSyntaxException;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Vector;

/**
 * Constants used by graphing
 *
 * @author jschweers
 */
public class GraphUtil {
    public static final String TYPE_XY = "xy";
    public static final String TYPE_BAR = "bar";
    public static final String TYPE_BUBBLE = "bubble";
    public static final String TYPE_TIME = "time";
}
