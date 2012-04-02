/**
 * 
 */
package org.commcare.android.references;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.javarosa.core.reference.Reference;

/**
 * @author ctsims
 *
 */
public class JavaFileReference implements Reference {
	
	String localPart;
	String uri;
	
	public JavaFileReference(String localPart, String uri) {
		this.localPart = localPart;
		this.uri = uri;
	}

	public boolean doesBinaryExist() throws IOException {
		return file().exists();
	}

	public OutputStream getOutputStream() throws IOException {
		File f = file();
		ensureFilePathExists(f);
		return new FileOutputStream(f);
	}
	
	/**
	 * Ensure that everything between "localpart" and f exists
	 * and create it if not.
	 * 
	 * @param f
	 */
	private void ensureFilePathExists(File f) {
		File folder = f.getParentFile();
		if(folder != null) {
			//Don't worry about return value
			folder.mkdirs();
		}
	}

	public InputStream getStream() throws IOException {
		File file = file();
		if(!file.exists()) {
			if(!file.createNewFile()) {
				throw new IOException("Could not create file at URI " + file.getAbsolutePath());
			}
		}
		return new FileInputStream(file);
	}

	public String getURI() {
		return "jr://file/" + uri;
	}

	public boolean isReadOnly() {
		return false;
	}

	public void remove() throws IOException {
		File file = file();
		if(!file.delete()) {
			throw new IOException("Could not delete file at URI " + file.getAbsolutePath());
		}
	}
	
	private File file() {
		return new File(getLocalURI());
	}

	public String getLocalURI() {
		return new File(localPart + File.separator + uri).getAbsolutePath();
	}
	
	public Reference[] probeAlternativeReferences() {
		return new Reference [0];
	}
}
