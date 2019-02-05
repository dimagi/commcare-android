package org.commcare.engine.references;

import org.commcare.interfaces.CommcareRequestEndpoints;
import org.commcare.network.CommcareRequestGenerator;
import org.commcare.network.HttpUtils;
import org.javarosa.core.reference.Reference;
import org.javarosa.core.services.locale.Localization;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import okhttp3.ResponseBody;
import retrofit2.Response;

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
        Response<ResponseBody> response = generator.simpleGet(uri);
        if (response.isSuccessful()) {
            return response.body().byteStream();
        } else {
            if (response.code() == 406) {
                throw new IOException(HttpUtils.parseUserVisibleError(response));
            }
            throw new IOException(Localization.get("install.fail.error", Integer.toString(response.code())));
        }
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
