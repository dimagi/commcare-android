package org.commcare.dalvik.geo;

import org.javarosa.core.model.condition.EvaluationContext;
import org.javarosa.core.model.condition.IFunctionHandler;
import org.javarosa.core.model.data.GeoPointData;

import java.util.Vector;

/**
 * Created by ftong on 1/12/16.
 */
public class HereFunctionHandler implements IFunctionHandler {
    public static final String HERE_NAME = "here";
    private GeoPointData location;

    public HereFunctionHandler() {}

    public String getName() {
        return HERE_NAME;
    }

    public void setLocation(GeoPointData location) { this.location = location; }

    public Vector getPrototypes() {
        Vector p = new Vector();
        p.addElement(new Class[0]);
        return p;
    }

    public boolean rawArgs() {
        return false;
    }

    public boolean realTime() {
        return true;
    }

    public Object eval(Object[] args, EvaluationContext ec) {
        return location;
    }
}
