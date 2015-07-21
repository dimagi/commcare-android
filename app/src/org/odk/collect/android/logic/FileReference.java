/**
 * 
 */

package org.odk.collect.android.logic;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.javarosa.core.reference.Reference;

/**
 * @author ctsims
 */
public class FileReference implements Reference {
    String localPart;
    String referencePart;


    public FileReference(String localPart, String referencePart) {
        this.localPart = localPart;
        this.referencePart = referencePart;
    }


    @NonNull
    private String getInternalURI() {
        return "/" + localPart + referencePart;
    }


    /*
     * (non-Javadoc)
     * @see org.javarosa.core.reference.Reference#doesBinaryExist()
     */
    @Override
    public boolean doesBinaryExist() {
        return new File(getInternalURI()).exists();
    }


    /*
     * (non-Javadoc)
     * @see org.javarosa.core.reference.Reference#getStream()
     */
    @NonNull
    @Override
    public InputStream getStream() throws IOException {
        return new FileInputStream(getInternalURI());
    }


    /*
     * (non-Javadoc)
     * @see org.javarosa.core.reference.Reference#getURI()
     */
    @NonNull
    @Override
    public String getURI() {
        return "jr://file" + referencePart;
    }


    /*
     * (non-Javadoc)
     * @see org.javarosa.core.reference.Reference#isReadOnly()
     */
    @Override
    public boolean isReadOnly() {
        return false;
    }


    /*
     * (non-Javadoc)
     * @see org.javarosa.core.reference.Reference#getOutputStream()
     */
    @NonNull
    @Override
    public OutputStream getOutputStream() throws IOException {
        return new FileOutputStream(getInternalURI());
    }


    /*
     * (non-Javadoc)
     * @see org.javarosa.core.reference.Reference#remove()
     */
    @Override
    public void remove() {
        // TODO bad practice to ignore return values
        new File(getInternalURI()).delete();
    }


    /*
     * (non-Javadoc)
     * @see org.javarosa.core.reference.Reference#getLocalURI()
     */
    @NonNull
    @Override
    public String getLocalURI() {
        return getInternalURI();
    }


    /*
     * (non-Javadoc)
     * @see org.javarosa.core.reference.Reference#probeAlternativeReferences()
     */
    @Nullable
    @Override
    public Reference[] probeAlternativeReferences() {
        // TODO Auto-generated method stub
        return null;
    }

}
