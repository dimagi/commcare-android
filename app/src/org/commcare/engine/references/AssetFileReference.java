package org.commcare.engine.references;

import android.content.Context;

import org.javarosa.core.reference.Reference;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Reference to an asset file
 *
 * @author ctsims
 */
class AssetFileReference implements Reference {

    private final String assetURI;
    private final Context c;

    public AssetFileReference(Context c, String assetURI) {
        this.c = c;
        this.assetURI = assetURI;
    }

    @Override
    public boolean doesBinaryExist() throws IOException {
        try {
            c.getAssets().openFd(assetURI).close();
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    @Override
    public InputStream getStream() throws IOException {
        return c.getAssets().open(assetURI);
    }

    @Override
    public String getURI() {
        return "jr://asset/" + assetURI;
    }

    @Override
    public String getLocalURI() {
        return null;
    }

    @Override
    public boolean isReadOnly() {
        //I think this is always true, not 100% sure.
        return true;
    }

    @Override
    public OutputStream getOutputStream() throws IOException {
        throw new IOException("Asset references are read only!");
    }

    @Override
    public void remove() throws IOException {
        //IOException? Do we use this for certain forms of installers? Probably not.
        throw new IOException("Cannot remove Asset files from the Package");
    }

    @Override
    public Reference[] probeAlternativeReferences() {
        // TODO Auto-generated method stub
        return null;
    }

}
