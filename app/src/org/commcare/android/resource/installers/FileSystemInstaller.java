package org.commcare.android.resource.installers;

import org.commcare.CommCareApplication;
import org.commcare.dalvik.R;
import org.commcare.engine.references.ParameterizedReference;
import org.commcare.engine.resource.installers.LocalStorageUnavailableException;
import org.commcare.network.CommcareRequestGenerator;
import org.commcare.network.RateLimitedException;
import org.commcare.network.RequestStats;
import org.commcare.resources.ResourceInstallContext;
import org.commcare.resources.model.InstallRequestSource;
import org.commcare.resources.model.MissingMediaException;
import org.commcare.resources.model.Resource;
import org.commcare.resources.model.ResourceInstaller;
import org.commcare.resources.model.ResourceLocation;
import org.commcare.resources.model.ResourceTable;
import org.commcare.resources.model.UnreliableSourceException;
import org.commcare.resources.model.UnresolvedResourceException;
import org.commcare.util.CommCarePlatform;
import org.commcare.util.LogTypes;
import org.commcare.utils.AndroidCommCarePlatform;
import org.commcare.utils.FileUtil;
import org.commcare.utils.StringUtils;
import org.javarosa.core.io.StreamsUtil;
import org.javarosa.core.reference.InvalidReferenceException;
import org.javarosa.core.reference.Reference;
import org.javarosa.core.reference.ReferenceManager;
import org.javarosa.core.services.Logger;
import org.javarosa.core.util.externalizable.DeserializationException;
import org.javarosa.core.util.externalizable.ExtUtil;
import org.javarosa.core.util.externalizable.PrototypeFactory;
import org.javarosa.xml.util.InvalidStructureException;
import org.javarosa.xml.util.UnfullfilledRequirementsException;
import org.xmlpull.v1.XmlPullParserException;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Vector;

import javax.net.ssl.SSLException;

