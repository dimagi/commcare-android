/**
 *
 */
package org.commcare.android.references;

import org.javarosa.core.reference.InvalidReferenceException;
import org.javarosa.core.reference.Reference;
import org.javarosa.core.reference.ReferenceFactory;
import org.javarosa.core.reference.ReferenceManager;


/**
 * @author ctsims
 */
public class JavaFileRoot implements ReferenceFactory {
    private String localRoot;

    public JavaFileRoot(String localRoot) {
        this.localRoot = localRoot;
    }

    public Reference derive(String URI) throws InvalidReferenceException {
        return new JavaFileReference(localRoot, URI.substring("jr://file/".length()));
    }

    public Reference derive(String URI, String context) throws InvalidReferenceException {
        if (context.lastIndexOf('/') != -1) {
            context = context.substring(0, context.lastIndexOf('/') + 1);
        }
        return ReferenceManager._().DeriveReference(context + URI);
    }

    public boolean derives(String URI) {
        return URI.toLowerCase().startsWith("jr://file/");
    }
}
