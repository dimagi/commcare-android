/**
 * 
 */
package org.commcare.android.references;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import org.commcare.android.javarosa.AndroidLogger;
import org.commcare.android.logic.GlobalConstants;
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

	/* (non-Javadoc)
	 * @see org.javarosa.core.reference.Reference#getStream()
	 */
	public InputStream getStream() throws IOException {
		URL url = new URL(uri);
		HttpURLConnection con = (HttpURLConnection) url.openConnection();
		setup(con);
		// Start the query
		con.connect();
		//It's possible we're getting redirected from http to https
		//if so, we need to handle it explicitly
		if(con.getResponseCode() == 301) {
			//only allow one level of redirection here for now.	
			Logger.log(AndroidLogger.TYPE_WARNING_NETWORK, "Attempting 1 stage redirect from " + uri + " to " + con.getURL().toString());
			URL newUrl = con.getURL();
			con.disconnect();
			con = (HttpURLConnection) newUrl.openConnection();
			setup(con);
			con.connect();
		}
		
		//Don't allow redirects _from_ https _to_ https unless they are redirecting to the same server.
		if(!isValidRedirect(url, con.getURL())) {
			Logger.log(AndroidLogger.TYPE_WARNING_NETWORK, "Invalid redirect from " + uri + " to " + con.getURL().toString());
			throw new IOException("Invalid redirect from secure server to insecure server");
		}
		
		return con.getInputStream();
	}
	
	private boolean isValidRedirect(URL url, URL newUrl) {
		//unless it's https, don't worry about it
		if(!url.getProtocol().equals("https")) {
			return true;
		}
		
		//if it is, verify that we're on the same server.
		if(url.getHost().equals(newUrl.getHost())) {
			return true;
		} else {
			//otherwise we got redirected from a secure link to a different
			//link, which isn't acceptable for now.
			return false;
		}
	}

	private void setup(HttpURLConnection con) throws IOException {
		con.setConnectTimeout(GlobalConstants.CONNECTION_TIMEOUT);
		con.setRequestMethod("GET");
		con.setDoInput(true);
		con.setInstanceFollowRedirects(true);
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
}
