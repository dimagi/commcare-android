package org.commcare.android.mocks;

import org.apache.http.Header;
import org.apache.http.HeaderIterator;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.ProtocolVersion;
import org.apache.http.StatusLine;
import org.apache.http.params.HttpParams;

import java.util.Locale;

/**
 * @author Phillip Mates (pmates@dimagi.com)
 */
public class HttpResponseMock {
    public static HttpResponse buildHttpResponseMock(final int statusCode) {
        return new HttpResponse() {
            private final StatusLine statusLine = new StatusLine() {
                @Override
                public ProtocolVersion getProtocolVersion() {
                    return null;
                }

                @Override
                public int getStatusCode() {
                    return statusCode;
                }

                @Override
                public String getReasonPhrase() {
                    return null;
                }
            };

            @Override
            public StatusLine getStatusLine() {
                return statusLine;
            }

            @Override
            public void setStatusLine(StatusLine statusLine) {
                throw new RuntimeException("not supported in mock");
            }

            @Override
            public void setStatusLine(ProtocolVersion protocolVersion, int i) {
                throw new RuntimeException("not supported in mock");
            }

            @Override
            public void setStatusLine(ProtocolVersion protocolVersion, int i, String s) {
                throw new RuntimeException("not supported in mock");
            }

            @Override
            public void setStatusCode(int i) throws IllegalStateException {
                throw new RuntimeException("not supported in mock");
            }

            @Override
            public void setReasonPhrase(String s) throws IllegalStateException {
                throw new RuntimeException("not supported in mock");
            }

            @Override
            public HttpEntity getEntity() {
                throw new RuntimeException("not supported in mock");
            }

            @Override
            public void setEntity(HttpEntity httpEntity) {
                throw new RuntimeException("not supported in mock");
            }

            @Override
            public Locale getLocale() {
                throw new RuntimeException("not supported in mock");
            }

            @Override
            public void setLocale(Locale locale) {
                throw new RuntimeException("not supported in mock");
            }

            @Override
            public ProtocolVersion getProtocolVersion() {
                throw new RuntimeException("not supported in mock");
            }

            @Override
            public boolean containsHeader(String s) {
                throw new RuntimeException("not supported in mock");
            }

            @Override
            public Header[] getHeaders(String s) {
                throw new RuntimeException("not supported in mock");
            }

            @Override
            public Header getFirstHeader(String s) {
                throw new RuntimeException("not supported in mock");
            }

            @Override
            public Header getLastHeader(String s) {
                throw new RuntimeException("not supported in mock");
            }

            @Override
            public Header[] getAllHeaders() {
                throw new RuntimeException("not supported in mock");
            }

            @Override
            public void addHeader(Header header) {
                throw new RuntimeException("not supported in mock");
            }

            @Override
            public void addHeader(String s, String s1) {
                throw new RuntimeException("not supported in mock");
            }

            @Override
            public void setHeader(Header header) {
                throw new RuntimeException("not supported in mock");
            }

            @Override
            public void setHeader(String s, String s1) {
                throw new RuntimeException("not supported in mock");
            }

            @Override
            public void setHeaders(Header[] headers) {
                throw new RuntimeException("not supported in mock");
            }

            @Override
            public void removeHeader(Header header) {
                throw new RuntimeException("not supported in mock");
            }

            @Override
            public void removeHeaders(String s) {
                throw new RuntimeException("not supported in mock");
            }

            @Override
            public HeaderIterator headerIterator() {
                throw new RuntimeException("not supported in mock");
            }

            @Override
            public HeaderIterator headerIterator(String s) {
                throw new RuntimeException("not supported in mock");
            }

            @Override
            public HttpParams getParams() {
                throw new RuntimeException("not supported in mock");
            }

            @Override
            public void setParams(HttpParams httpParams) {
                throw new RuntimeException("not supported in mock");
            }
        };
    }
}
