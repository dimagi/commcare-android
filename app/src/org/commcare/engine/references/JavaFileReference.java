package org.commcare.engine.references;

import org.commcare.utils.FileUtil;
import org.javarosa.core.reference.Reference;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * @author ctsims
 */
public class JavaFileReference implements Reference {

    private final String localPart;
    private final String uri;

    public JavaFileReference(String localPart, String uri) {
        this.localPart = localPart;
        this.uri = uri;
    }

    @Override
    public boolean doesBinaryExist() throws IOException {
        return file().exists();
    }

    @Override
    public OutputStream getOutputStream() throws IOException {
        File f = file();
        FileUtil.ensureFilePathExists(f);
        f.createNewFile();
        return new FileOutputStream(f);
    }

    @Override
    public InputStream getStream() throws IOException {
        File file = file();
        //CTS: Removed a thing here that created an empty file. Not sure why that was there.
        if (!file.exists()) {
            throw new IOException("No file exists at " + file.getAbsolutePath());
        }
        return new FileInputStream(file);
    }

    @Override
    public String getURI() {
        return "jr://file/" + uri;
    }

    @Override
    public boolean isReadOnly() {
        return false;
    }

    @Override
    public void remove() throws IOException {
        File file = file();
        if (!file.delete()) {
            throw new IOException("Could not delete file at URI " + file.getAbsolutePath());
        }
    }

    private File file() {
        return new File(getLocalURI());
    }

    @Override
    public String getLocalURI() {
        return new File(localPart + File.separator + uri).getAbsolutePath();
    }

    @Override
    public Reference[] probeAlternativeReferences() {
        return new Reference[0];
    }
}
