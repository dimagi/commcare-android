/**
 * 
 */
package org.commcare.android.references;

import org.javarosa.core.reference.InvalidReferenceException;
import org.javarosa.core.reference.Reference;
import org.javarosa.core.reference.ReferenceFactory;
import org.javarosa.core.reference.ReferenceManager;

import android.content.Context;
import android.support.annotation.NonNull;


/**
 * @author ctsims
 *
 */
public class AssetFileRoot implements ReferenceFactory {
    private Context context;

    public AssetFileRoot(Context context) {
        this.context = context;
    }
    
    @NonNull
    public Reference derive(@NonNull String URI) throws InvalidReferenceException {
        return new AssetFileReference(context, URI.substring("jr://asset/".length()));
    }

    public Reference derive(String URI, @NonNull String context) throws InvalidReferenceException {
        if(context.lastIndexOf('/') != -1) {
            context = context.substring(0,context.lastIndexOf('/') + 1);
        }
        return ReferenceManager._().DeriveReference(context + URI);
    }

    public boolean derives(@NonNull String URI) {
        return URI.toLowerCase().startsWith("jr://asset/");
    }
}
