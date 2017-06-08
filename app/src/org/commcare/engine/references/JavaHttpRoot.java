package org.commcare.engine.references;

import org.commcare.network.HttpRequestGenerator;
import org.javarosa.core.reference.InvalidReferenceException;
import org.javarosa.core.reference.Reference;
import org.javarosa.core.reference.ReferenceFactory;

/**
 * @author ctsims
 */
public class JavaHttpRoot implements ReferenceFactory {

    private final HttpRequestGenerator generator = HttpRequestGenerator.buildNoAuthGenerator();

    @Override
    public Reference derive(String URI) throws InvalidReferenceException {
        return new JavaHttpReference(URI, generator);
    }

    @Override
    public Reference derive(String URI, String context) throws InvalidReferenceException {
        String rootPath = context.substring(0, context.lastIndexOf('/') + 1);
        String derivedPath = rootPath + URI;
        if (context.contains("?")) {
            String paramsPath = context.substring(context.lastIndexOf('?'));
            derivedPath = derivedPath + paramsPath;
        }
        return new JavaHttpReference(derivedPath, generator);
    }

    @Override
    public boolean derives(String URI) {
        URI = URI.toLowerCase();
        return URI.startsWith("http://") || URI.startsWith("https://");
    }
}
