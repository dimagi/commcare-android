package org.commcare.android.resource.installers;

import org.commcare.resources.model.Resource;
import org.commcare.resources.model.ResourceInitializationException;
import org.commcare.resources.model.UnresolvedResourceException;
import org.commcare.utils.AndroidCommCarePlatform;
import org.javarosa.core.reference.Reference;
import org.javarosa.core.services.locale.Localization;
import org.javarosa.core.util.externalizable.DeserializationException;
import org.javarosa.core.util.externalizable.ExtUtil;
import org.javarosa.core.util.externalizable.PrototypeFactory;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * @author ctsims
 */
public class LocaleAndroidInstaller extends FileSystemInstaller {

    private String locale;

    @SuppressWarnings("unused")
    public LocaleAndroidInstaller() {
        // For externalization
    }

    public LocaleAndroidInstaller(String destination, String upgradeDestination, String locale) {
        super(destination, upgradeDestination);
        this.locale = locale;
    }


    @Override
    public boolean initialize(AndroidCommCarePlatform instance, boolean isUpgrade) throws ResourceInitializationException {
        Localization.registerLanguageReference(locale, localLocation);
        return true;
    }

    @Override
    protected int customInstall(Resource r, Reference local, boolean upgrade) throws IOException, UnresolvedResourceException {
        return upgrade ? Resource.RESOURCE_STATUS_UPGRADE : Resource.RESOURCE_STATUS_INSTALLED;
    }

    @Override
    public boolean requiresRuntimeInitialization() {
        return true;
    }

    @Override
    public void readExternal(DataInputStream in, PrototypeFactory pf) throws IOException, DeserializationException {
        super.readExternal(in, pf);
        this.locale = ExtUtil.nullIfEmpty(ExtUtil.readString(in));
    }

    @Override
    public void writeExternal(DataOutputStream out) throws IOException {
        super.writeExternal(out);
        ExtUtil.writeString(out, ExtUtil.emptyIfNull(locale));
    }
}
