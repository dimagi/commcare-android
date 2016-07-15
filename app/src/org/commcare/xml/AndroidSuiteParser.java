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

    private AndroidSuiteParser(InputStream suiteStream, ResourceTable table, String resourceGuid,
                               boolean skipResources, boolean isValidationPass, boolean isUpgrade,
                               IStorageUtilityIndexed<FormInstance> fixtureStorage) throws IOException {
        super(suiteStream, table, resourceGuid, fixtureStorage, skipResources, isValidationPass, isUpgrade);
    }

    public static AndroidSuiteParser buildInstallParser(InputStream suiteStream,
                                                        ResourceTable table,
                                                        String resourceGuid,
                                                        IStorageUtilityIndexed<FormInstance> fixtureStorage)
            throws IOException {
        return new AndroidSuiteParser(suiteStream, table, resourceGuid, false, false, false, fixtureStorage);
    }

    public static AndroidSuiteParser buildUpgradeParser(InputStream suiteStream,
                                                        ResourceTable table,
                                                        String resourceGuid,
                                                        IStorageUtilityIndexed<FormInstance> fixtureStorage)
            throws IOException {
        return new AndroidSuiteParser(suiteStream, table, resourceGuid, false, false, true, fixtureStorage);
    }

    public static AndroidSuiteParser buildInitParser(InputStream suiteStream,
                                                     ResourceTable table,
                                                     String resourceGuid,
                                                     IStorageUtilityIndexed<FormInstance> fixtureStorage)
            throws IOException {
        return new AndroidSuiteParser(suiteStream, table, resourceGuid, true, false, false, fixtureStorage);
    }

    public static AndroidSuiteParser buildVerifyParser(InputStream suiteStream,
                                                       ResourceTable table,
                                                       String resourceGuid,
                                                       IStorageUtilityIndexed<FormInstance> fixtureStorage)
            throws IOException {
        return new AndroidSuiteParser(suiteStream, table, resourceGuid, false, true, false, fixtureStorage);
    }

    @Override
    protected DetailParser getDetailParser() {
        return new AndroidDetailParser(parser);
    }
}
