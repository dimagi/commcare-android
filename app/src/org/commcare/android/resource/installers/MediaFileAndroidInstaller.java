package org.commcare.android.resource.installers;

import android.util.Pair;

import org.commcare.resources.model.Resource;
import org.commcare.resources.model.ResourceInitializationException;
import org.commcare.resources.model.ResourceLocation;
import org.commcare.resources.model.UnresolvedResourceException;
import org.commcare.utils.AndroidCommCarePlatform;
import org.commcare.utils.FileUtil;
import org.javarosa.core.reference.Reference;
import org.javarosa.core.util.externalizable.DeserializationException;
import org.javarosa.core.util.externalizable.ExtUtil;
import org.javarosa.core.util.externalizable.PrototypeFactory;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * @author ctsims
 */
public class MediaFileAndroidInstaller extends FileSystemInstaller {

    private String path;

    @SuppressWarnings("unused")
    public MediaFileAndroidInstaller() {
        // For externalization
    }

    public MediaFileAndroidInstaller(String destination, String upgradeDestination, String path) {
        super(destination + (path == null ? "" : "/" + path), upgradeDestination + (path == null ? "" : "/" + path));
        //establish whether dir structure needs to be extended?
        this.path = path;
    }

    @Override
    public boolean uninstall(Resource r) throws UnresolvedResourceException {
        boolean success = super.uninstall(r);
        if (!success) {
            return false;
        }
        //cleanup dirs
        return FileUtil.cleanFilePath(this.localDestination, path);
    }

    @Override
    protected int customInstall(Resource r, Reference local, boolean upgrade) throws IOException, UnresolvedResourceException {
        return upgrade ? Resource.RESOURCE_STATUS_UPGRADE : Resource.RESOURCE_STATUS_INSTALLED;
    }

    @Override
    public boolean requiresRuntimeInitialization() {
        return false;
    }

    @Override
    public boolean initialize(AndroidCommCarePlatform instance) throws ResourceInitializationException {
        return false;
    }

    @Override
    public void readExternal(DataInputStream in, PrototypeFactory pf) throws IOException, DeserializationException {
        super.readExternal(in, pf);
        path = ExtUtil.nullIfEmpty(ExtUtil.readString(in));
    }

    @Override
    public void writeExternal(DataOutputStream out) throws IOException {
        super.writeExternal(out);
        ExtUtil.writeString(out, ExtUtil.emptyIfNull(path));
    }

    @Override
    public Pair<String, String> getResourceName(Resource r, ResourceLocation loc) {
        int index = loc.getLocation().lastIndexOf("/");
        if (index == -1) {
            return new Pair<>(loc.getLocation(), ".dat");
        }
        String fileName = loc.getLocation().substring(index);

        String extension = ".dat";
        int lastDot = fileName.lastIndexOf(".");
        if (lastDot != -1) {
            extension = fileName.substring(lastDot);
            fileName = fileName.substring(0, lastDot);
        }
        return new Pair<>(fileName, extension);
    }
}
