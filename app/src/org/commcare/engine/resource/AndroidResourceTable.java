package org.commcare.engine.resource;

import org.commcare.models.database.SqlStorage;
import org.commcare.resources.model.InstallerFactory;
import org.commcare.resources.model.Resource;
import org.commcare.resources.model.ResourceTable;
import org.javarosa.core.services.storage.IStorageUtilityIndexed;

import java.util.Vector;

/**
 * @author Phillip Mates (pmates@dimagi.com)
 */
public class AndroidResourceTable extends ResourceTable {
    private final SqlStorage<Resource> sqlStorage;

    public AndroidResourceTable(SqlStorage<Resource> storage, InstallerFactory factory) {
        super((IStorageUtilityIndexed) storage, factory);
        this.sqlStorage = storage;
    }

    @Override
    public Vector<Resource> getResourcesForParent(String parent) {
        Vector<Resource> v = new Vector<>();
        v.addAll(sqlStorage.getRecordsForValue(Resource.META_INDEX_PARENT_GUID, parent));
        return v;
    }
}
