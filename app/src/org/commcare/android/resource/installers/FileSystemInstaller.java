package org.commcare.android.resource.installers;

import android.util.Pair;

import org.commcare.android.logging.AndroidLogger;
import org.commcare.dalvik.application.CommCareApplication;
import org.commcare.resources.model.MissingMediaException;
import org.commcare.resources.model.Resource;
import org.commcare.resources.model.ResourceInitializationException;
import org.commcare.resources.model.ResourceInstaller;
import org.commcare.resources.model.ResourceLocation;
import org.commcare.resources.model.ResourceTable;
import org.commcare.resources.model.UnreliableSourceException;
import org.commcare.resources.model.UnresolvedResourceException;
import org.commcare.utils.AndroidCommCarePlatform;
import org.commcare.utils.AndroidStreamUtil;
import org.commcare.utils.FileUtil;
import org.javarosa.core.reference.InvalidReferenceException;
import org.javarosa.core.reference.Reference;
import org.javarosa.core.reference.ReferenceManager;
import org.javarosa.core.services.Logger;
import org.javarosa.core.util.externalizable.DeserializationException;
import org.javarosa.core.util.externalizable.ExtUtil;
import org.javarosa.core.util.externalizable.PrototypeFactory;
import org.javarosa.xml.util.UnfullfilledRequirementsException;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Vector;

import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLPeerUnverifiedException;

/**
 * @author ctsims
 */
abstract class FileSystemInstaller implements ResourceInstaller<AndroidCommCarePlatform> {

    //TODO:HAAACKY.
    private static final String STAGING_EXT = "cc_app-staging";


    String localLocation;
    String localDestination;
    private String upgradeDestination;

    FileSystemInstaller() {

    }

    FileSystemInstaller(String localDestination, String upgradeDestination) {
        this.localDestination = localDestination;
        this.upgradeDestination = upgradeDestination;
    }

    @Override
    public void cleanup() {
        // TODO Auto-generated method stub

    }

    @Override
    public abstract boolean initialize(AndroidCommCarePlatform instance) throws ResourceInitializationException;

    @Override
    public boolean install(Resource r, ResourceLocation location, Reference ref, ResourceTable table, AndroidCommCarePlatform instance, boolean upgrade) throws UnresolvedResourceException, UnfullfilledRequirementsException {
        try {
            OutputStream os;
            Reference localReference;

            //Moved this up before the local stuff, in case the local reference fails, we don't want to start dealing with it
            InputStream input;
            try {
                input = ref.getStream();
            } catch (FileNotFoundException e) {
                //This simply means that the reference wasn't actually valid like it thought it was (sometimes you can't tell until you try)
                //so let it keep iterating through options.
                return false;
            }

            File tempFile;

            //Stream to location
            try {
                Pair<String, String> fileDetails = getResourceName(r, location);
                //Final destination
                localReference = getEmptyLocalReference((upgrade ? upgradeDestination : localDestination), fileDetails.first, fileDetails.second);

                //Create a temporary place to store these bits
                tempFile = new File(CommCareApplication._().getTempFilePath());

                //Make sure the stream is valid
                os = new FileOutputStream(tempFile);

                //Get the actual local file we'll be putting the data into
                localLocation = localReference.getURI();
            } catch (InvalidReferenceException ire) {
                throw new LocalStorageUnavailableException("Couldn't create reference to declared location " + localLocation + " for file system installation", localLocation);
            } catch (IOException ioe) {
                throw new LocalStorageUnavailableException("Couldn't write to local reference " + localLocation + " for file system installation", localLocation);
            }

            //Write the full file to the temporary location
            AndroidStreamUtil.writeFromInputToOutput(input, os);

            //Get a cannonical path
            String localUri = localReference.getLocalURI();
            File destination = new File(localUri);

            //Make sure there's a seat for the new file
            FileUtil.ensureFilePathExists(destination);

            //File written, it must be valid now, so move it into our intended location
            if (!tempFile.renameTo(destination)) {
                throw new LocalStorageUnavailableException("Couldn't write to local reference " + localLocation + " to location " + localUri + " for file system installation", localLocation);
            }

            //TODO: Sketch - if this fails, we'll still have the file at that location.
            int status = customInstall(r, localReference, upgrade);

            table.commit(r, status);

            if (localLocation == null) {
                throw new UnresolvedResourceException(r, "After install there is no local resource location");
            }
            return true;
        } catch (SSLHandshakeException | SSLPeerUnverifiedException e) {
            // SSLHandshakeException is thrown by the HttpRequestGenerator on
            // 4.3 devices when the peer certificate is bad.
            //
            // SSLPeerUnverifiedException is thrown by the HttpRequestGenerator
            // on 2.3 devices when the peer certificate is bad.
            //
            // Deliver these errors upstream to the SetupActivity as an
            // UnresolvedResourceException
            e.printStackTrace();

            UnresolvedResourceException mURE =
                    new UnresolvedResourceException(r, "Your certificate was bad. This is often due to a mis-set phone clock.", true);
            mURE.initCause(e);

            throw mURE;
        } catch (IOException e) {
            e.printStackTrace();
            throw new UnreliableSourceException(r, e.getMessage());
        }
    }

