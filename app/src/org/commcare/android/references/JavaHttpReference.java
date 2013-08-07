/**
 * 
 */
package org.commcare.android.references;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import org.apache.http.HttpResponse;
import org.commcare.android.database.user.models.User;
import org.commcare.android.javarosa.AndroidLogger;
import org.commcare.android.logic.GlobalConstants;
import org.commcare.android.net.HttpRequestGenerator;
import org.commcare.dalvik.application.CommCareApplication;
import org.javarosa.core.reference.Reference;
import org.javarosa.core.services.Logger;

/**
 * @author ctsims
 *
 */
public class JavaHttpReference implements Reference {

	private String uri;
	
	public JavaHttpReference(String uri) {
		this.uri = uri;
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
	public OutputStream getOutputStream() throws IOException {
		throw new IOException("Http references are read only!");
	}
	
	HttpRequestGenerator generator;

	/* (non-Javadoc)
	 * @see org.javarosa.core.reference.Reference#getStream()
	 */
	public InputStream getStream() throws IOException {
		URL url = new URL(uri);
		
		//this is not a great way to do this...
		if(generator == null) {
				generator = new HttpRequestGenerator();
        }
		
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

	public Reference[] probeAlternativeReferences() {
		return new Reference [0];
	}


	public void setHttpRequestor(HttpRequestGenerator generator) {
		this.generator = generator;
	}
}
