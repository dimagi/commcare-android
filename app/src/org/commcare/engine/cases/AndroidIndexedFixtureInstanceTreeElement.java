package org.commcare.engine.cases;

import org.commcare.cases.instance.IndexedFixtureInstanceTreeElement;
import org.commcare.cases.model.StorageIndexedTreeElementModel;
import org.commcare.core.interfaces.UserSandbox;
import org.commcare.utils.SerializationUtil;
import org.javarosa.core.model.IndexedFixtureIdentifier;
import org.javarosa.core.model.instance.AbstractTreeElement;
import org.javarosa.core.model.instance.InstanceBase;
import org.javarosa.core.model.instance.TreeElement;
import org.javarosa.core.services.storage.IStorageUtilityIndexed;
import org.javarosa.core.util.externalizable.DeserializationException;

/**
 * The root element for an indexed fixture data instance
 * with support for attributes lookup
 */

public class AndroidIndexedFixtureInstanceTreeElement extends IndexedFixtureInstanceTreeElement {

    private TreeElement attributes;

    public AndroidIndexedFixtureInstanceTreeElement(AbstractTreeElement instanceRoot,
                                                    IStorageUtilityIndexed<StorageIndexedTreeElementModel> storage,
                                                    IndexedFixtureIdentifier indexedFixtureIdentifier) {
        super(instanceRoot, storage, indexedFixtureIdentifier);
    }

    public static IndexedFixtureInstanceTreeElement get(UserSandbox sandbox,
                                                        String instanceName,
                                                        InstanceBase instanceBase) {
        IndexedFixtureIdentifier indexedFixtureIdentifier = sandbox.getIndexedFixtureIdentifier(instanceName);
        if (indexedFixtureIdentifier == null) {
            return null;
        } else {
            IStorageUtilityIndexed<StorageIndexedTreeElementModel> storage =
                    sandbox.getIndexedFixtureStorage(instanceName);
            return new AndroidIndexedFixtureInstanceTreeElement(instanceBase, storage, indexedFixtureIdentifier);
        }
    }

    @Override
    public int getAttributeCount() {
        return loadAttributes().getAttributeCount();
    }

    @Override
    public String getAttributeNamespace(int index) {
        return loadAttributes().getAttributeNamespace(index);
    }

    @Override
    public String getAttributeName(int index) {
        return loadAttributes().getAttributeName(index);
    }

    @Override
    public String getAttributeValue(int index) {
        return loadAttributes().getAttributeValue(index);
    }

    @Override
    public AbstractTreeElement getAttribute(String namespace, String name) {
        TreeElement attr = loadAttributes().getAttribute(namespace, name);
        attr.setParent(this);
        return attr;
    }

    @Override
    public String getAttributeValue(String namespace, String name) {
        return loadAttributes().getAttributeValue(namespace, name);
    }

    private synchronized TreeElement loadAttributes() {
        if (attributes == null) {
            try {
                attributes = SerializationUtil.deserialize(attrHolder, TreeElement.class);
            } catch (Exception e) {
                if (e.getCause() instanceof DeserializationException) {
                    String newMessage = "Deserialization failed for indexed fixture root atrribute wrapper: " + e.getMessage();
                    throw new RuntimeException(newMessage);
                }
            }
        }
        return attributes;
    }

}
