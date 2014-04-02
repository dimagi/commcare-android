/**
 * 
 */
package org.commcare.android.references;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.javarosa.core.reference.Reference;

/**
 * @author ctsims
 *
 */
public class ArchiveFileReference implements Reference {

	String archiveURI;
	String localroot;

	public ArchiveFileReference(String localroot, String archiveURI) {
		this.archiveURI = archiveURI;
		this.localroot = localroot;
	}

	public boolean doesBinaryExist() throws IOException {
		return false;
	}

	public OutputStream getOutputStream() throws IOException {
		throw new IOException("Archive references are read only!");
	}

	public InputStream getStream() throws IOException {
		ZipFile zf = new ZipFile(getLocalURI());
		Enumeration e = zf.entries();
		ZipEntry entry = (ZipEntry) e.nextElement();
		return zf.getInputStream(entry);
	}

	public String getURI() {
		return "jr://archive/" + archiveURI;
	}

	public boolean isReadOnly() {
		return true;
	}

	public void remove() throws IOException {
		throw new IOException("Cannot remove Asset files from the Package");
	}

	public String getLocalURI() {
		return localroot +"/"+ archiveURI;
	}

	public Reference[] probeAlternativeReferences() {
		return new Reference [0];
	}
}
