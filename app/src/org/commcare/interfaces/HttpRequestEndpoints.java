package org.commcare.interfaces;

import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.entity.mime.MultipartEntity;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Date;

/**
 * Types of http requests made by CommCare mobile to server
 *
 * @author Phillip Mates (pmates@dimagi.com)
 */
public interface HttpRequestEndpoints {
    HttpResponse makeCaseFetchRequest(String baseUri, boolean includeStateFlags) throws ClientProtocolException, IOException;

    HttpResponse makeKeyFetchRequest(String baseUri, Date lastRequest) throws ClientProtocolException, IOException;

    HttpResponse postData(String url, MultipartEntity entity) throws ClientProtocolException, IOException;

    InputStream simpleGet(URL url) throws IOException;
}
