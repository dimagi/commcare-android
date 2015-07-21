/**
 * 
 */
package org.commcare.android.references;

import android.support.annotation.NonNull;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.javarosa.core.reference.Reference;

/**
 * @author wspride
 * this class associates a GUID and relative path with a corresponding 
 * real directory in the filesystem
 *
 */
public class ArchiveFileReference implements Reference {

    String GUID;
    String archiveURI;
    String localroot;

    public ArchiveFileReference(String localroot, String GUID, String archiveURI) {
        this.archiveURI = archiveURI;
        this.localroot = localroot;
        this.GUID = GUID;
    }

    public boolean doesBinaryExist() throws IOException {
        return false;
    }

    @NonNull
    public OutputStream getOutputStream() throws IOException {
        throw new IOException("Archive references are read only!");
    }

    @NonNull
    public InputStream getStream() throws IOException {
        File file = new File(getLocalURI());
        //CTS: Removed a thing here that created an empty file. Not sure why that was there.
        if(!file.exists()) {
            throw new IOException("No file exists at " + file.getAbsolutePath());
        }
        return new FileInputStream(file);

    }

    @NonNull
    public String getURI() {
        return "jr://archive/" + GUID + "/" + archiveURI;
    }

    public boolean isReadOnly() {
        return true;
    }

    public void remove() throws IOException {
        throw new IOException("Cannot remove files from the archive");
    }

    @NonNull
    public String getLocalURI() {
        return localroot +"/"+ archiveURI;
    }

    @NonNull
    public Reference[] probeAlternativeReferences() {
        return new Reference [0];
    }
}
