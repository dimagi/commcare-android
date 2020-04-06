/**
 *
 */
package org.commcare.android.database.app.models;

import org.commcare.resources.model.Resource;
import org.commcare.resources.model.ResourceInstaller;
import org.commcare.resources.model.ResourceLocation;
import org.javarosa.core.services.storage.IMetaData;
import org.javarosa.core.services.storage.Persistable;
import org.javarosa.core.util.externalizable.DeserializationException;
import org.javarosa.core.util.externalizable.ExtUtil;
import org.javarosa.core.util.externalizable.ExtWrapList;
import org.javarosa.core.util.externalizable.ExtWrapTagged;
import org.javarosa.core.util.externalizable.PrototypeFactory;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Vector;

/**
 * A model extension which reads Resource models from the
 * Version 13 db, and writes them back into the Version 14
 * db
 */
public class ResourceV13 implements Persistable, IMetaData {

    private static final String META_INDEX_RESOURCE_ID = "ID";
    private static final String META_INDEX_RESOURCE_GUID = "RGUID";
    private static final String META_INDEX_PARENT_GUID = "PGUID";
    private static final String META_INDEX_VERSION = "VERSION";

    private int recordId = -1;
    private int version;
    private int status;
    private String id;
    private Vector<ResourceLocation> locations;
    private ResourceInstaller initializer;
    private String guid;

    // Not sure if we want this persisted just yet...
    private String parent;

    private String descriptor;

    /**
     * Blank constructor for deserialization only!
     */
    public ResourceV13() {

    }

    @Override
    public void readExternal(DataInputStream in, PrototypeFactory pf) throws IOException, DeserializationException {
        this.recordId = ExtUtil.readInt(in);
        this.version = ExtUtil.readInt(in);
        this.id = ExtUtil.readString(in);
        this.guid = ExtUtil.readString(in);
        this.status = ExtUtil.readInt(in);
        this.parent = ExtUtil.nullIfEmpty(ExtUtil.readString(in));

        locations = (Vector<ResourceLocation>)ExtUtil.read(in, new ExtWrapList(ResourceLocation.class), pf);
        this.initializer = (ResourceInstaller)ExtUtil.read(in, new ExtWrapTagged(), pf);
        this.descriptor = ExtUtil.nullIfEmpty(ExtUtil.readString(in));
    }

    @Override
    public void writeExternal(DataOutputStream out) throws IOException {
        ExtUtil.writeNumeric(out, recordId);
        ExtUtil.writeNumeric(out, version);
        ExtUtil.writeString(out, id);
        ExtUtil.writeString(out, guid);
        ExtUtil.writeNumeric(out, status);
        ExtUtil.writeString(out, ExtUtil.emptyIfNull(parent));

        ExtUtil.write(out, new ExtWrapList(locations));
        ExtUtil.write(out, new ExtWrapTagged(initializer));
        ExtUtil.writeString(out, ExtUtil.emptyIfNull((String)null));
        ExtUtil.writeString(out, ExtUtil.emptyIfNull(descriptor));
    }

    @Override
    public Object getMetaData(String fieldName) {
        switch (fieldName) {
            case META_INDEX_RESOURCE_ID:
                return id;
            case META_INDEX_RESOURCE_GUID:
                return guid;
            case META_INDEX_PARENT_GUID:
                return parent == null ? "" : parent;
            case META_INDEX_VERSION:
                return version;
        }
        throw new IllegalArgumentException("No Field w/name " + fieldName + " is relevant for resources");
    }

    @Override
    public String[] getMetaDataFields() {
        return new String[]{META_INDEX_RESOURCE_ID, META_INDEX_RESOURCE_GUID, META_INDEX_PARENT_GUID, META_INDEX_VERSION};
    }

    public void setInstaller(ResourceInstaller initializer) {
        this.initializer = initializer;
    }

    public ResourceInstaller getInstaller() {
        return initializer;
    }

    @Override
    public int getID() {
        return recordId;
    }

    @Override
    public void setID(int ID) {
        recordId = ID;
    }

    public int getVersion() {
        return version;
    }

    public String getResourceId() {
        return id;
    }

    public String getRecordGuid() {
        return guid;
    }

    public Vector<ResourceLocation> getLocations() {
        return locations;
    }

    public String getDescriptor() {
        return descriptor;
    }

    public int getStatus() {
        return status;
    }
}
