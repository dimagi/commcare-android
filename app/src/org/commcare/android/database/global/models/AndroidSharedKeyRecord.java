package org.commcare.android.database.global.models;

import android.content.Intent;
import android.os.Bundle;
import android.os.Parcel;

import org.commcare.android.storage.framework.PersistedPlain;
import org.commcare.models.encryption.CryptUtil;
import org.commcare.models.framework.Table;
import org.javarosa.core.services.Logger;
import org.javarosa.core.util.PropertyUtils;
import org.javarosa.core.util.externalizable.DeserializationException;
import org.javarosa.core.util.externalizable.ExtUtil;
import org.javarosa.core.util.externalizable.PrototypeFactory;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

/**
 * This is a record of a key that CommCare ODK has shared with another app
 * in order for that app to send data to CommCare securely.
 *
 * @author ctsims
 */
@Table("android_sharing_key")
public class AndroidSharedKeyRecord extends PersistedPlain {

    // TODO PLM: mark this as unique
    public final static String META_KEY_ID = "sharing_key_id";

    private String keyId;
    private byte[] privateKey;
    private byte[] publicKey;

    /*
     * Deserialization only
     */
    public AndroidSharedKeyRecord() {

    }

    private AndroidSharedKeyRecord(String keyId, byte[] privateKey, byte[] publicKey) {
        this.keyId = keyId;
        this.privateKey = privateKey;
        this.publicKey = publicKey;
    }

    public static AndroidSharedKeyRecord generateNewSharingKey() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(256, new SecureRandom());
            KeyPair pair = generator.genKeyPair();
            byte[] encodedPrivate = pair.getPrivate().getEncoded();
            String privateEncoding = pair.getPrivate().getFormat();
            byte[] encodedPublic = pair.getPublic().getEncoded();
            String publicencoding = pair.getPublic().getFormat();
            return new AndroidSharedKeyRecord(PropertyUtils.genUUID(), pair.getPrivate().getEncoded(), pair.getPublic().getEncoded());
        } catch (NoSuchAlgorithmException nsae) {
            return null;
        }
    }

    private String getKeyId() {
        return keyId;
    }

    public final static String EXTRA_KEY_ID = "commcare_sharing_key_id";
    private final static String EXTRA_KEY_PAYLOAD = "commcare_sharing_key_payload";

    public void writeResponseToIntent(Intent response) {
        response.putExtra(EXTRA_KEY_ID, this.getKeyId());
        response.putExtra(EXTRA_KEY_PAYLOAD, this.publicKey);
    }

    private final static String EXTRA_KEY_CALLOUT = "commcare_sharing_key_callout";
    private final static String EXTRA_KEY_CALLOUT_SYMETRIC_KEY = "commcare_sharing_key_symetric";

    public Bundle getIncomingCallout(Intent incoming) {
        byte[] incomingCallout = incoming.getByteArrayExtra(EXTRA_KEY_CALLOUT);
        byte[] incomingKey = incoming.getByteArrayExtra(EXTRA_KEY_CALLOUT_SYMETRIC_KEY);
        Parcel p = Parcel.obtain();
        byte[] decoded;
        try {
            byte[] aesKey = CryptUtil.decrypt(incomingKey, CryptUtil.getPrivateKeyCipher(this.privateKey));
            decoded = CryptUtil.decrypt(incomingCallout, CryptUtil.getAesKeyCipher(aesKey));
        } catch (GeneralSecurityException gse) {
            Logger.exception(gse);
            return null;
        }

        p.unmarshall(decoded, 0, decoded.length);
        p.setDataPosition(0);

        Bundle result = p.readBundle();
        p.recycle();
        return result;
    }

    @Override
    public void readExternal(DataInputStream in, PrototypeFactory pf) throws IOException, DeserializationException {
        super.readExternal(in, pf);

        keyId = ExtUtil.readString(in);
        privateKey = ExtUtil.readBytes(in);
        publicKey = ExtUtil.readBytes(in);
    }

    @Override
    public void writeExternal(DataOutputStream out) throws IOException {
        super.writeExternal(out);

        ExtUtil.writeString(out, keyId);
        ExtUtil.writeBytes(out, privateKey);
        ExtUtil.writeBytes(out, publicKey);
    }

    @Override
    public String[] getMetaDataFields() {
        return new String[]{META_KEY_ID};
    }

    @Override
    public Object getMetaData(String fieldName) {
        switch (fieldName) {
            case META_KEY_ID:
                return keyId;
            default:
                throw new IllegalArgumentException("No metadata field " + fieldName + " in the storage system");
        }
    }
}
