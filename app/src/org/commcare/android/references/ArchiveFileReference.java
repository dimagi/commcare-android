/**
 * 
 */
package org.commcare.android.references;

import java.io.File;
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
		File file = new File(getLocalURI());
		//CTS: Removed a thing here that created an empty file. Not sure why that was there.
		if(!file.exists()) {
			throw new IOException("No file exists at " + file.getAbsolutePath());
		}
		return new FileInputStream(file);

	}

	public String getURI() {
		return "jr://archive/" + archiveURI;
	}

	public boolean isReadOnly() {
		return true;
	}

	public void remove() throws IOException {
		throw new IOException("Cannot remove files from the archive");
	}

	public String getLocalURI() {
		if(archiveURI.contains("profile.xml")){
			return localroot +"/"+ archiveURI;
		}
		return localroot +"/coverage610/"+ archiveURI;
	}

	public Reference[] probeAlternativeReferences() {
		return new Reference [0];
	}
}
