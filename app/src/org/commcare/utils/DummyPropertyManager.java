package org.commcare.utils;

import org.javarosa.core.services.IPropertyManager;
import org.javarosa.core.services.properties.IPropertyRules;

import java.util.Vector;

/**
 * @author ctsims
 */
public class DummyPropertyManager implements IPropertyManager {

    @Override
    public void addRules(IPropertyRules rules) {
    }

    @Override
    public Vector getProperty(String propertyName) {
        return null;
    }

    @Override
    public Vector getRules() {
        return null;
    }

    @Override
    public String getSingularProperty(String propertyName) {
        return null;
    }

    @Override
    public void setProperty(String propertyName, String propertyValue) {
    }

    @Override
    public void setProperty(String propertyName, Vector<String> propertyValue) {
    }
}
