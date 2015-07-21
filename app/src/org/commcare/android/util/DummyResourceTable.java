package org.commcare.android.util;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Vector;

import org.commcare.resources.model.InstallerFactory;
import org.commcare.resources.model.Resource;
import org.commcare.resources.model.ResourceInitializationException;
import org.commcare.resources.model.ResourceInstaller;
import org.commcare.resources.model.ResourceLocation;
import org.commcare.resources.model.ResourceTable;
import org.commcare.resources.model.UnresolvedResourceException;
import org.commcare.util.CommCareInstance;
import org.javarosa.xml.util.UnfullfilledRequirementsException;
import org.javarosa.core.reference.Reference;
import org.javarosa.core.services.storage.StorageFullException;
import org.javarosa.core.util.externalizable.DeserializationException;
import org.javarosa.core.util.externalizable.PrototypeFactory;

/**
 * @author ctsims
 *
 */
public class DummyResourceTable extends ResourceTable {

    /*
     * (non-Javadoc)
     * @see org.commcare.resources.model.ResourceTable#isEmpty()
     */
    @Override
    public boolean isEmpty() {
        return false;
    }

    /*
     * (non-Javadoc)
     * @see org.commcare.resources.model.ResourceTable#getInstallers()
     */
    @NonNull
    @Override
    public InstallerFactory getInstallers() {
        return new InstallerFactory() {
            @NonNull
            public ResourceInstaller getProfileInstaller(boolean forceInstall) {
                return getDummyInstaller();
            }
            
            @NonNull
            public ResourceInstaller getXFormInstaller() {
                return getDummyInstaller();
            }
            
            @NonNull
            public ResourceInstaller getSuiteInstaller() {
                return getDummyInstaller();
            }
            
            @NonNull
            public ResourceInstaller getLocaleFileInstaller(String locale) {
                return getDummyInstaller();
            }
            
            @NonNull
            public ResourceInstaller getLoginImageInstaller() {
                return getDummyInstaller();
            }
            
            @NonNull
            public ResourceInstaller getMediaInstaller(String path) {
                return getDummyInstaller();
            }
            
            @NonNull
            private ResourceInstaller getDummyInstaller() {
                return new ResourceInstaller() {

                    public void readExternal(DataInputStream in,
                            PrototypeFactory pf) throws IOException,
                            DeserializationException {
                        // TODO Auto-generated method stub
                        
                    }

                    public void writeExternal(DataOutputStream out)
                            throws IOException {
                        // TODO Auto-generated method stub
                        
                    }

                    public boolean requiresRuntimeInitialization() {
                        // TODO Auto-generated method stub
                        return false;
                    }

                    public boolean initialize(CommCareInstance instance)
                            throws ResourceInitializationException {
                        // TODO Auto-generated method stub
                        return true;
                    }

                    public boolean install(Resource r,
                            ResourceLocation location, Reference ref,
                            ResourceTable table, CommCareInstance instance,
                            boolean upgrade)
                            throws UnresolvedResourceException,
                            UnfullfilledRequirementsException {
                        // TODO Auto-generated method stub
                        return true;
                    }
                    
                    public int rollback(Resource r) {
                        throw new RuntimeException("Basic Installer resources can't rolled back");
                    }

                    /*
                     * (non-Javadoc)
                     * @see org.commcare.resources.model.ResourceInstaller#uninstall(org.commcare.resources.model.Resource)
                     */
                    @Override
                    public boolean uninstall(Resource r)
                            throws UnresolvedResourceException {
                        // TODO Auto-generated method stub
                        return true;
                    }

                    /*
                     * (non-Javadoc)
                     * @see org.commcare.resources.model.ResourceInstaller#unstage(org.commcare.resources.model.Resource, int)
                     */
                    @Override
                    public boolean unstage(Resource r, int newStatus) {
                        // TODO Auto-generated method stub
                        return true;
                    }

                    /*
                     * (non-Javadoc)
                     * @see org.commcare.resources.model.ResourceInstaller#revert(org.commcare.resources.model.Resource, org.commcare.resources.model.ResourceTable)
                     */
                    @Override
                    public boolean revert(Resource r, ResourceTable table) {
                        // TODO Auto-generated method stub
                        return true;
                    }

                    /*
                     * (non-Javadoc)
                     * @see org.commcare.resources.model.ResourceInstaller#upgrade(org.commcare.resources.model.Resource)
                     */
                    @Override
                    public boolean upgrade(Resource r)
                            throws UnresolvedResourceException {
                        // TODO Auto-generated method stub
                        return true;
                    }


                    public void cleanup() {
                        // TODO Auto-generated method stub
                        
                    }

                    public boolean verifyInstallation(Resource r, Vector problems) {
                        return false;
                    }
                    
                };
            }

        };
    }

