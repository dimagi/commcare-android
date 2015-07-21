/**
 * 
 */
package org.commcare.android.references;

import android.support.annotation.NonNull;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;

import org.commcare.android.net.HttpRequestGenerator;
import org.javarosa.core.reference.Reference;

/**
 * @author ctsims
 *
 */
public class JavaHttpReference implements Reference {

    private String uri;
    HttpRequestGenerator generator;
    
    public JavaHttpReference(String uri, HttpRequestGenerator generator) {
        this.uri = uri;
        this.generator = generator;
    }
    
    
    /* (non-Javadoc)
     * @see org.javarosa.core.reference.Reference#doesBinaryExist()
     */
    public boolean doesBinaryExist() throws IOException {
        //For now....
        return true;
    }

    /* (non-Javadoc)
     * @see org.javarosa.core.reference.Reference#getOutputStream()
     */
    @NonNull
    public OutputStream getOutputStream() throws IOException {
        throw new IOException("Http references are read only!");
    }
    
    /* (non-Javadoc)
     * @see org.javarosa.core.reference.Reference#getStream()
     */
    public InputStream getStream() throws IOException {
        URL url = new URL(uri);
        return generator.simpleGet(url);
    }

    /* (non-Javadoc)
     * @see org.javarosa.core.reference.Reference#getURI()
     */
    public String getURI() {
        return uri;
    }

    /* (non-Javadoc)
     * @see org.javarosa.core.reference.Reference#isReadOnly()
     */
    public boolean isReadOnly() {
        return true;
    }

    /* (non-Javadoc)
     * @see org.javarosa.core.reference.Reference#remove()
     */
    public void remove() throws IOException {
        throw new IOException("Http references are read only!");
    }


    public String getLocalURI() {
        return uri;
    }

    @NonNull
    public Reference[] probeAlternativeReferences() {
        return new Reference [0];
    }


    //TODO: This should get changed to be set from the root, don't assume this will
    //still be here indefinitely
    public void setHttpRequestor(HttpRequestGenerator generator) {
        this.generator = generator;
    }
}
