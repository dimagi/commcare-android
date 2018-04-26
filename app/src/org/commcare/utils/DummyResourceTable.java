package org.commcare.utils;

import org.commcare.resources.model.InstallerFactory;
import org.commcare.resources.model.Resource;
import org.commcare.resources.model.ResourceInstaller;
import org.commcare.resources.model.ResourceLocation;
import org.commcare.resources.model.ResourceTable;
import org.commcare.resources.model.UnresolvedResourceException;
import org.commcare.util.CommCarePlatform;
import org.javarosa.core.reference.Reference;
import org.javarosa.core.util.externalizable.DeserializationException;
import org.javarosa.core.util.externalizable.PrototypeFactory;
import org.javarosa.xml.util.UnfullfilledRequirementsException;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Vector;

/**
 * @author ctsims
 */
public class DummyResourceTable extends ResourceTable {

    @Override
    public boolean isEmpty() {
        return false;
    }

    @Override
    public InstallerFactory getInstallers() {
        return new InstallerFactory() {
            @Override
            public ResourceInstaller getProfileInstaller(boolean forceInstall) {
                return getDummyInstaller();
            }

            @Override
            public ResourceInstaller getXFormInstaller(boolean isUpdateInfoForm) {
                return getDummyInstaller();
            }

            @Override
            public ResourceInstaller getSuiteInstaller() {
                return getDummyInstaller();
            }

            @Override
            public ResourceInstaller getLocaleFileInstaller(String locale) {
                return getDummyInstaller();
            }

            @Override
            public ResourceInstaller getLoginImageInstaller() {
                return getDummyInstaller();
            }

            @Override
            public ResourceInstaller getMediaInstaller(String path) {
                return getDummyInstaller();
            }

            private ResourceInstaller getDummyInstaller() {
                return new ResourceInstaller() {

                    @Override
                    public void readExternal(DataInputStream in,
                                             PrototypeFactory pf) throws IOException,
                            DeserializationException {
                    }

                    @Override
                    public void writeExternal(DataOutputStream out)
                            throws IOException {
                    }

                    @Override
                    public boolean requiresRuntimeInitialization() {
                        return false;
                    }

                    @Override
                    public boolean initialize(CommCarePlatform platform, boolean isUpgrade) {
                        return true;
                    }

                    @Override
                    public boolean install(Resource r,
                                           ResourceLocation location, Reference ref,
                                           ResourceTable table, CommCarePlatform platform,
                                           boolean upgrade)
                            throws UnresolvedResourceException,
                            UnfullfilledRequirementsException {
                        return true;
                    }

                    @Override
                    public int rollback(Resource r, CommCarePlatform platform) {
                        throw new RuntimeException("Basic Installer resources can't rolled back");
                    }

                    @Override
                    public boolean uninstall(Resource r, CommCarePlatform platform)
                            throws UnresolvedResourceException {
                        return true;
                    }

                    @Override
                    public boolean unstage(Resource r, int newStatus, CommCarePlatform platform) {
                        return true;
                    }

                    @Override
                    public boolean revert(Resource r, ResourceTable table, CommCarePlatform platform) {
                        return true;
                    }

                    @Override
                    public boolean upgrade(Resource r, CommCarePlatform platform)
                            throws UnresolvedResourceException {
                        return true;
                    }


                    @Override
                    public void cleanup() {
                    }

                    @Override
                    public boolean verifyInstallation(Resource r, Vector problems, CommCarePlatform platform) {
                        return false;
                    }
                };
            }

        };
    }

    @Override
    public void addResource(Resource resource, ResourceInstaller initializer, String parentId, int status) {
    }

    @Override
    public void addResource(Resource resource, ResourceInstaller initializer,
                            String parentId) {
    }

    @Override
    public void addResource(Resource resource, int status) {
    }

    @Override
    public Vector<Resource> getResourcesForParent(String parent) {
        return new Vector<>();
    }

    @Override
    public Resource getResourceWithId(String id) {
        return null;
    }

    @Override
    public Resource getResourceWithGuid(String guid) {
        return null;
    }

    @Override
    public boolean isReady() {
        return true;
    }

    @Override
    public void commitCompoundResource(Resource r, int status, int version) throws UnresolvedResourceException {
    }

    @Override
    public void commit(Resource r, int status) {
    }

    @Override
    public void commit(Resource r) {
    }

    @Override
    public void prepareResources(ResourceTable master, CommCarePlatform platform)
            throws UnresolvedResourceException,
            UnfullfilledRequirementsException {
    }

    @Override
    public void upgradeTable(ResourceTable incoming, CommCarePlatform instance)
            throws UnresolvedResourceException {
    }

    @Override
    public String toString() {
        return "Dummy Table";
    }

    @Override
    public void destroy() {
    }

    @Override
    public void clear(CommCarePlatform instance) {
    }

    @Override
    public void initializeResources(CommCarePlatform platform, boolean isUpgrade) {
    }

}