    private Reference getEmptyLocalReference(String root, String fileName, String extension) throws InvalidReferenceException, IOException {
        Reference r = ReferenceManager._().DeriveReference(root + "/" + fileName + extension);
        int count = 0;
        while (r.doesBinaryExist()) {
            count++;
            r = ReferenceManager._().DeriveReference(root + "/" + fileName + String.valueOf(count) + extension);
        }
        return r;
    }

    /**
     * Perform any custom installation actions required for this resource.
     */
    protected abstract int customInstall(Resource r, Reference local, boolean upgrade) throws IOException, UnresolvedResourceException;

    @Override
    public abstract boolean requiresRuntimeInitialization();

    @Override
    public boolean uninstall(Resource r) throws UnresolvedResourceException {
        try {
            return new File(ReferenceManager._().DeriveReference(this.localLocation).getLocalURI()).delete();
        } catch (InvalidReferenceException e) {
            throw new UnresolvedResourceException(r, "Local reference couldn't be found for resource at " + this.localLocation);
        }
    }

    @Override
    public boolean upgrade(Resource r) {
        try {
            //TODO: This process is silly! Just put the files somewhere as a resource with a unique GUID and stop shuffling them around!
            //TODO: Also, there's way too much duplicated code here

            //use same filename as before
            String filepart = localLocation.substring(localLocation.lastIndexOf("/"));

            //Get final destination
            String finalLocation = localDestination + "/" + filepart;

            if (!moveFrom(localLocation, finalLocation, false)) {
                return false;
            }

            localLocation = finalLocation;
            return true;
        } catch (InvalidReferenceException e) {
            //e.printStackTrace();
            //throw new UnresolvedResourceException(r, "Invalid reference while upgrading local resource. Reference path is: " + e.getReferenceString());
            return false;
        }
    }


    private boolean moveFrom(String oldLocation, String newLocation, boolean force) throws InvalidReferenceException {
        File newFile = new File(ReferenceManager._().DeriveReference(newLocation).getLocalURI());
        File oldFile = new File(ReferenceManager._().DeriveReference(oldLocation).getLocalURI());

        if (!oldFile.exists()) {
            //Nothing should be allowed to exist in the new location except for the incoming file
            //due to the staging rules. If there's a file there, it's this one.
            return newFile.exists();
        }

        if (oldFile.exists() && newFile.exists()) {
            //There's a destination file where this file is 
            //trying to move to. Something might have failed to unstage
            if (force) {
                //If we're recovering or something, wipe out the destination.
                //we've gotta recover!
                if (!newFile.delete()) {
                    return false;
                } else {
                    //new file is gone. Let's get ours in there!
                }
            } else {
                //can't copy over an existing file. An unstage might have failed.
                return false;
            }
        }

        return oldFile.renameTo(newFile);
    }

