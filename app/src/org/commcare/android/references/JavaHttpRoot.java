package org.commcare.android.references;

import android.support.annotation.NonNull;

import org.commcare.android.net.HttpRequestGenerator;
import org.javarosa.core.reference.InvalidReferenceException;
import org.javarosa.core.reference.Reference;
import org.javarosa.core.reference.ReferenceFactory;

/**
 * @author ctsims
 */
public class JavaHttpRoot implements ReferenceFactory {
    
    @NonNull
    HttpRequestGenerator generator = new HttpRequestGenerator();

    /* (non-Javadoc)
     * @see org.javarosa.core.reference.RawRoot#derive(java.lang.String)
     */
    @NonNull
    public Reference derive(String URI) throws InvalidReferenceException {
        return new JavaHttpReference(URI, generator);
    }

    /* (non-Javadoc)
     * @see org.javarosa.core.reference.RawRoot#derive(java.lang.String, java.lang.String)
     */
    @NonNull
    public Reference derive(String URI, String context) throws InvalidReferenceException {
        context = context.substring(0, context.lastIndexOf('/')+1);
        return new JavaHttpReference(context + URI, generator);
    }

    /* (non-Javadoc)
     * @see org.javarosa.core.reference.RawRoot#derives(java.lang.String)
     */
    public boolean derives(String URI) {
        URI = URI.toLowerCase();
        return URI.startsWith("http://") || URI.startsWith("https://");
    }
}
