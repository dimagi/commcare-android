package org.commcare.android.database.global.models;

import org.commcare.android.storage.framework.Persisted;
import org.commcare.models.framework.Persisting;
import org.javarosa.core.util.externalizable.DeserializationException;
import org.javarosa.core.util.externalizable.ExtUtil;
import org.javarosa.core.util.externalizable.PrototypeFactory;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Represents an app that exists on CommCare HQ (either india or prod) and is accessible for
 * the currently-authenticated user (either mobile or web) to install.
 *
 * Created by amstone326 on 2/3/17.
 */
public class AppAvailableToInstall extends Persisted {

    public static final String STORAGE_KEY = "available_apps";

    @Persisting(1)
    private String domain;
    @Persisting(2)
    private String appName;
    @Persisting(3)
    private String profileRef;
    @Persisting(4)
    private String mediaProfileRef;

    public AppAvailableToInstall() {
        // for serialization only
    }

    public AppAvailableToInstall(String domain, String appName, String profileRef, String mediaProfileRef) {
        this.domain = domain;
        this.appName = appName;
        this.profileRef = profileRef;
        this.mediaProfileRef = mediaProfileRef;
    }

    public String getAppName() {
        return appName;
    }

    public String getMediaProfileRef() {
        return mediaProfileRef;
    }

    public String getDomainName() {
        return domain;
    }

    @Override
    public void readExternal(DataInputStream in, PrototypeFactory pf) throws IOException, DeserializationException {
        domain = ExtUtil.readString(in);
        appName = ExtUtil.readString(in);
        profileRef = ExtUtil.readString(in);
        mediaProfileRef = ExtUtil.readString(in);
    }

    @Override
    public void writeExternal(DataOutputStream out) throws IOException {
        ExtUtil.writeString(out, domain);
        ExtUtil.writeString(out, appName);
        ExtUtil.writeString(out, profileRef);
        ExtUtil.writeString(out, mediaProfileRef);
    }
}
