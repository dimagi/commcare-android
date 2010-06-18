/**
 * 
 */
package org.commcare.android.references;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;

import org.javarosa.core.reference.Reference;

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
		return url.openStream();
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

}
