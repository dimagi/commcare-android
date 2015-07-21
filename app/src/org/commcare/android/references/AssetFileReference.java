/**
 * 
 */
package org.commcare.android.references;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.javarosa.core.reference.Reference;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

/**
 * Reference to an asset file
 * 
 * @author ctsims
 *
 */
public class AssetFileReference implements Reference {
    
    String assetURI;
    Context c;
    
    public AssetFileReference(Context c, String assetURI) {
        this.c = c;
        this.assetURI = assetURI;
    }

    public boolean doesBinaryExist() throws IOException {
        try {
            c.getAssets().openFd(assetURI).close();
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    @NonNull
    public InputStream getStream() throws IOException {
        return c.getAssets().open(assetURI);
    }

    @NonNull
    public String getURI() {
        return "jr://asset/" + assetURI;
    }

    @Nullable
    public String getLocalURI() {
        return null;
    }

    public boolean isReadOnly() {
        //I think this is always true, not 100% sure.
        return true;
    }

    @NonNull
    public OutputStream getOutputStream() throws IOException {
        throw new IOException("Asset references are read only!");
    }

    public void remove() throws IOException {
        //IOException? Do we use this for certain forms of installers? Probably not.
        throw new IOException("Cannot remove Asset files from the Package");
    }

    @Nullable
    public Reference[] probeAlternativeReferences() {
        // TODO Auto-generated method stub
        return null;
    }

}
