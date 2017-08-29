package org.commcare.engine.references;

import org.commcare.interfaces.CommcareRequestEndpoints;
import org.commcare.network.CommcareRequestGenerator;
import org.javarosa.core.reference.Reference;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * @author ctsims
 */
public class JavaHttpReference implements Reference {

    private final String uri;
    private CommcareRequestEndpoints generator;

    public JavaHttpReference(String uri, CommcareRequestGenerator generator) {
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
        return generator.simpleGet(uri).body().byteStream();
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


    @Override
    public String getLocalURI() {
        return uri;
    }

    //TODO: This should get changed to be set from the root, don't assume this will
    //still be here indefinitely
    public void setHttpRequestor(CommcareRequestEndpoints generator) {
        this.generator = generator;
    }
}
