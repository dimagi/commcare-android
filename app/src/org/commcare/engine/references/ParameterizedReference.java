package org.commcare.engine.references;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

// A Reference that can be interpretated differently based on a set of parameters
public interface ParameterizedReference {

    InputStream getStream(Map<String, String> params) throws IOException;
}
