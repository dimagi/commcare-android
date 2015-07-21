/**
 * 
 */
package org.commcare.android.references;

import android.support.annotation.NonNull;

import java.util.HashMap;

import org.javarosa.core.reference.InvalidReferenceException;
import org.javarosa.core.reference.Reference;
import org.javarosa.core.reference.ReferenceFactory;
import org.javarosa.core.reference.ReferenceManager;
import org.javarosa.core.util.PropertyUtils;


/**
 * @author wspride
 * This class managers references between GUIDs and the associated path in the file system
 * To register an archive file with this system call addArchiveFile(filepath) - this will return a GUID
 * This GUID will allow you to derive files from this location using the ArchiveFileRefernece class
 *
 */
public class ArchiveFileRoot implements ReferenceFactory {
    
    @NonNull
    private HashMap<String, String> guidToFolderMap = new HashMap<String, String>();
    
    private final int guidLength = 10;

    public ArchiveFileRoot() {
    }
    
    @NonNull
    public Reference derive(@NonNull String guidPath) throws InvalidReferenceException {
        return new ArchiveFileReference(guidToFolderMap.get(getGUID(guidPath)),getGUID(guidPath), getPath(guidPath));
    }

    public Reference derive(String URI, @NonNull String context) throws InvalidReferenceException {
        if(context.lastIndexOf('/') != -1) {
            context = context.substring(0,context.lastIndexOf('/') + 1);
        }
        return ReferenceManager._().DeriveReference(context + URI);
    }

    public boolean derives(@NonNull String URI) {
        return URI.toLowerCase().startsWith("jr://archive/");
    }
    
    @NonNull
    public String addArchiveFile(String filepath){
        String mGUID = PropertyUtils.genGUID(guidLength);
        guidToFolderMap.put(mGUID, filepath);
        return mGUID;
    }
    
    @NonNull
    public String getGUID(@NonNull String jrpath){
        String prependRemoved = jrpath.substring("jr://archive/".length());
        int slashindex = prependRemoved.indexOf("/");
        return prependRemoved.substring(0, slashindex);
    }
    
    @NonNull
    public String getPath(@NonNull String jrpath){
        String mGUID = getGUID(jrpath);
        int mIndex = jrpath.indexOf(mGUID);
        return jrpath.substring(mIndex + mGUID.length());
    }
}
