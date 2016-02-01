package org.commcare.suite.model.graph;

import org.commcare.suite.model.Text;

import java.util.Enumeration;

/**
 * Interface to be implemented by any classes in this package that store configuration data using a String => Text mapping.
 *
 * @author jschweers
 */
public interface Configurable {

    public Enumeration getConfigurationKeys();

    public Text getConfiguration(String key);

    public void setConfiguration(String key, Text value);

}
