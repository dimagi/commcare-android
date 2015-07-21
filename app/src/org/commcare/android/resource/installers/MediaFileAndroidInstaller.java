package org.commcare.android.resource.installers;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import org.commcare.android.util.AndroidCommCarePlatform;
import org.commcare.android.util.FileUtil;
import org.commcare.resources.model.Resource;
import org.commcare.resources.model.ResourceInitializationException;
import org.commcare.resources.model.ResourceLocation;
import org.commcare.resources.model.UnresolvedResourceException;
import org.javarosa.core.reference.Reference;
import org.javarosa.core.util.externalizable.DeserializationException;
import org.javarosa.core.util.externalizable.ExtUtil;
import org.javarosa.core.util.externalizable.PrototypeFactory;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Pair;

/**
 * @author ctsims
 *
 */
public class MediaFileAndroidInstaller extends FileSystemInstaller {

    @Nullable
    String path;
    
    public MediaFileAndroidInstaller() {
        
    }
    
    public MediaFileAndroidInstaller(String destination, String upgradeDestination, @Nullable String path) {
        super(destination + (path == null ? "" : "/" + path), upgradeDestination + (path == null ? "" : "/" + path));
        //establish whether dir structure needs to be extended?
        this.path = path;
    }
    
    /* (non-Javadoc)
     * @see org.commcare.resources.model.ResourceInstaller#uninstall(org.commcare.resources.model.Resource, org.commcare.resources.model.ResourceTable, org.commcare.resources.model.ResourceTable)
     */
    public boolean uninstall(Resource r) throws UnresolvedResourceException {
        boolean success = super.uninstall(r);
        if( success == false ) { return false; }
        //cleanup dirs
        return FileUtil.cleanFilePath(this.localDestination, path);
    }
    
    /* (non-Javadoc)
     * @see org.commcare.resources.model.ResourceInstaller#upgrade(org.commcare.resources.model.Resource, org.commcare.resources.model.ResourceTable)
     */
    public boolean upgrade(Resource r) {
        return super.upgrade(r);
    }
    
    protected int customInstall(Resource r, Reference local, boolean upgrade) throws IOException, UnresolvedResourceException {
        return upgrade ? Resource.RESOURCE_STATUS_UPGRADE : Resource.RESOURCE_STATUS_INSTALLED;
    }

    /* (non-Javadoc)
     * @see org.commcare.resources.model.ResourceInstaller#requiresRuntimeInitialization()
     */
    public boolean requiresRuntimeInitialization() {
        return false;
    }
    
    /*
     * (non-Javadoc)
     * @see org.commcare.android.resource.installers.FileSystemInstaller#initialize(org.commcare.android.util.AndroidCommCarePlatform)
     */
    @Override
    public boolean initialize(AndroidCommCarePlatform instance) throws ResourceInitializationException {
        return false;
    }

    /* (non-Javadoc)
     * @see org.javarosa.core.util.externalizable.Externalizable#readExternal(java.io.DataInputStream, org.javarosa.core.util.externalizable.PrototypeFactory)
     */
    public void readExternal(DataInputStream in, PrototypeFactory pf) throws IOException, DeserializationException {
        super.readExternal(in, pf);
        path = ExtUtil.nullIfEmpty(ExtUtil.readString(in));
    }

    /* (non-Javadoc)
     * @see org.javarosa.core.util.externalizable.Externalizable#writeExternal(java.io.DataOutputStream)
     */
    public void writeExternal(DataOutputStream out) throws IOException {
        super.writeExternal(out);
        ExtUtil.writeString(out, ExtUtil.emptyIfNull(path));
    }
    
    /*
     * (non-Javadoc)
     * @see org.commcare.android.resource.installers.FileSystemInstaller#getResourceName(org.commcare.resources.model.Resource, org.commcare.resources.model.ResourceLocation)
     */
    @NonNull
    @Override
    public Pair<String, String> getResourceName(Resource r, @NonNull ResourceLocation loc) {
        int index = loc.getLocation().lastIndexOf("/");
        if(index == -1 ) { return new Pair<String,String>(loc.getLocation(), ".dat"); }
        String fileName = loc.getLocation().substring(index);
        
        String extension = ".dat";
        int lastDot = fileName.lastIndexOf(".");
        if(lastDot != -1) {
            extension =fileName.substring(lastDot);
            fileName = fileName.substring(0, lastDot);
        }
        return new Pair<String, String>(fileName, extension);
    }
}
