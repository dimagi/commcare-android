package org.commcare.graph.model;

/**
 * Interface to be implemented by any classes in this package that store configuration data using a String => String mapping.
 *
 * @author jschweers
 */
public interface ConfigurableData {

    public void setConfiguration(String key, String value);

    public String getConfiguration(String key);

    public String getConfiguration(String key, String defaultValue);
}