    /*
     * (non-Javadoc)
     * @see org.commcare.resources.model.ResourceTable#removeResource(org.commcare.resources.model.Resource)
     */
    @Override
    public void removeResource(Resource resource) {
    }

    /*
     * (non-Javadoc)
     * @see org.commcare.resources.model.ResourceTable#addResource(org.commcare.resources.model.Resource, org.commcare.resources.model.ResourceInstaller, java.lang.String, int)
     */
    @Override
    public void addResource(Resource resource, ResourceInstaller initializer, String parentId, int status) throws StorageFullException {
    }

    /*
     * (non-Javadoc)
     * @see org.commcare.resources.model.ResourceTable#addResource(org.commcare.resources.model.Resource, org.commcare.resources.model.ResourceInstaller, java.lang.String)
     */
    @Override
    public void addResource(Resource resource, ResourceInstaller initializer,
            String parentId) throws StorageFullException {
    }

    /*
     * (non-Javadoc)
     * @see org.commcare.resources.model.ResourceTable#addResource(org.commcare.resources.model.Resource, int)
     */
    @Override
    public void addResource(Resource resource, int status)
            throws StorageFullException {
    }

    /*
     * (non-Javadoc)
     * @see org.commcare.resources.model.ResourceTable#getResourcesForParent(java.lang.String)
     */
    @NonNull
    @Override
    public Vector<Resource> getResourcesForParent(String parent) {
        return new Vector<Resource>();
    }

    /*
     * (non-Javadoc)
     * @see org.commcare.resources.model.ResourceTable#getResourceWithId(java.lang.String)
     */
    @Nullable
    @Override
    public Resource getResourceWithId(String id) {
        return null;
    }

    /*
     * (non-Javadoc)
     * @see org.commcare.resources.model.ResourceTable#getResourceWithGuid(java.lang.String)
     */
    @Nullable
    @Override
    public Resource getResourceWithGuid(String guid) {
        return null;
    }

    /*
     * (non-Javadoc)
     * @see org.commcare.resources.model.ResourceTable#isReady()
     */
    @Override
    public boolean isReady() {
        return true;
    }

    /*
     * (non-Javadoc)
     * @see org.commcare.resources.model.ResourceTable#commit(org.commcare.resources.model.Resource, int, int)
     */
    @Override
    public void commit(Resource r, int status, int version) throws UnresolvedResourceException {
    }

    /*
     * (non-Javadoc)
     * @see org.commcare.resources.model.ResourceTable#commit(org.commcare.resources.model.Resource, int)
     */
    @Override
    public void commit(Resource r, int status) {
    }

    /*
     * (non-Javadoc)
     * @see org.commcare.resources.model.ResourceTable#commit(org.commcare.resources.model.Resource)
     */
    @Override
    public void commit(Resource r) {
    }

    /*
     * (non-Javadoc)
     * @see org.commcare.resources.model.ResourceTable#prepareResources(org.commcare.resources.model.ResourceTable, org.commcare.util.CommCareInstance)
     */
    @Override
    public void prepareResources(ResourceTable master, CommCareInstance instance)
            throws UnresolvedResourceException,
            UnfullfilledRequirementsException {
    }

    /*
     * (non-Javadoc)
     * @see org.commcare.resources.model.ResourceTable#upgradeTable(org.commcare.resources.model.ResourceTable)
     */
    @Override
    public boolean upgradeTable(ResourceTable incoming)
            throws UnresolvedResourceException {
        return true;
    }

    /*
     * (non-Javadoc)
     * @see org.commcare.resources.model.ResourceTable#toString()
     */
    @NonNull
    @Override
    public String toString() {
        return "Dummy Table";
    }

    /*
     * (non-Javadoc)
     * @see org.commcare.resources.model.ResourceTable#destroy()
     */
    @Override
    public void destroy() {
    }

    /*
     * (non-Javadoc)
     * @see org.commcare.resources.model.ResourceTable#clear()
     */
    @Override
    public void clear() {
    }

    /*
     * (non-Javadoc)
     * @see org.commcare.resources.model.ResourceTable#initializeResources(org.commcare.util.CommCareInstance)
     */
    @Override
    public void initializeResources(CommCareInstance instance)
            throws ResourceInitializationException {
    }

}
