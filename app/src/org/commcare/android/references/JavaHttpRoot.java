package org.commcare.android.references;

import org.commcare.android.net.HttpRequestGenerator;
import org.javarosa.core.reference.InvalidReferenceException;
import org.javarosa.core.reference.Reference;
import org.javarosa.core.reference.ReferenceFactory;

/**
 * @author ctsims
 */
public class JavaHttpRoot implements ReferenceFactory {
    
    HttpRequestGenerator generator = new HttpRequestGenerator();

    @Override
    public Reference derive(String URI) throws InvalidReferenceException {
        return new JavaHttpReference(URI, generator);
    }

    @Override
    public Reference derive(String URI, String context) throws InvalidReferenceException {
        context = context.substring(0, context.lastIndexOf('/')+1);
        return new JavaHttpReference(context + URI, generator);
    }

    @Override
    public boolean derives(String URI) {
        URI = URI.toLowerCase();
        return URI.startsWith("http://") || URI.startsWith("https://");
    }
}