import androidx.core.util.Pair;

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

    FileSystemInstaller(String localLocation, String localDestination, String upgradeDestination) {
        this.localLocation = localLocation;
        this.localDestination = localDestination;
        this.upgradeDestination = upgradeDestination;
    }

    FileSystemInstaller(String localDestination, String upgradeDestination) {
        this(null, localDestination, upgradeDestination);
    }

    @Override
    public boolean initialize(AndroidCommCarePlatform platform, boolean isUpgrade) throws
            IOException, InvalidReferenceException, InvalidStructureException,
            XmlPullParserException, UnfullfilledRequirementsException {
        Reference ref = ReferenceManager.instance().DeriveReference(localLocation);
        if (!ref.doesBinaryExist()) {
            throw new FileNotFoundException("No file exists at " + ref.getLocalURI());
        }
        return true;
    }

    @Override
    public boolean install(Resource r, ResourceLocation location,
                           Reference ref, ResourceTable table,
                           AndroidCommCarePlatform platform, boolean upgrade, ResourceInstallContext resourceInstallContext)
            throws UnresolvedResourceException, UnfullfilledRequirementsException {
        try {
            Reference localReference = resolveEmptyLocalReference(r, location, upgrade);

            InputStream inputFileStream;
            try {
                if (ref instanceof ParameterizedReference) {
                    inputFileStream = ((ParameterizedReference)ref).getStream(getInstallHeaders(platform, resourceInstallContext));
                } else {
                    inputFileStream = ref.getStream();
                }
            } catch (FileNotFoundException e) {
                // Means the reference wasn't valid so let it keep iterating through options.
                throw new UnresolvedResourceException(r,
                        StringUtils.getStringRobust(CommCareApplication.instance(), R.string.install_error_file_not_found, r.getDescriptor()), true);
            }

            renameFile(localReference.getLocalURI(), writeToTempFile(inputFileStream));

            //TODO: Sketch - if this fails, we'll still have the file at that location.
            int status = customInstall(r, localReference, upgrade, platform);

            table.commit(r, status);

            return true;
        } catch (SSLException e) { //Wrap in UnresolvedResourceExcption
            // Deliver these errors upstream to the SetupActivity as an
            // UnresolvedResourceException
            e.printStackTrace();

            UnresolvedResourceException mURE =
                    new UnresolvedResourceException(r, "Your certificate was bad. This is often due to a mis-set phone clock.", true);
            mURE.initCause(e);

            throw mURE;
        } catch (IOException e) {
            e.printStackTrace();
            UnreliableSourceException exception = new UnreliableSourceException(r, e.getMessage());
            exception.initCause(e);
            throw exception;
        } catch (RateLimitedException e) {
            UnresolvedResourceException mURE = new UnresolvedResourceException(r, "Our servers are unavailable at this time. Please try again later", true);
            mURE.initCause(e);
            throw mURE;
        }
    }

    private Map<String, String> getInstallHeaders(AndroidCommCarePlatform platform, ResourceInstallContext resourceInstallContext) {
        Map<String, String> headers = new HashMap<>();

        InstallRequestSource installRequestSource = resourceInstallContext.getInstallRequestSource();
        headers.put(CommcareRequestGenerator.X_COMMCAREHQ_REQUEST_SOURCE,
                String.valueOf(installRequestSource).toLowerCase());
        headers.put(CommcareRequestGenerator.X_COMMCAREHQ_REQUEST_AGE,
                String.valueOf(RequestStats.getRequestAge(platform.getApp(), installRequestSource)).toLowerCase());
        return headers;
    }

    private File writeToTempFile(InputStream inputFileStream) throws LocalStorageUnavailableException, IOException {
        File tempFile = new File(CommCareApplication.instance().getTempFilePath());
        try {
            OutputStream outputFileStream = new FileOutputStream(tempFile);
            StreamsUtil.writeFromInputToOutputNew(inputFileStream, outputFileStream);
            return tempFile;
        } catch (FileNotFoundException e) {
            throw new LocalStorageUnavailableException("Couldn't create temp file for file system installation", localLocation);
        } catch (StreamsUtil.OutputIOException e) {
            throw new LocalStorageUnavailableException("Couldn't write incoming file to temp location for file system installation", tempFile.getAbsolutePath());
        }
    }

    private void renameFile(String newFilename, File currentFile) throws LocalStorageUnavailableException {
        File destination = new File(newFilename);
        FileUtil.ensureFilePathExists(destination);
        if (!currentFile.renameTo(destination)) {
            throw new LocalStorageUnavailableException("Couldn't write to local reference " + localLocation + " to location " + newFilename + " for file system installation", localLocation);
        }
    }

    protected Reference resolveEmptyLocalReference(Resource r, ResourceLocation location, boolean upgrade)
            throws UnresolvedResourceException, LocalStorageUnavailableException {
        Reference localReference;
        try {
            Pair<String, String> fileNameAndExt = getResourceName(r, location);
            String referenceRoot = upgrade ? upgradeDestination : localDestination;
            localReference = getEmptyLocalReference(referenceRoot, fileNameAndExt.first, fileNameAndExt.second);
            //Get the actual local file we'll be putting the data into
            localLocation = localReference.getURI();
        } catch (InvalidReferenceException ire) {
            throw new LocalStorageUnavailableException("Couldn't create reference to declared location " + localLocation + " for file system installation", localLocation);
        } catch (IOException ioe) {
            throw new LocalStorageUnavailableException("Couldn't get a local reference " + localLocation + " for file system installation", localLocation);
        }

        if (localLocation == null) {
            throw new UnresolvedResourceException(r, "After install there is no local resource location");
        }
        return localReference;
    }

    private Reference getEmptyLocalReference(String root, String fileName, String extension)
            throws InvalidReferenceException, IOException {
        Reference r = ReferenceManager.instance().DeriveReference(root + "/" + fileName + extension);
        int count = 0;
        while (r.doesBinaryExist()) {
            count++;
            r = ReferenceManager.instance().DeriveReference(root + "/" + fileName + count + extension);
        }
        return r;
    }

    /**
     * Perform any custom installation actions required for this resource.
     */
    protected abstract int customInstall(Resource r, Reference local, boolean upgrade, AndroidCommCarePlatform platform) throws IOException, UnresolvedResourceException;

    @Override
    public abstract boolean requiresRuntimeInitialization();

    @Override
    public boolean uninstall(Resource r, AndroidCommCarePlatform platform) throws UnresolvedResourceException {
        try {
            return new File(ReferenceManager.instance().DeriveReference(this.localLocation).getLocalURI()).delete();
        } catch (InvalidReferenceException e) {
            throw new UnresolvedResourceException(r, "Local reference couldn't be found for resource at " + this.localLocation);
        }
    }

    @Override
    public boolean upgrade(Resource r, AndroidCommCarePlatform platform) {
        try {
            //TODO: This process is silly! Just put the files somewhere as a resource with a unique GUID and stop shuffling them around!

            //use same filename as before
            String filepart = localLocation.substring(localLocation.lastIndexOf("/"));

            //Get final destination
            String finalLocation = localDestination + "/" + filepart;

            if (!FileSystemUtils.moveFrom(localLocation, finalLocation, false)) {
                Logger.log(LogTypes.TYPE_RESOURCES, "Failed to move resource " + r.getDescriptor() +
                        " during upgrade from origin file path " + localLocation + " to  desination file path " +
                        finalLocation + ". Origin file exists = " + new File(localLocation).exists()
                        + " and Destination File exits = " + new File(finalLocation).exists());
                return false;
            }

            localLocation = finalLocation;
            return true;
        } catch (InvalidReferenceException e) {
            return false;
        }
    }

    @Override
    public boolean unstage(Resource r, int newStatus, AndroidCommCarePlatform platform) {
        try {
            //Our destination/source are different depending on where we're going
            if (newStatus == Resource.RESOURCE_STATUS_UNSTAGED) {
                String newLocation = localLocation + STAGING_EXT;
                if (!FileSystemUtils.moveFrom(localLocation, newLocation, true)) {
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
                if (!FileSystemUtils.moveFrom(localLocation, finalLocation, true)) {
                    return false;
                }
                localLocation = finalLocation;
                return true;
            } else {
                Logger.log(LogTypes.TYPE_RESOURCES, "Couldn't figure out how to unstage to status " + newStatus);
                return false;
            }
        } catch (InvalidReferenceException e) {
            Logger.log(LogTypes.TYPE_RESOURCES, "Very Bad! Couldn't derive a reference to " + e.getReferenceString());
            return false;
        }
    }

    @Override
    public boolean revert(Resource r, ResourceTable table, CommCarePlatform platform) {
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

            if (!FileSystemUtils.moveFrom(localLocation, finalLocation, true)) {
                return false;
            }

            localLocation = finalLocation;
            return true;
        } catch (InvalidReferenceException e) {
            Logger.log(LogTypes.TYPE_RESOURCES, "Very Bad! Couldn't restore a resource to destination " + finalLocation);
            return false;
        }
    }

    @Override
    public int rollback(Resource r, CommCarePlatform platform) {
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
            File preMove = new File(ReferenceManager.instance().DeriveReference(oldRef).getLocalURI());

            File expectedFile = new File(ReferenceManager.instance().DeriveReference(expectedRef).getLocalURI());

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
        } catch (InvalidReferenceException | IOException e) {
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

    @Override
    public boolean verifyInstallation(Resource r, Vector<MissingMediaException> issues,
                                      CommCarePlatform platform) {
        try {
            Reference ref = ReferenceManager.instance().DeriveReference(localLocation);
            FileUtil.checkReferenceURI(r, ref.getURI(), issues);
        } catch (InvalidReferenceException e) {
            issues.add(new MissingMediaException(r, "invalid reference: " + localLocation, localLocation,
                    MissingMediaException.MissingMediaExceptionType.INVALID_REFERENCE));
            return true;
        }
        return false;
    }

    //TODO: Put files into an arbitrary name and keep the reference. This confuses things too much
    protected Pair<String, String> getResourceName(Resource r, ResourceLocation loc) {
        String input = loc.getLocation();
        String extension = "";
        int lastDot = input.lastIndexOf(".");
        if (lastDot != -1) {
            extension = input.substring(lastDot);
        }
        return new Pair<>(r.getResourceId(), FileSystemUtils.extension(extension));
    }

    @Override
    public void cleanup() {
    }

    public String getLocalLocation() {
        return localLocation;
    }

    public String getLocalDestination() {
        return localDestination;
    }

    public String getUpgradeDestination() {
        return upgradeDestination;
    }

}
