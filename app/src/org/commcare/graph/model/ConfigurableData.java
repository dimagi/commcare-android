package org.commcare.graph.model;

/**
 * Interface to be implemented by any classes in this package that store configuration data using a String => String mapping.
 *
 * @author jschweers
 */
public interface ConfigurableData {

    void setConfiguration(String key, String value);

    String getConfiguration(String key);

    String getConfiguration(String key, String defaultValue);
}
