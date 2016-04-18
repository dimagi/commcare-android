/**
 *
 */
package org.commcare.android.database.app.models;

import org.commcare.resources.model.Resource;
import org.commcare.resources.model.ResourceInstaller;
import org.commcare.resources.model.ResourceLocation;
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
 * Version 2 db, and writes them back into the Version 3
 * db.
 * <p/>
 * <p/>
 * NOTE: This updater is _NOT ROBUST AGAINST METADATA
 * CHANGES_. If the Resource model metadata changes, this
 * needs to be modified to reflect the V2/3 metadata.
 *
 * @author ctsims
 */
public class ResourceModelUpdater extends Resource {

    /**
     * Blank constructor for deserialization only!
     */
    public ResourceModelUpdater() {

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
    }

}