    @Override
    public boolean unstage(Resource r, int newStatus) {
        try {
            //Our destination/source are different depending on where we're going
            if (newStatus == Resource.RESOURCE_STATUS_UNSTAGED) {
                String newLocation = localLocation + STAGING_EXT;
                if (!moveFrom(localLocation, newLocation, true)) {
                    return false;
                }
                localLocation = newLocation;
                return true;
            } else if (newStatus == Resource.RESOURCE_STATUS_UPGRADE) {
                //use same filename as before
                String filepart = localLocation.substring(localLocation.lastIndexOf("/"));

                //Get update destination
                String finalLocation = upgradeDestination + "/" + filepart;

                //move back to upgrade folder
                if (!moveFrom(localLocation, finalLocation, true)) {
                    return false;
                }
                localLocation = finalLocation;
                return true;
            } else {
                Logger.log(AndroidLogger.TYPE_RESOURCES, "Couldn't figure out how to unstage to status " + newStatus);
                return false;
            }
        } catch (InvalidReferenceException e) {
            Logger.log(AndroidLogger.TYPE_RESOURCES, "Very Bad! Couldn't derive a reference to " + e.getReferenceString());
            //e.printStackTrace();
            //throw new UnresolvedResourceException(r, "Invalid reference while upgrading local resource. Reference path is: " + e.getReferenceString());
            return false;
        }

    }

    @Override
    public boolean revert(Resource r, ResourceTable table) {
        String finalLocation = null;
        try {
            //use same filename as before
            String filepart = localLocation.substring(localLocation.lastIndexOf("/"));

            //remove staging extension 
            int stagingindex = filepart.lastIndexOf(STAGING_EXT);
            if (stagingindex != -1) {
                filepart = filepart.substring(0, stagingindex);
            }

            //Get final destination
            finalLocation = localDestination + "/" + filepart;

            if (!moveFrom(localLocation, finalLocation, true)) {
                return false;
            }

            localLocation = finalLocation;
            return true;
        } catch (InvalidReferenceException e) {
            Logger.log(AndroidLogger.TYPE_RESOURCES, "Very Bad! Couldn't restore a resource to destination" + finalLocation + " somehow");
            //e.printStackTrace();
            //throw new UnresolvedResourceException(r, "Invalid reference while upgrading local resource. Reference path is: " + e.getReferenceString());
            return false;
        }
    }

