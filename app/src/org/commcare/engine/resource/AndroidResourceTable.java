package org.commcare.engine.resource;

import org.commcare.models.database.SqlStorage;
import org.commcare.resources.model.InstallerFactory;
import org.commcare.resources.model.Resource;
import org.commcare.resources.model.ResourceTable;
import org.javarosa.core.services.storage.IStorageIterator;
import org.javarosa.core.services.storage.IStorageUtilityIndexed;

import java.util.HashSet;
import java.util.Vector;

/**
 * @author Phillip Mates (pmates@dimagi.com)
 */
public class AndroidResourceTable extends ResourceTable {
    private final SqlStorage<Resource> sqlStorage;
    private HashSet<String> resourcesInTable;

    public AndroidResourceTable(SqlStorage<Resource> storage, InstallerFactory factory) {
        super((IStorageUtilityIndexed) storage, factory);
        this.sqlStorage = storage;
    }

    @Override
    public Vector<Resource> getResourcesForParent(String parent) {
        return sqlStorage.getRecordsForValue(Resource.META_INDEX_PARENT_GUID, parent);
    }

    @Override
    protected boolean resourceDoesntExist(Resource resource) {
        initResourcesInTable();
        return !resourcesInTable.contains(resource.getResourceId());
    }

    private void initResourcesInTable() {
        if (resourcesInTable == null) {
            resourcesInTable = new HashSet<>();
            for (IStorageIterator it = sqlStorage.iterate(); it.hasMore(); ) {
                Resource r = (Resource)it.nextRecord();
                resourcesInTable.add(r.getResourceId());
            }
        }
    }

    @Override
    protected void cleanup() {
        super.cleanup();
        initResourcesInTable();
        resourcesInTable.clear();
    }

    @Override
    public void removeResource(Resource resource) {
        super.removeResource(resource);
        initResourcesInTable();
        resourcesInTable.remove(resource.getResourceId());
    }

    @Override
    public void commit(Resource r) {
        super.commit(r);
        initResourcesInTable();
        resourcesInTable.add(r.getResourceId());
    }
}
