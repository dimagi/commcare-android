package org.commcare.xml;

import org.commcare.resources.model.ResourceTable;
import org.javarosa.core.model.instance.FormInstance;
import org.javarosa.core.services.storage.IStorageUtilityIndexed;

import java.io.IOException;
import java.io.InputStream;

/**
 * Created by jschweers on 1/28/2016.
 */
public class AndroidSuiteParser extends SuiteParser {

    public AndroidSuiteParser(InputStream suiteStream, ResourceTable table, String resourceGuid, IStorageUtilityIndexed<FormInstance> fixtureStorage) throws IOException {
        super(suiteStream, table, resourceGuid, fixtureStorage);
    }

    @Override
    protected DetailParser getDetailParser() {
        return new AndroidDetailParser(parser);
    }
}
