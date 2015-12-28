package org.commcare.android.references;

import org.commcare.android.net.HttpRequestGenerator;
import org.javarosa.core.reference.Reference;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;

/**
 * @author ctsims
 */
public class JavaHttpReference implements Reference {

    private final String uri;
    HttpRequestGenerator generator;

    public JavaHttpReference(String uri, HttpRequestGenerator generator) {
        this.uri = uri;
        this.generator = generator;
    }


    @Override
    public boolean doesBinaryExist() throws IOException {
        //For now....
        return true;
    }

    @Override
    public OutputStream getOutputStream() throws IOException {
        throw new IOException("Http references are read only!");
    }

    @Override
    public InputStream getStream() throws IOException {
        URL url = new URL(uri);
        return generator.simpleGet(url);
    }

    @Override
    public String getURI() {
        return uri;
    }

    @Override
    public boolean isReadOnly() {
        return true;
    }

    @Override
    public void remove() throws IOException {
        throw new IOException("Http references are read only!");
    }


    public String getLocalURI() {
        return uri;
    }

    public Reference[] probeAlternativeReferences() {
        return new Reference[0];
    }


    //TODO: This should get changed to be set from the root, don't assume this will
    //still be here indefinitely
    public void setHttpRequestor(HttpRequestGenerator generator) {
        this.generator = generator;
    }
}