    public int rollback(Resource r) {

        //TODO: These filepath ops need to be the same for this all to work,
        //which is not super robust against changes right now.

        int status = r.getStatus();
        File currentPointer = new File(this.localLocation);

        String filepart = localLocation.substring(localLocation.lastIndexOf("/"));
        int stagingindex = filepart.lastIndexOf(STAGING_EXT);
        if (stagingindex != -1) {
            filepart = filepart.substring(0, stagingindex);
        }

        //Expected location for the file if the operation had succeeded.
        String oldRef;
        String expectedRef;
        int[] rollbackPushForward;

        switch (status) {
            case Resource.RESOURCE_STATUS_INSTALL_TO_UNSTAGE:
                oldRef = localDestination + "/" + filepart;
                expectedRef = localDestination + "/" + filepart + STAGING_EXT;
                rollbackPushForward = new int[]{Resource.RESOURCE_STATUS_INSTALLED, Resource.RESOURCE_STATUS_UNSTAGED};
                break;
            case Resource.RESOURCE_STATUS_UNSTAGE_TO_INSTALL:
                oldRef = localDestination + "/" + filepart + STAGING_EXT;
                expectedRef = localDestination + "/" + filepart;
                rollbackPushForward = new int[]{Resource.RESOURCE_STATUS_UNSTAGED, Resource.RESOURCE_STATUS_INSTALLED};
                break;
            case Resource.RESOURCE_STATUS_UPGRADE_TO_INSTALL:
                oldRef = upgradeDestination + "/" + filepart;
                expectedRef = localDestination + "/" + filepart;
                rollbackPushForward = new int[]{Resource.RESOURCE_STATUS_UNSTAGED, Resource.RESOURCE_STATUS_INSTALLED};
                break;
            case Resource.RESOURCE_STATUS_INSTALL_TO_UPGRADE:
                oldRef = localDestination + "/" + filepart;
                expectedRef = upgradeDestination + "/" + filepart;
                rollbackPushForward = new int[]{Resource.RESOURCE_STATUS_UNSTAGED, Resource.RESOURCE_STATUS_INSTALLED};
                break;
            default:
                throw new RuntimeException("Unexpected status for rollback! " + status);
        }

        try {
            File preMove = new File(ReferenceManager._().DeriveReference(oldRef).getLocalURI());

            File expectedFile = new File(ReferenceManager._().DeriveReference(expectedRef).getLocalURI());

            //the expectation is that localReference might be pointing to the old ref which no longer exists, 
            //in which case the moved already happened.
            if (currentPointer.exists()) {

                //This either means that the move worked (we couldn't have updated the pointer otherwise)
                //or that it didn't move.
                if (currentPointer.getCanonicalFile().equals(preMove.getCanonicalFile())) {
                    return rollbackPushForward[0];
                } else if (currentPointer.getCanonicalFile().equals(expectedFile.getCanonicalFile())) {
                    return rollbackPushForward[1];
                } else {
                    //Uh... we should only have been able to move the file to one of those places.
                    return -1;
                }
            } else {
                //The file should have already moved to its new location
                if (expectedFile.exists()) {
                    localLocation = expectedRef;
                    return rollbackPushForward[1];
                } else {
                    return -1;
                }
            }
        } catch (InvalidReferenceException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void readExternal(DataInputStream in, PrototypeFactory pf) throws IOException, DeserializationException {
        this.localLocation = ExtUtil.nullIfEmpty(ExtUtil.readString(in));
        this.localDestination = ExtUtil.readString(in);
        this.upgradeDestination = ExtUtil.readString(in);
    }

    @Override
    public void writeExternal(DataOutputStream out) throws IOException {
        ExtUtil.writeString(out, ExtUtil.emptyIfNull(localLocation));
        ExtUtil.writeString(out, localDestination);
        ExtUtil.writeString(out, upgradeDestination);
    }

    //TODO: Put files into an arbitrary name and keep the reference. This confuses things too much
    Pair<String, String> getResourceName(Resource r, ResourceLocation loc) {
        String input = loc.getLocation();
        String extension = "";
        int lastDot = input.lastIndexOf(".");
        if (lastDot != -1) {
            extension = input.substring(lastDot);
        }
        return new Pair<>(r.getResourceId(), extension(extension));
    }

    //Hate this
    private static final String validExtChars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

    private String extension(String input) {
        int invalid = -1;
        //we wanna go from the last "." to the next non-alphanumeric character.
        for (int i = 1; i < input.length(); ++i) {
            if (validExtChars.indexOf(input.charAt(i)) == -1) {
                invalid = i;
                break;
            }
        }
        if (invalid == -1) {
            return input;
        }
        return input.substring(0, invalid);
    }

    public boolean verifyInstallation(Resource r, Vector<MissingMediaException> issues) {
        try {
            Reference ref = ReferenceManager._().DeriveReference(localLocation);
            if (!ref.doesBinaryExist()) {
                issues.add(new MissingMediaException(r, "File doesn't exist at: " + ref.getLocalURI()));
                return true;
            }
        } catch (IOException e) {
            issues.add(new MissingMediaException(r, "Problem accessing file at: " + localLocation));
            return true;
        } catch (InvalidReferenceException e) {
            issues.add(new MissingMediaException(r, "invalid reference: " + localLocation));
            return true;
        }
        return false;
    }
}
